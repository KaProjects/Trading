import Foundation

/// One planned purchase (T8): a contract and the quantity being considered. Scratchpad only (D8.2).
public struct PlanRow: Identifiable, Sendable, Equatable {
    public let id: UUID
    public let contract: Contract
    public var quantity: Decimal?

    public init(id: UUID = UUID(), contract: Contract, quantity: Decimal? = nil) {
        self.id = id
        self.contract = contract
        self.quantity = quantity
    }
}

/// The Planner's numbers (T8), all totals in the account's base currency:
/// account value − holdings = cash − reserved by open buys = available − planned = left.
public struct PlannerSummary: Sendable {
    public struct Holding: Identifiable, Sendable {
        public let position: Position
        public let quote: Quote?
        public let marketValue: Decimal       // contract currency
        public let marketValueBase: Decimal   // base currency
        /// No live price (cost basis used) or unknown FX rate.
        public let isApproximate: Bool
        public var id: Int { position.id }
    }

    public struct Planned: Identifiable, Sendable {
        public let row: PlanRow
        public let quote: Quote?
        public let cost: Decimal?             // contract currency; nil until quantity and price are known
        public let costBase: Decimal?
        public var id: UUID { row.id }
    }

    public let baseCurrency: String
    public let accountValue: Decimal
    public let holdings: [Holding]
    public let holdingsTotal: Decimal
    public let cash: Decimal
    public let reservedByOpenBuys: Decimal
    public let available: Decimal
    public let planned: [Planned]
    public let plannedTotal: Decimal
    public let remaining: Decimal

    public var isApproximate: Bool { holdings.contains(where: \.isApproximate) }

    public static func make(account: AccountSummary, positions: [Position], orders: [Order],
                            quotes: [String: Quote], rows: [PlanRow]) -> PlannerSummary {
        let holdings = positions.map { position -> Holding in
            let quote = quotes[position.contract.feedSymbol]
            let native = quote.map { position.quantity.value * $0.last.value } ?? position.costBasis.value
            let rate = account.rate(position.contract.currency)
            return Holding(position: position, quote: quote, marketValue: native, marketValueBase: native * (rate ?? 1),
                           isApproximate: quote == nil || rate == nil)
        }
        let holdingsTotal = holdings.reduce(Decimal(0)) { $0 + $1.marketValueBase }
        let accountValue = account.netLiquidation.value
        let cash = accountValue - holdingsTotal
        let reserved = orders.filter { $0.side == .buy }.reduce(Decimal(0)) { sum, order in
            sum + order.remaining.value * order.limitPrice.value * (account.rate(order.contract.currency) ?? 1)
        }
        let available = cash - reserved
        let planned = rows.map { row -> Planned in
            let quote = quotes[row.contract.feedSymbol]
            guard let quantity = row.quantity, let quote else {
                return Planned(row: row, quote: quote, cost: nil, costBase: nil)
            }
            let cost = quantity * quote.last.value
            return Planned(row: row, quote: quote, cost: cost,
                           costBase: account.rate(row.contract.currency).map { cost * $0 })
        }
        let plannedTotal = planned.reduce(Decimal(0)) { $0 + ($1.costBase ?? 0) }
        return PlannerSummary(baseCurrency: account.baseCurrency, accountValue: accountValue, holdings: holdings,
                              holdingsTotal: holdingsTotal, cash: cash, reservedByOpenBuys: reserved,
                              available: available, planned: planned, plannedTotal: plannedTotal,
                              remaining: available - plannedTotal)
    }
}
