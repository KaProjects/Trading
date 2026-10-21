import Foundation
import Testing
@testable import TraderCore

/// Runs the real Python service (terminal/src/service/.venv, `uv sync` first) and talks to it over its socket.
@Suite(.serialized) struct ServiceIntegrationTests {
    static let serviceExecutable = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("service/.venv/bin/trader-service")

    final class Exit: @unchecked Sendable {
        private let lock = NSLock()
        private var code: Int32?
        var value: Int32? { lock.withLock { code } }
        func set(_ value: Int32) { lock.withLock { code = value } }
    }

    struct Running {
        let process: ServiceProcess
        let api: ServiceAPI
        let socketPath: String
        let exit: Exit
    }

    func startService() async throws -> Running {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("tt-\(UUID().uuidString.prefix(8))")
        let socketPath = folder.appendingPathComponent("s.sock").path
        let exit = Exit()
        let process = ServiceProcess(executable: Self.serviceExecutable,
                                     arguments: ["--env", "fake", "--socket", socketPath, "--watch-stdin",
                                                 "--log-level", "warning"],
                                     onExit: { exit.set($0) })
        try process.start()
        let api = ServiceAPI(socketPath: socketPath, timeout: 2)
        let deadline = Date().addingTimeInterval(15)
        while true {
            if (try? await api.status()) != nil { break }
            #expect(Date() < deadline, "service did not start")
            if Date() >= deadline { throw SocketError.closed }
            try await Task.sleep(for: .milliseconds(100))
        }
        return Running(process: process, api: api, socketPath: socketPath, exit: exit)
    }

    @Test func statusLoginAndErrors() async throws {
        let service = try await startService()
        defer { service.process.stop(grace: 1) }

        let initial = try await service.api.status()
        #expect(initial.state == .disconnected && initial.environment == .fake)
        #expect(try await service.api.http.send("GET", "/account").status == 409)
        #expect(try await service.api.login().state == .loggingIn)
        try await service.api.dev(.approve2FA)
        try await Task.sleep(for: .milliseconds(100))
        let connected = try await service.api.status()
        #expect(connected.state == .connected && connected.accountId == "FAKE0001")
    }

    @Test func eventStreamDeliversStatusChanges() async throws {
        let service = try await startService()
        defer { service.process.stop(grace: 1) }

        let socket = try await service.api.events()
        let reader = Task {
            var states: [ConnectionState] = []
            for try await message in socket.messages {
                let event = try ServiceEvent.parse(message)
                guard event.type == "status" else { continue }
                let status = try ServiceJSON.decoder.decode(BrokerStatus.self, from: event.data)
                states.append(status.state)
                if status.state == .connected { break }
            }
            return states
        }
        try await Task.sleep(for: .milliseconds(100))
        _ = try await service.api.login()
        try await service.api.dev(.approve2FA)
        let seen = try await withThrowingTaskGroup(of: [ConnectionState].self) { group in
            group.addTask { try await reader.value }
            group.addTask { try await Task.sleep(for: .seconds(5)); throw SocketError.closed }
            defer { group.cancelAll() }
            return try await group.next()!
        }
        #expect(seen == [.disconnected, .loggingIn, .connected])
        socket.close()
    }

    @Test func serviceExitsWhenStdinCloses() async throws {
        let service = try await startService()
        service.process.stop(grace: 10)  // closes stdin; terminate only as a fallback after 10 s
        let deadline = Date().addingTimeInterval(8)
        while service.exit.value == nil && Date() < deadline {
            try await Task.sleep(for: .milliseconds(100))
        }
        #expect(service.exit.value == 0)
        #expect(!FileManager.default.fileExists(atPath: service.socketPath))
    }
}
