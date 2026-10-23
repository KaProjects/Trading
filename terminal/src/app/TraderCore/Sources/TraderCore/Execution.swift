import Foundation

/// T10: an executed plan as saved at Place. Fills are matched by order_ref in the service's trade history.
public struct PlanRecord: Codable, Sendable {
    public struct Row: Codable, Sendable {
        public let conid: Int
        public let symbol: String
        public let currency: String
        public let orderRef: String
        public let plannedQuantity: Amount
        public let expectedReturnPct: Amount?

        public init(conid: Int, symbol: String, currency: String, orderRef: String, plannedQuantity: Amount,
                    expectedReturnPct: Amount?) {
            self.conid = conid
            self.symbol = symbol
            self.currency = currency
            self.orderRef = orderRef
            self.plannedQuantity = plannedQuantity
            self.expectedReturnPct = expectedReturnPct
        }
    }

    public let id: String
    public let committedAt: Date
    public let placedAt: Date
    public let rows: [Row]

    public init(id: String, committedAt: Date, placedAt: Date, rows: [Row]) {
        self.id = id
        self.committedAt = committedAt
        self.placedAt = placedAt
        self.rows = rows
    }
}

/// Part of a plan row: the shares still held (live price) or one sale (sell price).
public struct ExecutionLine: Codable, Sendable, Identifiable {
    public let kind: String            // held | sold
    public let quantity: Amount
    public let price: Amount?
    public let time: Date?
    public let cost: Amount
    public let value: Amount?
    public let profit: Amount?
    public let returnPct: Amount?
    public let expectedProfit: Amount?
    public let difference: Amount?

    public var isHeld: Bool { kind == "held" }
    public var id: String { "\(kind)-\(time?.timeIntervalSince1970 ?? 0)-\(price?.value ?? 0)" }
}

public struct ExecutionRow: Codable, Sendable, Identifiable {
    public let conid: Int
    public let symbol: String
    public let currency: String
    public let bought: Amount
    public let avgPrice: Amount?
    public let held: Amount
    public let price: Amount?
    public let cost: Amount
    public let value: Amount?
    public let realized: Amount
    public let profit: Amount?
    public let returnPct: Amount?
    public let expectedReturnPct: Amount?
    public let expectedProfit: Amount?
    public let difference: Amount?
    public let lines: [ExecutionLine]
    public var id: Int { conid }
}

public struct PlanExecution: Codable, Sendable, Identifiable {
    public let id: String?
    public let committedAt: Date?
    public let placedAt: Date?
    public let status: String          // waiting | open | partly_sold | closed
    public let targetReached: Bool
    public let finished: Bool          // rows that never bought anything are left out
    public let working: Bool           // an order of the plan is still open at the broker
    public let rows: [ExecutionRow]
    public let cost: Amount
    public let value: Amount?
    public let profit: Amount?
    public let returnPct: Amount?
    public let expectedProfit: Amount?
    public let expectedReturnPct: Amount?
    public let difference: Amount?

    public var isOutside: Bool { id == nil }

    /// Finish is offered while rows without anything bought are still shown and nothing is working any more.
    public var canFinish: Bool { !isOutside && !finished && !working && rows.contains { $0.bought.value == 0 } }

    public var statusLabel: String {
        switch status {
        case "waiting": "Waiting for fills"
        case "open": "Open"
        case "partly_sold": "Partly sold"
        case "closed": "Closed"
        default: status
        }
    }
}

/// Return minus expected return, in percentage points.
public func returnDifference(_ actual: Amount?, _ expected: Amount?) -> Amount? {
    guard let actual, let expected else { return nil }
    return Amount(actual.value - expected.value)
}

public struct ExecutionReport: Codable, Sendable {
    public let baseCurrency: String
    public let plans: [PlanExecution]
    public let outside: PlanExecution?
}
