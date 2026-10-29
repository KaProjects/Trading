import Foundation

/// A placed plan as saved at Place. T12: it only provides the expected returns of its tickers.
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

/// Part of a position: the shares still active (live price) or one sale (sell price).
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

/// One buy order of a position (its fills summed).
public struct Purchase: Codable, Sendable, Identifiable {
    public let time: Date?             // nil = held before the recorded history
    public let quantity: Amount
    public let price: Amount
    public let cost: Amount
    public var id: String { "\(time?.timeIntervalSince1970 ?? 0)-\(quantity.value)-\(price.value)" }
}

/// Shares of one buy order and, if sold, the sale they went into (FIFO).
public struct ExecutionTrade: Codable, Sendable, Identifiable {
    public let boughtAt: Date?         // nil = held before the recorded history
    public let quantity: Amount
    public let buyPrice: Amount
    public let cost: Amount
    public let soldAt: Date?           // nil = still active
    public let sellPrice: Amount?
    public let value: Amount?
    public let profit: Amount?
    public let returnPct: Amount?
    public var id: String {
        "\(boughtAt?.timeIntervalSince1970 ?? 0)-\(soldAt?.timeIntervalSince1970 ?? -1)-\(buyPrice.value)-\(sellPrice?.value ?? 0)"
    }
}

/// T12: one position of a ticker, from its first buy until sold down to 0 (then it is in History).
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
    public let expectedFrom: Date?     // placed time of the plan that set the expected return
    public let expectedProfit: Amount?
    public let difference: Amount?
    public let purchases: [Purchase]
    public let lines: [ExecutionLine]
    public let trades: [ExecutionTrade]   // sold first, then the active ones
    public let closedAt: Date?
    public var id: String { "\(conid)-\(closedAt?.timeIntervalSince1970 ?? 0)" }
}

public struct ExecutionTotal: Codable, Sendable {
    public let cost: Amount
    public let value: Amount?
    public let profit: Amount?
    public let returnPct: Amount?
    public let expectedProfit: Amount?
    public let expectedReturnPct: Amount?
    public let difference: Amount?
}

public struct ExecutionReport: Codable, Sendable {
    public let baseCurrency: String
    public let positions: [ExecutionRow]   // open, by symbol
    public let total: ExecutionTotal?
    public let closed: [ExecutionRow]      // newest first
}
