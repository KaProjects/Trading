import Foundation
import Testing
@testable import TraderCore

@Suite struct PlanCommitTests {
    let account = AccountSummary(accountId: "X", baseCurrency: "USD", netLiquidation: Amount(100_000),
                                 totalCash: Amount(0), availableFunds: Amount(0), buyingPower: Amount(0), cash: [],
                                 fxToBase: ["USD": Amount(1), "EUR": Amount(Decimal(string: "1.1")!)])

    func contract(_ symbol: String) -> Contract {
        Contract(conid: symbol.hashValue, symbol: symbol, name: symbol, secType: "STK", exchange: "SMART",
                 primaryExchange: "NASDAQ", currency: "USD", priceSymbol: symbol)
    }

    func plan(_ rows: [PlanRow], price: Decimal = 100) -> PlannerSummary {
        let quotes = Dictionary(uniqueKeysWithValues: rows.map {
            ($0.contract.feedSymbol, Quote(symbol: $0.contract.feedSymbol, last: Amount(price), prevClose: Amount(price),
                                           changePct: Amount(0), time: Date()))
        })
        return PlannerSummary.make(account: account, positions: [], orders: [], quotes: quotes, rows: rows)
    }

    @Test func limitPriceRoundsDown() {
        #expect(PlanCommit.limitPrice(last: Decimal(string: "913.67")!, markupPercent: 5) == Decimal(string: "959.35")!)  // 959.3535
        #expect(PlanCommit.limitPrice(last: Decimal(string: "11.21")!, markupPercent: 5) == Decimal(string: "11.77")!)    // 11.7705
        #expect(PlanCommit.limitPrice(last: 100, markupPercent: 0) == 100)
    }

    @Test func commitRules() {
        #expect(!PlanCommit.canCommit(plan([])))
        #expect(PlanCommit.canCommit(plan([PlanRow(contract: contract("A"), quantity: 10)])))
        #expect(!PlanCommit.canCommit(plan([PlanRow(contract: contract("A"))])))  // no quantity
        #expect(!PlanCommit.canCommit(plan([PlanRow(contract: contract("A"), quantity: 2_000)])))  // 200k > 100k
        let noPrice = PlannerSummary.make(account: account, positions: [], orders: [], quotes: [:],
                                          rows: [PlanRow(contract: contract("A"), quantity: 1)])
        #expect(!PlanCommit.canCommit(noPrice))
    }

    @Test func totalAtLimitInBaseCurrency() {
        let total = PlanCommit.totalAtLimit([(10, 105, "USD"), (5, 200, "EUR")], account: account)
        #expect(total == Decimal(2_150), "got \(String(describing: total))")
        #expect(PlanCommit.totalAtLimit([(nil, 105, "USD")], account: account) == nil)
        #expect(PlanCommit.totalAtLimit([(1, 1, "JPY")], account: account) == nil)  // unknown rate
    }
}


@Suite struct LimitHeadroomTests {
    @Test func headroomFollowsThePrice() {
        let limit = PlanCommit.limitPrice(last: 100, markupPercent: 3)  // 103.00
        #expect(PlanCommit.limitHeadroom(limit: limit, last: 100) == 3)
        #expect(PlanCommit.limitHeadroom(limit: limit, last: 103) == 0)
        let moved = PlanCommit.limitHeadroom(limit: limit, last: Decimal(string: "103.5")!)!
        #expect(moved < 0 && moved > Decimal(string: "-0.49")!)  // price moved above the limit
        #expect(PlanCommit.limitHeadroom(limit: nil, last: 100) == nil)
        #expect(PlanCommit.limitHeadroom(limit: 100, last: 0) == nil)
    }
}
