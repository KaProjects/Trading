import Foundation

/// T9: committing a plan into buy orders.
public enum PlanCommit {
    /// Commit is allowed when every planned row has a quantity and a live price and the plan fits the money.
    public static func canCommit(_ plan: PlannerSummary) -> Bool {
        !plan.planned.isEmpty
            && plan.planned.allSatisfy { ($0.row.quantity ?? 0) > 0 && $0.costBase != nil }
            && plan.remaining >= 0
    }

    /// Plan limits sit this far above the last price. Fixed: IBKR cancels buy limits more than ~3 % above the
    /// market (its price cap, not configurable), so 2 % keeps headroom without being refused.
    public static let markupPercent: Decimal = 2

    /// Quick Sell (Overview): limit = last × (1 − markup %), rounded **up** to 2 decimals, so it is never further
    /// below the market than the markup (IBKR caps sell limits too far below it, like buys above it).
    public static func sellLimitPrice(last: Decimal, markupPercent: Decimal = markupPercent) -> Decimal {
        var raw = last * (1 - markupPercent / 100)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &raw, 2, .up)
        return rounded
    }

    /// Limit price = last × (1 + markup %), rounded **down** to 2 decimals (D9.1) so it never exceeds the markup.
    public static func limitPrice(last: Decimal, markupPercent: Decimal = markupPercent) -> Decimal {
        var raw = last * (1 + markupPercent / 100)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &raw, 2, .down)
        return rounded
    }

    /// How far the buy limit is above the live price, in percent (negative = the price moved above the limit,
    /// so the buy may not fill). nil without both prices.
    public static func limitHeadroom(limit: Decimal?, last: Decimal?) -> Decimal? {
        guard let limit, let last, last > 0 else { return nil }
        return (limit - last) / last * 100
    }

    /// Σ quantity × limit in base currency. IBKR reserves at the limit, so this — not the plan's cost at last
    /// prices — must fit into "available to invest". nil when a line has no valid input or no FX rate.
    public static func totalAtLimit(_ lines: [(quantity: Decimal?, limit: Decimal?, currency: String)],
                                    account: AccountSummary) -> Decimal? {
        var total = Decimal(0)
        for line in lines {
            guard let quantity = line.quantity, quantity > 0, let limit = line.limit, limit > 0,
                  let rate = account.rate(line.currency) else { return nil }
            total += quantity * limit * rate
        }
        return total
    }
}

/// Outcome of placing one basket line.
public enum BasketResult: Equatable, Sendable {
    case placed(orderId: Int, status: String)
    case failed(String)

    public var isPlaced: Bool { if case .placed = self { true } else { false } }
}
