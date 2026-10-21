import Foundation

public enum ServiceError: Error, Equatable {
    case http(status: Int, code: String?, message: String?)
}

public enum DevAction: String, Sendable, CaseIterable {
    case approve2FA = "/dev/2fa/approve"
    case fail2FA = "/dev/2fa/fail"
    case dropConnection = "/dev/drop-connection"
    case requireLogin = "/dev/require-login"
    case reset = "/dev/reset"
}

/// Typed access to the T0 service contract.
public struct ServiceAPI: Sendable {
    public let http: UnixHTTPClient

    public init(socketPath: String, timeout: TimeInterval = 10) {
        http = UnixHTTPClient(socketPath: socketPath, timeout: timeout)
    }

    public func status() async throws -> BrokerStatus { try await call("GET", "/status") }
    public func login() async throws -> BrokerStatus { try await call("POST", "/login") }
    public func logout() async throws -> BrokerStatus { try await call("POST", "/logout") }

    public func dev(_ action: DevAction) async throws {
        try check(try await http.send("POST", action.rawValue))
    }

    /// Fake broker: on = 2FA approves itself after a delay; off = waits for `.approve2FA` / `.fail2FA`.
    public func autoApprove2FA() async throws -> Bool {
        let response = try await http.send("GET", "/dev/2fa/auto")
        try check(response)
        let object = try JSONSerialization.jsonObject(with: response.body) as? [String: Any]
        return object?["enabled"] as? Bool ?? true
    }

    public func setAutoApprove2FA(_ enabled: Bool) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["enabled": enabled])
        try check(try await http.send("POST", "/dev/2fa/auto", json: body))
    }

    public func setSlowResponses(seconds: Double) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["seconds": seconds])
        try check(try await http.send("POST", "/dev/slow", json: body))
    }

    public func events() async throws -> UnixWebSocket {
        try await UnixWebSocket.connect(socketPath: http.socketPath, path: "/events")
    }

    private func call<T: Decodable>(_ method: String, _ path: String) async throws -> T {
        let response = try await http.send(method, path)
        try check(response)
        return try ServiceJSON.decoder.decode(T.self, from: response.body)
    }

    private func check(_ response: HTTPResponse) throws {
        guard (200..<300).contains(response.status) else {
            let object = (try? JSONSerialization.jsonObject(with: response.body)) as? [String: Any]
            throw ServiceError.http(status: response.status, code: object?["error"] as? String,
                                    message: object?["message"] as? String)
        }
    }
}
