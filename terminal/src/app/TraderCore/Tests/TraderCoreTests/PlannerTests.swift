import Foundation
import Testing
@testable import TraderCore

@Suite struct PlannerTests {
    func contract(_ symbol: String, _ currency: String = "USD", conid: Int) -> Contract {
        Contract(conid: conid, symbol: symbol, name: symbol, secType: "STK", exchange: "SMART",
                 primaryExchange: currency == "USD" ? "NASDAQ" : "IBIS", currency: currency,
                 priceSymbol: currency == "USD" ? symbol : "\(symbol).DE")
    }

    func quote(_ symbol: String, _ last: Decimal) -> Quote {
        Quote(symbol: symbol, last: Amount(last), prevClose: Amount(last), changePct: Amount(0), time: Date())
    }

    var account: AccountSummary {
        AccountSummary(accountId: "X", baseCurrency: "USD", netLiquidation: Amount(50_000), totalCash: Amount(0),
                       availableFunds: Amount(0), buyingPower: Amount(0), cash: [],
                       fxToBase: ["USD": Amount(1), "EUR": Amount(Decimal(string: "1.1")!)])
    }

    @Test func linesAddUp() {
        let aapl = contract("AAPL", conid: 1), sap = contract("SAP", "EUR", conid: 2), f = contract("F", conid: 3)
        let positions = [
            Position(contract: aapl, quantity: Amount(50), avgCost: Amount(190), costBasis: Amount(9500)),
            Position(contract: sap, quantity: Amount(10), avgCost: Amount(200), costBasis: Amount(2000)),
        ]
        let openBuy = Order(orderId: 1, orderRef: "r", contract: aapl, side: .buy, quantity: Amount(10),
                            limitPrice: Amount(200), tif: "DAY", outsideRth: false, status: "Submitted",
                            filled: Amount(4), remaining: Amount(6), avgFillPrice: nil, rejectReason: nil,
                            createdAt: Date(), updatedAt: Date())
        let openSell = Order(orderId: 2, orderRef: "s", contract: aapl, side: .sell, quantity: Amount(5),
                             limitPrice: Amount(300), tif: "DAY", outsideRth: false, status: "Submitted",
                             filled: Amount(0), remaining: Amount(5), avgFillPrice: nil, rejectReason: nil,
                             createdAt: Date(), updatedAt: Date())
        let quotes = ["AAPL": quote("AAPL", 230), "SAP.DE": quote("SAP.DE", 250), "F": quote("F", 11)]
        let rows = [PlanRow(contract: f, quantity: 100), PlanRow(contract: aapl), PlanRow(contract: sap, quantity: 2)]

        let plan = PlannerSummary.make(account: account, positions: positions, orders: [openBuy, openSell],
                                       quotes: quotes, rows: rows)
        #expect(plan.holdings.map(\.marketValueBase) == [11_500, 2_750])  // 50×230, 10×250×1.1
        #expect(plan.holdingsTotal == 14_250)
        #expect(plan.cash == 35_750)
        #expect(plan.reservedByOpenBuys == 1_200)  // 6 remaining × 200; sells ignored
        #expect(plan.available == 34_550)
        #expect(plan.planned.map(\.costBase) == [1_100, nil, 550])  // no quantity → no cost
        #expect(plan.plannedTotal == 1_650)
        #expect(plan.remaining == 32_900)
        #expect(!plan.isApproximate)

        // weights: holdings of total holdings; plan of available to invest
        #expect(plan.holdings.map { plan.weight(of: $0) } == [11_500 / Decimal(14_250) * 100, 2_750 / Decimal(14_250) * 100])
        #expect(plan.weight(of: plan.planned[0]) == 1_100 / Decimal(34_550) * 100)
        #expect(plan.weight(of: plan.planned[1]) == nil)
        let total = plan.plannedWeight! + plan.remainingWeight!
        #expect(abs(total - 100) < Decimal(string: "0.000001")!)
    }

    @Test func missingPriceFallsBackToCostBasisAndIsMarked() {
        let aapl = contract("AAPL", conid: 1)
        let position = Position(contract: aapl, quantity: Amount(50), avgCost: Amount(190), costBasis: Amount(9500))
        let plan = PlannerSummary.make(account: account, positions: [position], orders: [], quotes: [:],
                                       rows: [PlanRow(contract: aapl, quantity: 5)])
        #expect(plan.holdings[0].marketValueBase == 9_500 && plan.isApproximate)
        #expect(plan.planned[0].cost == nil)
        #expect(plan.remaining == 40_500)
    }

    @Test func overspendingGoesNegative() {
        let f = contract("F", conid: 3)
        let plan = PlannerSummary.make(account: account, positions: [], orders: [], quotes: ["F": quote("F", 11)],
                                       rows: [PlanRow(contract: f, quantity: 5_000)])
        #expect(plan.remaining == -5_000)
    }
}

@Suite struct PlanVolumeTests {
    @Test func roundsUpToWholeShares() {
        // 30k USD of NVDA at 221.20 → 135.62… → 136 shares
        #expect(PlanVolume.quantity(thousands: 30, price: Decimal(string: "221.2")!, fxToBase: 1) == 136)
        #expect(PlanVolume.quantity(thousands: 1, price: 250, fxToBase: 1) == 4)  // exactly 4.0 stays 4
        #expect(PlanVolume.quantity(thousands: Decimal(string: "0.5")!, price: 1000, fxToBase: 1) == 1)
    }

    @Test func convertsForeignPricesToBase() {
        // 10k USD of SAP at 236.80 EUR × 1.08 = 255.744 USD per share → 39.1… → 40
        #expect(PlanVolume.quantity(thousands: 10, price: Decimal(string: "236.8")!,
                                    fxToBase: Decimal(string: "1.08")!) == 40)
    }

    @Test func noPriceNoQuantity() {
        #expect(PlanVolume.quantity(thousands: 30, price: 0, fxToBase: 1) == nil)
        #expect(PlanVolume.quantity(thousands: 0, price: 100, fxToBase: 1) == nil)
    }
}

@Suite struct PlanReturnTests {
    @Test func amountAndTotal() {
        #expect(PlanReturn.amount(cost: 30_000, percent: 12) == 3_600)
        #expect(PlanReturn.amount(cost: nil, percent: 12) == nil)
        #expect(PlanReturn.amount(cost: 30_000, percent: nil) == nil)
        let total = PlanReturn.total([(30_000, 12), (10_000, 20), (5_000, nil)])!
        #expect(total.amount == 5_600)
        #expect(total.percent == 14)  // 5 600 / 40 000 — rows without a percent don't count
        #expect(PlanReturn.total([(5_000, nil)]) == nil)
    }
}
