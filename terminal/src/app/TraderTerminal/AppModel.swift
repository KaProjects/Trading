import AppKit
import Observation
import os
import ServiceManagement
import TraderCore

/// App state: owns the service process, the event stream, the Touch ID lock and notifications.
@MainActor
@Observable
final class AppModel {
    let environment = AppEnvironment.defaultBroker
    private(set) var phase: ServicePhase = .starting
    private(set) var status: BrokerStatus?
    private(set) var locked = true
    private(set) var lastError: String?
    private(set) var startsAtLogin = SMAppService.mainApp.status == .enabled
    private(set) var slowResponses = false
    private(set) var autoApprove2FA = true

    var presentation: StatusPresentation { .make(locked: locked, phase: phase, status: status) }

    @ObservationIgnored private let api = ServiceAPI(socketPath: AppEnvironment.socketPath)
    @ObservationIgnored private let appLock = AppLock()
    @ObservationIgnored private let notifier = Notifier()
    @ObservationIgnored private var process: ServiceProcess?
    @ObservationIgnored private var streamTask: Task<Void, Never>?
    @ObservationIgnored private var heartbeatTask: Task<Void, Never>?
    @ObservationIgnored private var restarts: [Date] = []
    @ObservationIgnored private var quitting = false
    @ObservationIgnored private let logger = Logger(subsystem: "org.kaleta.trader-terminal", category: "app")

    init() {
        appLock.onLock = { [weak self] in self?.locked = true }
    }

    // MARK: - Service lifecycle

    func start() {
        notifier.requestAuthorization()
        launchService()
    }

    func shutdown() {
        quitting = true
        streamTask?.cancel()
        heartbeatTask?.cancel()
        process?.stop(grace: 2)
        process?.waitUntilExit()
    }

    private func launchService() {
        guard AppEnvironment.available.contains(environment) else {
            phase = .failed("\(environment.label) environment is not available yet")
            return
        }
        guard let executable = AppEnvironment.serviceExecutable,
              FileManager.default.isExecutableFile(atPath: executable.path) else {
            phase = .failed("Service not installed — run `uv sync` in terminal/src/service")
            return
        }
        phase = .starting
        status = nil
        let service = ServiceProcess(
            executable: executable,
            arguments: ["--env", environment.rawValue, "--socket", AppEnvironment.socketPath, "--watch-stdin",
                        "--log-level", "warning"],
            onExit: { [weak self] code in Task { @MainActor in self?.serviceExited(code: code) } })
        do {
            try service.start()
        } catch {
            phase = .failed("Could not start service: \(error.localizedDescription)")
            return
        }
        process = service
        Task { await waitUntilReady() }
    }

    private func waitUntilReady() async {
        let deadline = Date().addingTimeInterval(20)
        while Date() < deadline, !quitting {
            if let current = try? await api.status() {
                phase = .running
                apply(current)
                slowResponses = false
                if environment == .fake, let auto = try? await api.autoApprove2FA() { autoApprove2FA = auto }
                startStreams()
                return
            }
            try? await Task.sleep(for: .milliseconds(200))
        }
        if !quitting { phase = .failed("Service did not respond") }
    }

    private func serviceExited(code: Int32) {
        streamTask?.cancel()
        heartbeatTask?.cancel()
        process = nil
        status = nil
        guard !quitting else { return }
        logger.error("service exited with code \(code)")
        restarts = restarts.filter { $0 > Date().addingTimeInterval(-60) } + [Date()]
        guard restarts.count <= 3 else {
            phase = .failed("Service keeps stopping (exit code \(code))")
            return
        }
        phase = .failed("Service stopped (exit code \(code)) — restarting…")
        Task {
            try? await Task.sleep(for: .seconds(2))
            if !quitting { launchService() }
        }
    }

    /// Event stream (pushes) + heartbeat (detects a hung service; the stream alone can't).
    private func startStreams() {
        streamTask?.cancel()
        streamTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                do {
                    let socket = try await api.events()
                    defer { socket.close() }
                    for try await message in socket.messages {
                        handle(message)
                    }
                } catch {
                    logger.info("event stream ended: \(error.localizedDescription)")
                }
                try? await Task.sleep(for: .seconds(1))
            }
        }
        heartbeatTask?.cancel()
        heartbeatTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(10))
                guard let self, phase == .running else { continue }
                do { apply(try await api.status()) } catch { lastError = "Service not responding" }
            }
        }
    }

    private func handle(_ message: Data) {
        guard let event = try? ServiceEvent.parse(message), event.type == "status",
              let update = try? ServiceJSON.decoder.decode(BrokerStatus.self, from: event.data) else { return }
        apply(update)
    }

    private func apply(_ update: BrokerStatus) {
        let previous = status?.state
        status = update
        lastError = nil
        if let notification = StatusNotification.forTransition(from: previous, to: update.state), previous != nil {
            notifier.post(notification)
        }
    }

    // MARK: - User actions

    @discardableResult
    func unlock() async -> Bool {
        let ok = await appLock.unlock()
        if ok {
            locked = false
            if phase == .running, let current = try? await api.status() { apply(current) }
        }
        return ok
    }

    func logIn() async {
        if locked, !(await unlock()) { return }
        await perform { try await self.apply(self.api.login()) }
    }

    func stop() async {
        await perform { try await self.apply(self.api.logout()) }
    }

    func lockNow() {
        appLock.lock()
    }

    /// D2.9: closing the window locks the app like a screen lock; the broker session stays up.
    /// Reopening needs Touch ID only. A full logout is Stop or Quit.
    func windowClosed() {
        appLock.lock()
    }

    func setStartsAtLogin(_ enabled: Bool) {
        do {
            if enabled { try SMAppService.mainApp.register() } else { try SMAppService.mainApp.unregister() }
        } catch {
            lastError = "Start at login: \(error.localizedDescription)"
        }
        startsAtLogin = SMAppService.mainApp.status == .enabled
    }

    // MARK: - Developer menu (Debug builds, fake environment only)

    func dev(_ action: DevAction) async {
        await perform { try await self.api.dev(action) }
    }

    func toggleAutoApprove2FA() async {
        let enable = !autoApprove2FA
        await perform {
            try await self.api.setAutoApprove2FA(enable)
            self.autoApprove2FA = enable
        }
    }

    func toggleSlowResponses() async {
        let enable = !slowResponses
        await perform {
            try await self.api.setSlowResponses(seconds: enable ? 2 : 0)
            self.slowResponses = enable
        }
    }

    private func perform(_ work: @escaping @MainActor () async throws -> Void) async {
        do {
            try await work()
            lastError = nil
        } catch ServiceError.http(_, _, let message) {
            lastError = message ?? "Request failed"
        } catch {
            lastError = error.localizedDescription
        }
    }
}
