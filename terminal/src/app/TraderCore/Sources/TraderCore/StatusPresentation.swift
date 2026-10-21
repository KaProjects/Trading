import Foundation

public enum ServicePhase: Equatable, Sendable {
    case starting
    case running
    case failed(String)
}

public enum Indicator: Sendable {
    case grey, red, yellow, green
}

/// What the menu bar and window show for a given state (T2 states, D2.8 lock).
public struct StatusPresentation: Equatable, Sendable {
    public let title: String
    public let detail: String?
    public let indicator: Indicator
    public let isLocked: Bool
    public let canLogIn: Bool
    public let canStop: Bool

    public static func make(locked: Bool, phase: ServicePhase, status: BrokerStatus?) -> StatusPresentation {
        if locked {
            return .init(title: "Locked", detail: "Unlock with Touch ID", indicator: .grey, isLocked: true,
                         canLogIn: phase == .running, canStop: false)
        }
        switch phase {
        case .starting:
            return .init(title: "Starting…", detail: nil, indicator: .grey, isLocked: false,
                         canLogIn: false, canStop: false)
        case .failed(let reason):
            return .init(title: "Service error", detail: reason, indicator: .red, isLocked: false,
                         canLogIn: false, canStop: false)
        case .running:
            break
        }
        guard let status else {
            return .init(title: "Connecting to service…", detail: nil, indicator: .grey, isLocked: false,
                         canLogIn: false, canStop: false)
        }
        switch status.state {
        case .disconnected:
            return .init(title: "Disconnected", detail: nil, indicator: .red, isLocked: false,
                         canLogIn: true, canStop: false)
        case .loggingIn:
            return .init(title: "Logging in…", detail: status.message ?? "Approve on your phone", indicator: .yellow,
                         isLocked: false, canLogIn: false, canStop: true)
        case .connected:
            let account = status.accountId.map { " · \($0)" } ?? ""
            return .init(title: "Connected (\(status.environment.label)\(account))", detail: nil, indicator: .green,
                         isLocked: false, canLogIn: false, canStop: true)
        case .reconnecting:
            return .init(title: "Reconnecting…", detail: nil, indicator: .yellow, isLocked: false,
                         canLogIn: false, canStop: true)
        case .loginRequired:
            return .init(title: "Login required", detail: status.message, indicator: .red, isLocked: false,
                         canLogIn: true, canStop: false)
        case .loginFailed:
            return .init(title: "Login failed — Retry", detail: status.message, indicator: .red, isLocked: false,
                         canLogIn: true, canStop: false)
        }
    }
}

/// States that deserve a macOS notification (T2: user action needed).
public enum StatusNotification: Equatable, Sendable {
    case approveOnPhone, loginFailed, loginRequired

    public static func forTransition(from old: ConnectionState?, to new: ConnectionState) -> StatusNotification? {
        guard old != new else { return nil }
        switch new {
        case .loggingIn: return .approveOnPhone
        case .loginFailed: return .loginFailed
        case .loginRequired: return .loginRequired
        default: return nil
        }
    }

    public var title: String {
        switch self {
        case .approveOnPhone: "Approve the login on your phone"
        case .loginFailed: "Login failed"
        case .loginRequired: "Login required"
        }
    }

    public var body: String {
        switch self {
        case .approveOnPhone: "IBKR sent a 2FA request to IBKR Mobile."
        case .loginFailed: "The 2FA request was not approved in time. Click Log in to retry."
        case .loginRequired: "The broker session ended. Click Log in to connect again."
        }
    }
}
