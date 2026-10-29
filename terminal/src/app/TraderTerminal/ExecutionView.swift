import SwiftUI
import TraderCore

/// T12: every position you hold, one small table per ticker (bought through the Planner or not), alphabetical,
/// with the total of all of them: a row per buy and, if sold, its sale (FIFO). Sold-out positions move to History.
struct ExecutionView: View {
    let model: AppModel
    let snapshot: AccountSnapshot
    let isLive: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if let report = model.execution {
                    if report.positions.isEmpty {
                        Text("No positions — buy in the Planner or from Overview.").foregroundStyle(.secondary)
                    } else {
                        if model.sellBasket.isOpen {
                            StretchOrScroll { SellReviewView(model: model) }
                        } else {
                            sellActions(report)
                        }
                        StretchOrScroll {
                            TradesTable(rows: report.positions, total: report.total, base: report.baseCurrency,
                                        selling: model.sellBasket.isOpen ? nil : model)
                        }
                    }
                } else {
                    ProgressView().frame(maxWidth: .infinity)
                }
            }
            .padding(20)
        }
        .opacity(isLive ? 1 : 0.55)
        .task { await model.keepRefreshingExecution() }
    }

    /// Sell all, or the ticked tickers.
    private func sellActions(_ report: ExecutionReport) -> some View {
        HStack {
            Spacer()
            if !model.sellSelection.isEmpty {
                Button("Sell \(model.sellSelection.count) selected") { model.openSell(model.sellSelection) }
                Button("Clear selection") { model.sellSelection = [] }
            }
            Button("Sell all") { model.openSell(nil) }
                .help("Review a sell order for every position: all active shares, limit 2 % below the price")
        }
    }
}

/// Closed positions: everything bought was sold again. Newest first.
struct HistoryView: View {
    let model: AppModel
    let snapshot: AccountSnapshot
    let isLive: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if let report = model.execution {
                    if report.closed.isEmpty {
                        Text("No closed positions yet — a position lands here once all its shares are sold.")
                            .foregroundStyle(.secondary)
                    } else {
                        // One card per ticker (newest closed first); a ticker closed more than once keeps its
                        // positions together in its card.
                        let order = report.closed.map(\.conid).reduce(into: [Int]()) { if !$0.contains($1) { $0.append($1) } }
                        ForEach(order, id: \.self) { conid in
                            StretchOrScroll {
                                ExecutionTable(rows: report.closed.filter { $0.conid == conid }, total: nil,
                                               base: report.baseCurrency)
                            }
                        }
                    }
                } else {
                    ProgressView().frame(maxWidth: .infinity)
                }
            }
            .padding(20)
        }
        .opacity(isLive ? 1 : 0.55)
        .task { await model.keepRefreshingExecution() }
    }
}

/// A small table per ticker (the ticker is its title). Purchase is fixed (bought, purchase price, cost, expected
/// return for information), with a sub-row per buy when there were several; Execution sums up what was sold or
/// is still active, with small Sold / Active sub-rows when there is more than one.
struct ExecutionTable: View {
    let rows: [ExecutionRow]
    let total: ExecutionTotal?
    let base: String

    var body: some View {
        Grid(alignment: .leading, horizontalSpacing: 18, verticalSpacing: 6) {
            ForEach(rows) { row in
                tickerHeader(row)
                purchaseRow(row)
                if row.purchases.count > 1 {
                    ForEach(row.purchases) { purchase in
                        purchaseLine(purchase, row: row)
                    }
                }
                executionRow(row)
                if row.lines.count > 1 {
                    ForEach(row.lines) { line in
                        lineRow(line, row: row)
                    }
                }
                Color.clear.frame(height: 6).gridCellUnsizedAxes(.horizontal)  // space between tickers
            }
            if let total {
                Divider()
                GridRow(alignment: .top) {
                    Text("Total")
                    Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(2)
                    Text(Format.money(total.cost, base)).gridColumnAlignment(.trailing)
                    Text(total.value.map { Format.money($0, base) } ?? "—").gridColumnAlignment(.trailing)
                    ReturnText(amount: total.profit.map { Format.signedMoney($0, base) },
                               percent: total.returnPct.map { Format.signedPercent($0) },
                               color: Format.color(total.profit))
                }
                .fontWeight(.semibold)
                .monospacedDigit()
            }
        }
        .padding(14)
        .background(.quaternary.opacity(0.4), in: RoundedRectangle(cornerRadius: 8))
    }

    /// The ticker is the table's title, in the first column next to the column names.
    private func tickerHeader(_ row: ExecutionRow) -> some View {
        GridRow(alignment: .lastTextBaseline) {
            HStack(spacing: 8) {
                Text(row.symbol).font(.headline)
                if let closed = row.closedAt {
                    Text("Closed \(Format.day(closed))").font(.caption).foregroundStyle(.secondary)
                }
            }
            Color.clear.frame(maxWidth: .infinity, maxHeight: 0)  // stretches: numbers sit on the right
            // Minimum widths keep the columns still while live numbers change length (more digits, sign).
            ForEach([("Quantity", 60.0), ("Price", 110.0), ("Cost / Value", 120.0),
                     ("Expected / Actual return", 210.0)], id: \.0) { title, width in
                Text(title).font(.caption).foregroundStyle(.secondary)
                    .frame(minWidth: width, alignment: .trailing)
                    .gridColumnAlignment(.trailing)
            }
        }
    }

    /// Fixed at purchase; the expected return only for information. One buy: its date in brackets.
    private func purchaseRow(_ row: ExecutionRow) -> some View {
        GridRow(alignment: .top) {
            HStack(spacing: 6) {
                Text("Purchase")
                if row.purchases.count == 1, let time = row.purchases.first?.time {
                    Text("(\(Format.day(time)))").foregroundStyle(.secondary)
                }
            }
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.quantity(row.bought)).gridColumnAlignment(.trailing)
            Text(row.avgPrice.map { Format.money($0, row.currency) } ?? "—").gridColumnAlignment(.trailing)
            Text(Format.money(row.cost, base)).gridColumnAlignment(.trailing)
            ReturnText(amount: row.expectedProfit.map { Format.money($0, base) },
                       percent: row.expectedReturnPct.map { Format.percent($0) }, color: .secondary)
        }
        .monospacedDigit()
    }

    /// One buy of several: its date, shares, average fill price and cost.
    private func purchaseLine(_ purchase: Purchase, row: ExecutionRow) -> some View {
        GridRow(alignment: .top) {
            Text(purchase.time.map { "Bought \(Format.day($0))" } ?? "Held before").padding(.leading, 14)
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.quantity(purchase.quantity)).gridColumnAlignment(.trailing)
            Text(Format.money(purchase.price, row.currency)).gridColumnAlignment(.trailing)
            Text(Format.money(purchase.cost, base)).gridColumnAlignment(.trailing)
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
        }
        .font(.caption)
        .foregroundStyle(.secondary)
        .padding(.top, -4)  // closer together, and closer to Purchase
        .monospacedDigit()
    }

    /// Everything bought, sold or still held, together: average price, value, actual return. With a single
    /// line (all held, or all sold at once) it says which and has no sub-rows.
    private func executionRow(_ row: ExecutionRow) -> some View {
        let shares = row.lines.reduce(Decimal(0)) { $0 + $1.quantity.value }
        let average = averagePrice(row)
        let value = executedValue(row)
        return GridRow(alignment: .top) {
            HStack(spacing: 6) {
                Text("Execution")
                if row.lines.count == 1, let line = row.lines.first {
                    HStack(spacing: 0) {
                        Text("(")
                        lineLabel(line)
                        Text(")")
                    }
                    .foregroundStyle(.secondary)
                }
            }
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.quantity(Amount(shares))).gridColumnAlignment(.trailing)
            Text(average.map { Format.money(Amount($0), row.currency) } ?? "—").gridColumnAlignment(.trailing)
            Text(value.map { Format.money(Amount($0), base) } ?? "—").gridColumnAlignment(.trailing)
            ReturnText(amount: row.profit.map { Format.signedMoney($0, base) },
                       percent: row.returnPct.map { Format.signedPercent($0) }, color: Format.color(row.profit))
        }
        .fontWeight(.semibold)
        .monospacedDigit()
    }

    /// Average of the sell prices and the live price, weighted by shares; nil while a price is missing.
    private func averagePrice(_ row: ExecutionRow) -> Decimal? {
        let shares = row.lines.reduce(Decimal(0)) { $0 + $1.quantity.value }
        guard shares > 0, row.lines.allSatisfy({ $0.price != nil }) else { return nil }
        return row.lines.reduce(Decimal(0)) { $0 + $1.quantity.value * $1.price!.value } / shares
    }

    /// Proceeds of the sales plus the market value of what is held.
    private func executedValue(_ row: ExecutionRow) -> Decimal? {
        guard !row.lines.isEmpty, row.lines.allSatisfy({ $0.value != nil }) else { return nil }
        return row.lines.reduce(Decimal(0)) { $0 + $1.value!.value }
    }

    /// Part of the execution: the shares still held (live price) or one sale (its sell price).
    private func lineRow(_ line: ExecutionLine, row: ExecutionRow) -> some View {
        GridRow(alignment: .top) {
            lineLabel(line).padding(.leading, 14)
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.quantity(line.quantity)).gridColumnAlignment(.trailing)
            Text(line.price.map { Format.money($0, row.currency) } ?? "—").gridColumnAlignment(.trailing)
            Text(line.value.map { Format.money($0, base) } ?? "—").gridColumnAlignment(.trailing)
            ReturnText(amount: line.profit.map { Format.signedMoney($0, base) },
                       percent: line.returnPct.map { Format.signedPercent($0) }, color: Format.color(line.profit))
        }
        .font(.caption)
        .foregroundStyle(.secondary)
        .padding(.top, -4)  // closer together, and closer to Execution
        .monospacedDigit()
    }

    private func lineLabel(_ line: ExecutionLine) -> some View {
        HStack(spacing: 6) {
            Text(line.isHeld ? "Active" : "Sold")
            if let time = line.time {
                Text(Format.day(time)).foregroundStyle(.secondary)
            }
        }
    }
}

/// Amount with the percentage next to it in brackets, on one line.
private struct ReturnText: View {
    let amount: String?
    let percent: String?
    let color: Color

    var body: some View {
        HStack(spacing: 6) {
            Text(amount ?? "—")
            if let percent { Text("(\(percent))") }
        }
        .fixedSize()  // amount and percent always on one line
        .foregroundStyle(amount == nil ? .secondary : color)
        .gridColumnAlignment(.trailing)
    }
}

/// T12 Execution tab: per ticker one row per buy and, once sold, its sale — the quantity is the same on both
/// sides (FIFO: sold rows first, then the active ones, whose sell columns stay empty). The ticker's total row
/// shows the active shares, the current value and the return, with the expected return under it.
struct TradesTable: View {
    let rows: [ExecutionRow]
    let total: ExecutionTotal?
    let base: String
    /// Tick boxes and a Sell button per ticker (nil while a sell review is open).
    var selling: AppModel?

    /// Share of the active shares' value in the value of all positions (as the Planner's weight).
    private func weight(_ row: ExecutionRow) -> Decimal? {
        let values = rows.compactMap { $0.value?.value }
        guard let value = row.value?.value, values.count == rows.count else { return nil }
        let all = values.reduce(Decimal(0), +)
        return all > 0 ? value / all * 100 : nil
    }

    /// Sold / Active / Total rows: a bit bigger than the rows they sum up.
    private static let summaryFont = Font.system(size: 14)

    var body: some View {
        Grid(alignment: .leading, horizontalSpacing: 16, verticalSpacing: 6) {
            ForEach(rows) { row in
                if row.id != rows.first?.id { Divider().padding(.vertical, 4) }  // between tickers, as above Total
                header(row)
                let sold = row.trades.filter { $0.soldAt != nil }
                if sold.count > 1 {
                    soldSummary(sold, row: row).font(Self.summaryFont)
                    ForEach(sold) { trade in
                        tradeRow(trade, row: row).font(.caption).foregroundStyle(.secondary).padding(.top, -4)
                    }
                } else if let trade = sold.first {
                    tradeRow(trade, row: row)
                }
                let active = row.trades.filter { $0.soldAt == nil }
                if active.count > 1 {
                    activeRow(active, row: row, summary: true, weight: weight(row)).font(Self.summaryFont)
                    ForEach(active) { trade in
                        activeRow([trade], row: row, summary: false)
                            .font(.caption).foregroundStyle(.secondary).padding(.top, -4)
                    }
                } else if !active.isEmpty {
                    activeRow(active, row: row, summary: false, weight: weight(row))
                }
                tickerTotal(row).font(Self.summaryFont)
            }
            if let total {
                Divider()
                GridRow(alignment: .top) {
                    Text("Total")
                    WeightText(value: total.value != nil ? 100 : nil)
                    Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(3)
                    Text(Format.money(total.cost, base)).gridColumnAlignment(.trailing)
                    Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(2)
                    Text(total.value.map { Format.money($0, base) } ?? "—").gridColumnAlignment(.trailing)
                    ReturnText(amount: total.profit.map { Format.signedMoney($0, base) },
                               percent: total.returnPct.map { Format.signedPercent($0) },
                               color: Format.color(total.profit))
                }
                .fontWeight(.semibold)
                .monospacedDigit()
            }
        }
        .lineLimit(1)  // every cell on one line
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)  // the card fills the window; the columns keep their width
        .background(.quaternary.opacity(0.4), in: RoundedRectangle(cornerRadius: 8))
    }

    /// Ticker (tick box, Sell) next to the column names.
    private func header(_ row: ExecutionRow) -> some View {
        GridRow(alignment: .lastTextBaseline) {
            HStack(spacing: 8) {
                if let model = selling {
                    Toggle("", isOn: Binding(get: { model.sellSelection.contains(row.conid) },
                                             set: { if $0 { model.sellSelection.insert(row.conid) }
                                                    else { model.sellSelection.remove(row.conid) } }))
                        .toggleStyle(.checkbox).labelsHidden()
                }
                Text(row.symbol).font(.headline)
                if let model = selling {
                    Button("Sell") { model.openSell([row.conid]) }
                        .controlSize(.small)
                        .help("Review a sell order for \(row.symbol): all active shares, limit 2 % below the price")
                }
            }
            .fixedSize()  // ticker, tick box and Sell on one line
            // Columns as wide as their content: the same gap between all of them.
            ForEach(["Weight", "Quantity", "Bought", "Purchase price", "Cost", "Sold", "Sell / Current price", "Value",
                     "Realized / Unrealized return"], id: \.self) { title in
                Text(title).font(.caption).foregroundStyle(.secondary)
                    .fixedSize()
                    .gridColumnAlignment(.trailing)
            }
        }
    }

    private func tradeRow(_ trade: ExecutionTrade, row: ExecutionRow) -> some View {
        GridRow(alignment: .top) {
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.quantity(trade.quantity))
            Text(trade.boughtAt.map(Format.day) ?? "Before").foregroundStyle(.secondary)
            Text(Format.money(trade.buyPrice, row.currency))
            Text(Format.money(trade.cost, base))
            Text(trade.soldAt.map(Format.day) ?? "").foregroundStyle(.secondary)
            Text(trade.sellPrice.map { Format.money($0, row.currency) } ?? "")
            Text(trade.value.map { Format.money($0, base) } ?? "")
            if let profit = trade.profit {
                ReturnText(amount: Format.signedMoney(profit, base),
                           percent: trade.returnPct.map { Format.signedPercent($0) }, color: Format.color(profit))
            } else {
                Text("")
            }
        }
        .monospacedDigit()
    }

    /// All sales of the ticker together: shares, average purchase price, cost, average sell price, proceeds,
    /// return. The sales themselves follow as small sub-rows.
    private func soldSummary(_ sold: [ExecutionTrade], row: ExecutionRow) -> some View {
        let shares = sold.reduce(Decimal(0)) { $0 + $1.quantity.value }
        let cost = sold.reduce(Decimal(0)) { $0 + $1.cost.value }
        let value = sold.reduce(Decimal(0)) { $0 + ($1.value?.value ?? 0) }
        let buy = sold.reduce(Decimal(0)) { $0 + $1.quantity.value * $1.buyPrice.value } / shares
        let sell = sold.reduce(Decimal(0)) { $0 + $1.quantity.value * ($1.sellPrice?.value ?? 0) } / shares
        let profit = value - cost
        return GridRow(alignment: .top) {
            Text("Sold")
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.quantity(Amount(shares)))
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.money(Amount(buy), row.currency))
            Text(Format.money(Amount(cost), base))
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.money(Amount(sell), row.currency))
            Text(Format.money(Amount(value), base))
            ReturnText(amount: Format.signedMoney(Amount(profit), base),
                       percent: cost > 0 ? Format.signedPercent(Amount(profit / cost * 100)) : nil,
                       color: Format.color(Amount(profit)))
        }
        .monospacedDigit()
    }

    /// Shares still held — one buy, or all of them together (summary) above their sub-rows: weight (on the
    /// ticker's own row only), shares,
    /// average purchase price, cost, the value at the live price and the return so far, muted green / red
    /// (not realized). The live price is in the ticker's total row only.
    private func activeRow(_ trades: [ExecutionTrade], row: ExecutionRow, summary: Bool,
                           weight: Decimal? = nil) -> some View {
        let shares = trades.reduce(Decimal(0)) { $0 + $1.quantity.value }
        let cost = trades.reduce(Decimal(0)) { $0 + $1.cost.value }
        let buy = trades.reduce(Decimal(0)) { $0 + $1.quantity.value * $1.buyPrice.value } / shares
        // The position's value at the live price (base currency), split by shares.
        let value: Decimal? = row.value.flatMap { total in row.held.value > 0 ? total.value * shares / row.held.value : nil }
        let profit = value.map { $0 - cost }
        return GridRow(alignment: .top) {
            if summary { Text("Active") } else { Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]) }
            WeightText(value: weight)
            Text(Format.quantity(Amount(shares)))
            if summary {
                Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            } else {
                Text(trades[0].boughtAt.map(Format.day) ?? "Before").foregroundStyle(.secondary)
            }
            Text(Format.money(Amount(buy), row.currency))
            Text(Format.money(Amount(cost), base))
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(2)
            Text(value.map { Format.money(Amount($0), base) } ?? "—")  // at the live price
            ReturnText(amount: profit.map { Format.signedMoney(Amount($0), base) },
                       percent: profit.flatMap { cost > 0 ? Format.signedPercent(Amount($0 / cost * 100)) : nil },
                       color: Format.color(profit.map(Amount.init)).opacity(0.4))  // not realized: muted
        }
        .monospacedDigit()
    }

    /// Value = proceeds of the sales + active shares at the live price.
    private func tickerTotal(_ row: ExecutionRow) -> some View {
        let values = row.lines.map(\.value)
        let value: Decimal? = values.isEmpty || values.contains { $0 == nil } ? nil
            : values.reduce(Decimal(0)) { $0 + $1!.value }
        return GridRow(alignment: .top) {
            Text("Total")
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.quantity(row.bought))
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(row.avgPrice.map { Format.money($0, row.currency) } ?? "—")
            Text(Format.money(row.cost, base))
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(row.price.map { Format.money($0, row.currency) } ?? "—")
            Text(value.map { Format.money(Amount($0), base) } ?? "—")
            VStack(alignment: .trailing, spacing: 1) {
                ReturnText(amount: row.profit.map { Format.signedMoney($0, base) },
                           percent: row.returnPct.map { Format.signedPercent($0) }, color: Format.color(row.profit))
                if let expected = row.expectedProfit, let percent = row.expectedReturnPct {
                    Text("Expected \(Format.money(expected, base)) (\(Format.percent(percent)))")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .fontWeight(.semibold)
        .monospacedDigit()
    }
}

/// T12: the sell orders to review — one per ticker, editable and removable until placed. Same rules as the plan's
/// buys: DAY, regular hours only, nothing placed while the market is closed.
struct SellReviewView: View {
    let model: AppModel

    private var basket: SellBasketModel { model.sellBasket }

    var body: some View {
        TimelineView(.periodic(from: .now, by: 10)) { context in
            content(now: context.date)
        }
    }

    @ViewBuilder
    private func content(now: Date) -> some View {
        let allowClosed = model.basket.allowPlacingWhenClosed
        let reason = basket.blockingReason(allowWhenClosed: allowClosed, now: now)
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Sell orders").font(.title3.weight(.semibold))
                Text("DAY · regular hours").font(.caption).foregroundStyle(.secondary)
                Spacer()
                if !USMarket.isRegularSession(now) {
                    Label(allowClosed ? "Market closed · placing allowed (dev)" : "Market closed",
                          systemImage: "moon.zzz.fill")
                        .font(.callout).foregroundStyle(.orange)
                }
            }
            Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 6) {
                GridRow {
                    ForEach(["", "Quantity", "Limit", "Price", "Value", " "], id: \.self) {
                        Text($0).font(.caption).foregroundStyle(.secondary)
                    }
                }
                ForEach(basket.lines) { line in
                    row(line)
                    if let notes = notes(line) {
                        GridRow {
                            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
                            notes.gridCellColumns(5)
                        }
                    }
                }
            }
            HStack(spacing: 10) {
                Spacer()
                actions(reason: reason)
            }
            .padding(.top, 6)
        }
        .padding(14)
        .background(.quaternary.opacity(0.4), in: RoundedRectangle(cornerRadius: 8))
    }

    private func row(_ line: SellBasketModel.Line) -> some View {
        let contract = line.contract
        let value: Decimal? = if line.isFilled, let order = line.order, let fill = order.avgFillPrice {
            order.filled.value * fill.value
        } else {
            line.shownQuantity.flatMap { q in line.shownLimit.map { q * $0 } }
        }
        let editable = !line.isPlaced && basket.stage != .placing
        return GridRow(alignment: .firstTextBaseline) {
            HStack(spacing: 6) {
                Text("SELL").font(.caption.weight(.bold)).foregroundStyle(.red)
                Text(contract.symbol).fontWeight(.semibold)
            }
            if line.isPlaced {
                Text(line.shownQuantity.map { Format.quantity(Amount($0)) } ?? "—").gridColumnAlignment(.trailing)
                Text(line.shownLimit.map { Format.money(Amount($0), contract.currency) } ?? "—")
                    .gridColumnAlignment(.trailing)
            } else {
                NumericField(placeholder: "qty", text: Binding(get: { line.quantityText },
                                                               set: { basket.setQuantity(line.id, $0) }),
                             allowsDecimal: true, isInvalid: line.quantity == nil || (line.quantity ?? 0) > line.held)
                    .disabled(!editable)
                    .help("Active shares: \(Format.quantity(Amount(line.held)))")
                NumericField(placeholder: "limit", text: Binding(get: { line.limitText },
                                                                 set: { basket.setLimit(line.id, $0) }),
                             allowsDecimal: true, isInvalid: line.limit == nil, width: 90)
                    .disabled(!editable)
                    .help(line.limitFollowsPrice ? "2 % below the live price — follows it until you type a limit" : "")
            }
            Text(line.isFilled ? line.order?.avgFillPrice.map { Format.money($0, contract.currency) } ?? "—"
                 : model.quotes[contract.feedSymbol].map { Format.money($0.last, contract.currency) } ?? "—")
                .gridColumnAlignment(.trailing)
            Text(value.map { Format.money(Amount($0), contract.currency) } ?? "—").gridColumnAlignment(.trailing)
            HStack(spacing: 6) {
                switch line.result {
                case .placed(let orderId, let status):
                    Label("#\(orderId) \(Order.statusLabel(status))", systemImage: BasketView.statusIcon(status))
                        .foregroundStyle(BasketView.statusColor(status))
                        .font(.callout)
                case .failed:
                    Image(systemName: "xmark.octagon.fill").foregroundStyle(.red)
                case nil:
                    EmptyView()
                }
                if editable {
                    Button { basket.remove(line.id) } label: { Image(systemName: "minus.circle") }
                        .buttonStyle(.borderless)
                        .help("Remove from the sell orders")
                }
            }
        }
        .monospacedDigit()
    }

    /// Failure and block (red), warnings (orange) under the row.
    private func notes(_ line: SellBasketModel.Line) -> AnyView? {
        var items: [(String, Color)] = []
        if case .failed(let reason) = line.result { items.append((reason, .red)) }
        if !line.isPlaced {
            if let block = line.preview?.block { items.append((block.message, .red)) }
            for warning in line.preview?.warnings ?? [] where warning.code != "market_closed" {
                items.append((warning.message, warning.level == .strong ? .red : warning.level == .info ? .secondary : .orange))
            }
            if let error = line.previewError { items.append((error, .secondary)) }
        }
        guard !items.isEmpty else { return nil }
        return AnyView(VStack(alignment: .leading, spacing: 2) {
            ForEach(items.indices, id: \.self) { index in
                Label(items[index].0, systemImage: items[index].1 == .secondary ? "info.circle"
                                                                                  : "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(items[index].1)
            }
        })
    }

    @ViewBuilder
    private func actions(reason: String?) -> some View {
        switch basket.stage {
        case .reviewing:
            Button("Cancel") { basket.close() }
            VStack(alignment: .trailing, spacing: 4) {
                Button("Place \(basket.pendingCount) sell order\(basket.pendingCount == 1 ? "" : "s")") {
                    Task { await model.placeSells() }
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .disabled(reason != nil)
                if let reason { Text(reason).font(.caption).foregroundStyle(.secondary) }
            }
        case .placing:
            ProgressView().controlSize(.small)
            Text("Placing orders…")
        case .placed:
            if basket.failedCount > 0 {
                Label("\(basket.failedCount) failed", systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
                Button("Retry \(basket.failedCount) failed") { Task { await model.placeSells() } }
                    .disabled(reason != nil)
                    .help(reason ?? "Resend only the failed orders (same references, never duplicated)")
            }
            if basket.anyWorking {
                Label("Waiting for fills", systemImage: "clock").foregroundStyle(.blue)
                    .help("Change or cancel them in Overview → Open orders")
            } else if basket.allFilled {
                Label("All orders filled", systemImage: "checkmark.seal.fill").foregroundStyle(.green)
            }
            if basket.canClose {
                Button("Done") { basket.close() }.buttonStyle(.borderedProminent)
            }
        case .closed:
            EmptyView()
        }
    }
}
