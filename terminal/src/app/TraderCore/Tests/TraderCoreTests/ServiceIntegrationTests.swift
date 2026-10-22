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

extension ServiceIntegrationTests {
    @Test func accountDataAndSnapshotAfterUnlockOverStdin() async throws {
        let service = try await startService()
        defer { service.process.stop(grace: 1) }
        _ = try await service.api.login()
        try await service.api.dev(.approve2FA)
        try await Task.sleep(for: .milliseconds(100))

        let live = try await service.api.liveSnapshot()
        #expect(live.account.accountId == "FAKE0001")
        #expect(live.account.totalCash == Amount(Decimal(string: "133640.00")!))
        #expect(live.positions.count == 5 && live.orders.count == 1 && live.fills.count == 4)
        #expect(live.positions.first { $0.contract.symbol == "AAPL" }?.quantity == Amount(50))

        await #expect(throws: ServiceError.self) { _ = try await service.api.savedSnapshot() }  // 423 locked
        try service.process.send(.unlock(dataKey: Data(repeating: 7, count: 32)))
        try await Task.sleep(for: .milliseconds(300))
        let saved = try #require(try await service.api.savedSnapshot())
        #expect(saved.account == live.account)
        #expect(try await service.api.history(days: 30).count == 4)

        try service.process.send(.lock)
        try await Task.sleep(for: .milliseconds(200))
        await #expect(throws: ServiceError.self) { _ = try await service.api.savedSnapshot() }
    }
}

extension ServiceIntegrationTests {
    @Test func previewPlaceAndBlockedOrder() async throws {
        let service = try await startService()
        defer { service.process.stop(grace: 1) }
        _ = try await service.api.login()
        try await service.api.dev(.approve2FA)
        try await Task.sleep(for: .milliseconds(100))

        let contracts = try await service.api.contracts(symbol: "shop")
        #expect(Set(contracts.map(\.primaryExchange)) == ["NYSE", "TSE"])
        let aapl = try #require(try await service.api.contracts(symbol: "AAPL").first)

        var request = OrderRequest(conid: aapl.conid, side: .buy, quantity: Amount(200), limitPrice: Amount(250))
        let preview = try await service.api.preview(request)
        #expect(preview.value == Amount(50000))
        // info lines ("market closed") depend on the clock
        #expect(Set(preview.warnings.filter { $0.level != .info }.map(\.code)) == ["pct_of_account", "price_deviation"])
        #expect(preview.block == nil)

        request.quantity = Amount(1)
        request.orderRef = "swift-1"
        let order = try await service.api.placeOrder(request)
        let again = try await service.api.placeOrder(request)
        #expect(order.orderId == again.orderId)

        let sell = OrderRequest(conid: aapl.conid, side: .sell, quantity: Amount(1000), limitPrice: Amount(230),
                                orderRef: "swift-2")
        do {
            _ = try await service.api.placeOrder(sell)
            Issue.record("selling more than owned must be blocked")
        } catch ServiceError.http(let status, let code, _) {
            #expect(status == 422 && code == "sell_exceeds_position")
        }
        let rules = try await service.api.ruleThresholds()
        #expect(rules.quantityWarn == Amount(1000))
    }

    @Test func summaryLine() {
        let contract = Contract(conid: 1, symbol: "AAPL", name: "Apple Inc", secType: "STK", exchange: "SMART",
                                primaryExchange: "NASDAQ", currency: "USD")
        let request = OrderRequest(conid: 1, side: .buy, quantity: Amount(100),
                                   limitPrice: Amount(Decimal(string: "182.5")!))
        let line = OrderSummary.line(request, contract: contract, value: Amount(18250),
                                     locale: Locale(identifier: "en_US"))
        #expect(line == "BUY 100 AAPL (Apple Inc · NASDAQ · USD) LMT 182.50 DAY ≈ 18,250.00 USD")
    }
}

extension ServiceIntegrationTests {
    @Test func modifyCancelAndCancelAll() async throws {
        let service = try await startService()
        defer { service.process.stop(grace: 1) }
        _ = try await service.api.login()
        try await service.api.dev(.approve2FA)
        try await Task.sleep(for: .milliseconds(100))

        let aapl = 265598
        let placed = try await service.api.placeOrder(
            OrderRequest(conid: aapl, side: .buy, quantity: Amount(10), limitPrice: Amount(200), orderRef: "t6-1"))
        try await Task.sleep(for: .milliseconds(500))  // PendingSubmit -> Submitted

        let preview = try await service.api.preview(
            OrderRequest(conid: aapl, side: .buy, quantity: Amount(140), limitPrice: Amount(200)), modifying: placed.orderId)
        #expect(preview.block == nil)
        let modified = try await service.api.modifyOrder(placed.orderId, quantity: Amount(12), limitPrice: Amount(201))
        #expect(modified.quantity == Amount(12) && modified.limitPrice == Amount(201))

        let cancelled = try await service.api.cancelOrder(placed.orderId)
        #expect(cancelled.status == "Cancelled")
        #expect(try await service.api.cancelAllOrders().count == 1)  // the seeded MSFT order
        #expect(try await service.api.orders().isEmpty)
    }
}

@Suite struct OrderNotificationTests {
    func order(_ status: String, filled: Int, quantity: Int = 600, reason: String? = nil) -> Order {
        let contract = Contract(conid: 1, symbol: "NVDA", name: "NVIDIA", secType: "STK", exchange: "SMART",
                                primaryExchange: "NASDAQ", currency: "USD")
        return Order(orderId: 7, orderRef: "r", contract: contract, side: .buy, quantity: Amount(Decimal(quantity)),
                     limitPrice: Amount(40), tif: "DAY", outsideRth: false, status: status,
                     filled: Amount(Decimal(filled)), remaining: Amount(Decimal(quantity - filled)),
                     avgFillPrice: filled > 0 ? Amount(39) : nil, rejectReason: reason, createdAt: Date(), updatedAt: Date())
    }

    @Test func transitions() {
        #expect(OrderNotification.forUpdate(previous: nil, current: order("PendingSubmit", filled: 0)) == nil)
        #expect(OrderNotification.forUpdate(previous: order("PendingSubmit", filled: 0),
                                            current: order("Submitted", filled: 0)) == nil)
        #expect(OrderNotification.forUpdate(previous: order("Submitted", filled: 0),
                                            current: order("Submitted", filled: 200)) == .partiallyFilled)
        #expect(OrderNotification.forUpdate(previous: order("Submitted", filled: 200),
                                            current: order("Submitted", filled: 200)) == nil)
        #expect(OrderNotification.forUpdate(previous: order("Submitted", filled: 400),
                                            current: order("Filled", filled: 600)) == .filled)
        #expect(OrderNotification.forUpdate(previous: nil, current: order("Rejected", filled: 0, reason: "Insufficient funds"))
                == .rejected)
        #expect(OrderNotification.forUpdate(previous: order("Submitted", filled: 0),
                                            current: order("Cancelled", filled: 0)) == .cancelled)
    }

    @Test func texts() {
        let partial = order("Submitted", filled: 200)
        #expect(partial.statusText == "Partially filled 200 / 600")
        #expect(OrderNotification.partiallyFilled.body(for: partial).hasPrefix("200 of 600 shares at avg 39"))
        #expect(order("Rejected", filled: 0, reason: "Insufficient funds").statusText == "Rejected: Insufficient funds")
    }
}

extension ServiceIntegrationTests {
    @Test func savedPlanShowsInExecutionWithFills() async throws {
        let service = try await startService()
        defer { service.process.stop(grace: 1) }
        _ = try await service.api.login()
        try await service.api.dev(.approve2FA)
        try await Task.sleep(for: .milliseconds(100))
        try service.process.send(.unlock(dataKey: Data(repeating: 9, count: 32)))
        try await Task.sleep(for: .milliseconds(300))

        let ford = 9599491
        let plan = PlanRecord(id: "swift-plan", committedAt: Date(), placedAt: Date(), rows: [
            .init(conid: ford, symbol: "F", currency: "USD", orderRef: "swift-plan-f", plannedQuantity: Amount(10),
                  expectedReturnPct: Amount(10)),
        ])
        try await service.api.savePlan(plan)
        _ = try await service.api.placeOrder(OrderRequest(conid: ford, side: .buy, quantity: Amount(10),
                                                          limitPrice: Amount(12), orderRef: "swift-plan-f"))
        var report = try await service.api.execution()
        for _ in 0..<30 where report.plans.first?.status == "waiting" {
            try await Task.sleep(for: .milliseconds(100))
            report = try await service.api.execution()
        }
        let executed = try #require(report.plans.first)
        #expect(executed.id == "swift-plan" && executed.status == "open")
        #expect(executed.rows.first?.bought == Amount(10))
        #expect(report.outside?.rows.contains { $0.symbol == "AAPL" } == true)  // seeded holdings = initial lots
    }
}
