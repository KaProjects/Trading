import AppKit
import LocalAuthentication

/// D2.8: one Touch ID unlocks the app; Mac sleep, screen lock or user switch/log-out locks it again.
/// The authenticated LAContext is kept for the unlocked session so later Keychain reads (T3) don't prompt again.
@MainActor
final class AppLock {
    private(set) var context: LAContext?
    var onLock: (() -> Void)?
    private var observers: [NSObjectProtocol] = []

    init() {
        let workspace = NSWorkspace.shared.notificationCenter
        for name in [NSWorkspace.willSleepNotification, NSWorkspace.sessionDidResignActiveNotification] {
            observers.append(workspace.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.lock() }
            })
        }
        observers.append(DistributedNotificationCenter.default().addObserver(
            forName: Notification.Name("com.apple.screenIsLocked"), object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.lock() }
        })
    }

    var isUnlocked: Bool { context != nil }

    /// Touch ID (or the Mac password when Touch ID is unavailable, e.g. lid closed).
    func unlock() async -> Bool {
        if context != nil { return true }
        let candidate = LAContext()
        let reason = "unlock Trader Terminal"
        let success = await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            candidate.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { ok, _ in
                continuation.resume(returning: ok)
            }
        }
        if success { context = candidate }
        return success
    }

    func lock() {
        guard context != nil else { return }
        context?.invalidate()
        context = nil
        onLock?()
    }
}
