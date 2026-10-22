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

    public func vaultUnlocked() async throws -> Bool {
        let response = try await http.send("GET", "/vault")
        try check(response)
        let object = try JSONSerialization.jsonObject(with: response.body) as? [String: Any]
        return object?["unlocked"] as? Bool ?? false
    }

    public func account() async throws -> AccountSummary { try await call("GET", "/account") }
    public func positions() async throws -> [Position] { try await call("GET", "/positions") }
    public func orders() async throws -> [Order] { try await call("GET", "/orders") }
    public func fills(days: Int = 7) async throws -> [Fill] { try await call("GET", "/fills?days=\(days)") }
    public func history(days: Int) async throws -> [Fill] { try await call("GET", "/history?days=\(days)") }

    /// Live data in one go (used after connect and on account/position/order/fill events).
    public func liveSnapshot() async throws -> AccountSnapshot {
        async let account = account()
        async let positions = positions()
        async let orders = orders()
        async let fills = fills()
        return try await AccountSnapshot(asOf: Date(), account: account, positions: positions, orders: orders,
                                         fills: fills)
    }

    /// Last saved snapshot; nil when none was saved yet. Throws 423 while the service is locked.
    public func savedSnapshot() async throws -> AccountSnapshot? {
        let response = try await http.send("GET", "/snapshot")
        try check(response)
        if response.body == Data("null".utf8) { return nil }
        return try ServiceJSON.decoder.decode(AccountSnapshot.self, from: response.body)
    }

    public func contracts(symbol: String) async throws -> [Contract] {
        let query = symbol.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
        return try await call("GET", "/contracts?symbol=\(query)")
    }

    /// T10: record an executed plan (at Place). Needs the service unlocked.
    public func savePlan(_ plan: PlanRecord) async throws {
        try check(try await http.send("POST", "/plans", json: try ServiceJSON.encoder.encode(plan)))
    }

    /// T10: executed plans with FIFO lots and live numbers, plus the "Outside plans" group.
    public func execution() async throws -> ExecutionReport { try await call("GET", "/execution") }

    /// T11: transactions of these days (both included, the Mac's local dates) as CSV, one row per order.
    public func transactionsCSV(from start: Date, to end: Date) async throws -> Data {
        let day = Date.ISO8601FormatStyle(timeZone: .current).year().month().day()
        let response = try await http.send("GET", "/transactions.csv?from=\(start.formatted(day))&to=\(end.formatted(day))")
        try check(response)
        return response.body
    }

    /// T8: stream prices for these contracts too (planner rows). Not persisted.
    public func setWatchlist(_ conids: [Int]) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["conids": conids])
        try check(try await http.send("PUT", "/watchlist", json: body))
    }

    /// Latest price of every streamed symbol; afterwards follow `price` events.
    public func prices() async throws -> [Quote] { try await call("GET", "/prices") }

    /// Last price, nil when the feed has none yet.
    public func quote(conid: Int) async throws -> Quote? {
        let response = try await http.send("GET", "/quote?conid=\(conid)")
        try check(response)
        if response.body == Data("null".utf8) { return nil }
        return try ServiceJSON.decoder.decode(Quote.self, from: response.body)
    }

    /// `modifying`: check a change to an open order (its own reservations don't count against it).
    public func preview(_ request: OrderRequest, modifying orderId: Int? = nil) async throws -> OrderPreview {
        let query = orderId.map { "?modify_order_id=\($0)" } ?? ""
        return try await call("POST", "/orders/preview\(query)", body: request)
    }

    public func modifyOrder(_ orderId: Int, quantity: Amount, limitPrice: Amount) async throws -> Order {
        try await call("PATCH", "/orders/\(orderId)", body: OrderChange(quantity: quantity, limitPrice: limitPrice))
    }

    public func cancelOrder(_ orderId: Int) async throws -> Order { try await call("DELETE", "/orders/\(orderId)") }

    public func cancelAllOrders() async throws -> [Order] { try await call("DELETE", "/orders") }

    /// Throws `ServiceError.http(422, "sell_exceeds_position", …)` when blocked (D5.3).
    public func placeOrder(_ request: OrderRequest) async throws -> Order {
        precondition(request.orderRef != nil, "orderRef is required to send an order")
        return try await call("POST", "/orders", body: request)
    }

    public func ruleThresholds() async throws -> RuleThresholds { try await call("GET", "/settings/rules") }

    public func saveRuleThresholds(_ thresholds: RuleThresholds) async throws -> RuleThresholds {
        try await call("PUT", "/settings/rules", body: thresholds)
    }

    /// Activity log entries only the app knows about (e.g. order dialog cancelled).
    public func note(_ kind: String, detail: [String: String] = [:]) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["kind": kind, "detail": detail])
        try check(try await http.send("POST", "/activity", json: body))
    }

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
        try await call(method, path, json: nil)
    }

    private func call<T: Decodable, B: Encodable>(_ method: String, _ path: String, body: B) async throws -> T {
        try await call(method, path, json: try ServiceJSON.encoder.encode(body))
    }

    private func call<T: Decodable>(_ method: String, _ path: String, json: Data?) async throws -> T {
        let response = try await http.send(method, path, json: json)
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
