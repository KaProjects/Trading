import Foundation

/// One planned purchase (T8): a contract and the quantity being considered. Scratchpad only (D8.2).
public struct PlanRow: Identifiable, Sendable, Equatable {
    public let id: UUID
    public let contract: Contract
    public var quantity: Decimal?
    /// Set once the row's order is filled: the execution price, used instead of the live price (T9).
    public var fixedPrice: Decimal?

    public init(id: UUID = UUID(), contract: Contract, quantity: Decimal? = nil, fixedPrice: Decimal? = nil) {
        self.id = id
        self.contract = contract
        self.quantity = quantity
        self.fixedPrice = fixedPrice
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
            // A filled row is frozen on its execution price; the others follow the live price.
            let quote = row.fixedPrice.map {
                Quote(symbol: row.contract.feedSymbol, last: Amount($0), prevClose: Amount($0), changePct: Amount(0),
                      time: Date())
            } ?? quotes[row.contract.feedSymbol]
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

public enum PlanVolume {
    /// Whole shares to buy for a planned volume (D8.5): `thousands` × 1 000 in the account's base currency,
    /// divided by the live price converted to base currency, rounded **up**. nil without a usable price.
    public static func quantity(thousands: Decimal, price: Decimal, fxToBase: Decimal) -> Decimal? {
        let unitCost = price * fxToBase
        guard thousands > 0, unitCost > 0 else { return nil }
        var shares = thousands * 1000 / unitCost
        var rounded = Decimal()
        NSDecimalRound(&rounded, &shares, 0, .up)
        return rounded
    }
}

public extension PlannerSummary {
    /// Share of total holdings (weight breakdown), in percent. nil when nothing is held.
    func weight(of holding: Holding) -> Decimal? {
        holdingsTotal > 0 ? holding.marketValueBase / holdingsTotal * 100 : nil
    }

    /// Share of "available to invest", in percent. nil without a cost or without available money.
    func weight(of planned: Planned) -> Decimal? {
        guard let cost = planned.costBase, available > 0 else { return nil }
        return cost / available * 100
    }

    /// Planned total and what is left, as shares of "available to invest" (together 100 %).
    var plannedWeight: Decimal? { available > 0 ? plannedTotal / available * 100 : nil }
    var remainingWeight: Decimal? { available > 0 ? remaining / available * 100 : nil }
}

/// T8: expected return of planned purchases (user's estimate, in percent of cost).
public enum PlanReturn {
    /// Cost × percent / 100. nil without a cost or a percent.
    public static func amount(cost: Decimal?, percent: Decimal?) -> Decimal? {
        guard let cost, let percent else { return nil }
        return cost * percent / 100
    }

    /// Σ expected returns and their share of the cost of the rows that have one (nil when none do).
    public static func total(_ items: [(cost: Decimal?, percent: Decimal?)]) -> (amount: Decimal, percent: Decimal)? {
        let counted = items.compactMap { item -> (Decimal, Decimal)? in
            guard let cost = item.cost, let amount = amount(cost: cost, percent: item.percent) else { return nil }
            return (cost, amount)
        }
        let cost = counted.reduce(Decimal(0)) { $0 + $1.0 }
        guard !counted.isEmpty, cost > 0 else { return nil }
        let amount = counted.reduce(Decimal(0)) { $0 + $1.1 }
        return (amount, amount / cost * 100)
    }
}
