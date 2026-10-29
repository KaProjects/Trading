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
                        // Every closed position in one table (latest closed first), separated by lines like the
                        // tickers in Execution: the columns line up without fixed widths.
                        StretchOrScroll {
                            TradesTable(rows: report.closed, total: nil, base: report.baseCurrency, history: true)
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

/// T12 Execution and History tables: per ticker its buys paired with the sales their shares went into (FIFO) —
/// Sold (sum, sales as sub-rows), Active (weight, live price, value, unrealized return; buys as sub-rows) and the
/// ticker's whole return. History shows a closed position's sales only.
struct TradesTable: View {
    let rows: [ExecutionRow]
    let total: ExecutionTotal?
    let base: String
    /// Tick boxes and a Sell button per ticker (nil while a sell review is open).
    var selling: AppModel?
    /// History: closed positions — their sales only (no weight, Active or ticker total).
    var history = false

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
            // Spans every column: extra width is shared by all of them (even with a single ticker, no line below).
            GridRow { Color.clear.frame(maxWidth: .infinity, maxHeight: 0).gridCellColumns(history ? 9 : 10) }
            ForEach(rows) { row in
                if row.id != rows.first?.id { Divider().padding(.vertical, 4) }  // between tickers, as above Total
                header(row)
                let sold = row.trades.filter { $0.soldAt != nil }
                if history && !sold.isEmpty {
                    // A closed position: its sales, then their sum as the Total below them (with the expected return).
                    ForEach(sold) { trade in
                        tradeRow(trade, row: row).font(.caption).foregroundStyle(.secondary).padding(.top, -4)
                    }
                    soldSummary(sold, row: row, label: "Total").font(Self.summaryFont)
                } else if sold.count > 1 {
                    soldSummary(sold, row: row, label: "Sold").font(Self.summaryFont)
                    ForEach(sold) { trade in
                        tradeRow(trade, row: row).font(.caption).foregroundStyle(.secondary).padding(.top, -4)
                    }
                } else if let trade = sold.first {
                    tradeRow(trade, row: row)
                }
                let active = history ? [] : row.trades.filter { $0.soldAt == nil }
                if active.count > 1 {
                    activeRow(active, row: row, summary: true, own: true, weight: weight(row)).font(Self.summaryFont)
                    ForEach(active) { trade in
                        activeRow([trade], row: row, summary: false, own: false)
                            .font(.caption).foregroundStyle(.secondary).padding(.top, -4)
                    }
                } else if !active.isEmpty {
                    activeRow(active, row: row, summary: false, own: true, weight: weight(row))
                }
                if !history {
                    GridRow {  // a double line above the ticker's total return
                        Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(9)
                        VStack(spacing: 2) { Divider(); Divider() }
                    }
                    tickerTotal(row).font(Self.summaryFont)
                }
            }
            if let total {
                Divider()
                // The Active rows added up (weight, value, unrealized return, muted) …
                let held = rows.compactMap { $0.lines.first(where: \.isHeld) }
                let values = held.map(\.value)
                let value: Decimal? = values.contains { $0 == nil } ? nil : values.reduce(Decimal(0)) { $0 + $1!.value }
                let profits = held.map(\.profit)
                let profit: Decimal? = profits.contains { $0 == nil } ? nil : profits.reduce(Decimal(0)) { $0 + $1!.value }
                let cost = held.reduce(Decimal(0)) { $0 + $1.cost.value }
                GridRow(alignment: .top) {
                    Text("Total active")
                    WeightText(value: value != nil ? 100 : nil)
                    Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(6)
                    Text(value.map { Format.money(Amount($0), base) } ?? "—")
                    ReturnText(amount: profit.map { Format.signedMoney(Amount($0), base) },
                               percent: profit.flatMap { cost > 0 ? Format.signedPercent(Amount($0 / cost * 100)) : nil },
                               color: Format.color(profit.map(Amount.init)).opacity(0.4))
                }
                .fontWeight(.semibold)
                .monospacedDigit()
                .fixedSize(horizontal: true, vertical: false)  // every cell: never cut short ("…")
                // … then the whole return: realized + unrealized, against the cost of everything bought.
                GridRow(alignment: .top) {
                    Text("Total")
                    Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(8)
                    ReturnText(amount: total.profit.map { Format.signedMoney($0, base) },
                               percent: total.returnPct.map { Format.signedPercent($0) },
                               color: Format.color(total.profit))
                }
                .fontWeight(.semibold)
                .monospacedDigit()
                .fixedSize(horizontal: true, vertical: false)
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
                if let closed = row.closedAt {
                    Text("Closed \(Format.day(closed))").font(.caption).foregroundStyle(.secondary)
                }
                if let model = selling {
                    Button("Sell") { model.openSell([row.conid]) }
                        .controlSize(.small)
                        .help("Review a sell order for \(row.symbol): all active shares, limit 2 % below the price")
                }
            }
            .fixedSize()  // ticker, tick box and Sell on one line
            // Columns as wide as their content: the same gap between all of them.
            ForEach(history ? ["Quantity", "Bought", "Purchase price", "Cost", "Sold", "Sell price", "Value",
                               "Realized return"]
                            : ["Weight", "Quantity", "Bought", "Purchase price", "Cost", "Sold", "Sell / Current price",
                               "Value", "Realized / Unrealized return"], id: \.self) { title in
                Text(title).font(.caption).foregroundStyle(.secondary)
                    .fixedSize()
                    .gridColumnAlignment(.trailing)
            }
        }
    }

    private func tradeRow(_ trade: ExecutionTrade, row: ExecutionRow) -> some View {
        GridRow(alignment: .top) {
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            if !history { Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]) }  // weight
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
        .fixedSize(horizontal: true, vertical: false)  // every cell: never cut short ("…")
    }

    /// All sales of the ticker together: shares, average purchase price, cost, average sell price, proceeds,
    /// return. The sales themselves follow as small sub-rows.
    private func soldSummary(_ sold: [ExecutionTrade], row: ExecutionRow, label: String) -> some View {
        let shares = sold.reduce(Decimal(0)) { $0 + $1.quantity.value }
        let cost = sold.reduce(Decimal(0)) { $0 + $1.cost.value }
        let value = sold.reduce(Decimal(0)) { $0 + ($1.value?.value ?? 0) }
        let buy = sold.reduce(Decimal(0)) { $0 + $1.quantity.value * $1.buyPrice.value } / shares
        let sell = sold.reduce(Decimal(0)) { $0 + $1.quantity.value * ($1.sellPrice?.value ?? 0) } / shares
        let profit = value - cost
        return GridRow(alignment: .top) {
            Text(label)
            if !history { Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]) }  // weight
            Text(Format.quantity(Amount(shares)))
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.money(Amount(buy), row.currency))
            Text(Format.money(Amount(cost), base))
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.money(Amount(sell), row.currency))
            Text(Format.money(Amount(value), base))
            VStack(alignment: .trailing, spacing: 1) {
                ReturnText(amount: Format.signedMoney(Amount(profit), base),
                           percent: cost > 0 ? Format.signedPercent(Amount(profit / cost * 100)) : nil,
                           color: Format.color(Amount(profit)))
                if history, let expected = row.expectedProfit, let percent = row.expectedReturnPct {
                    Text("Expected \(Format.money(expected, base)) (\(Format.percent(percent)))")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if history { deltaText(row) }
            }
        }
        .monospacedDigit()
        .fixedSize(horizontal: true, vertical: false)  // every cell: never cut short ("…")
    }

    /// Shares still held — one buy, or all of them together (summary) above their sub-rows: weight (on the
    /// ticker's own row only), shares,
    /// average purchase price, cost, the value at the live price and the return so far, muted green / red
    /// (not realized). The live price is in the ticker's total row only.
    /// own: the ticker's Active row (not a sub-row) — it also shows the live price.
    private func activeRow(_ trades: [ExecutionTrade], row: ExecutionRow, summary: Bool, own: Bool,
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
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(own ? row.price.map { Format.money($0, row.currency) } ?? "—" : "")
            Text(value.map { Format.money(Amount($0), base) } ?? "—")  // at the live price
            ReturnText(amount: profit.map { Format.signedMoney(Amount($0), base) },
                       percent: profit.flatMap { cost > 0 ? Format.signedPercent(Amount($0 / cost * 100)) : nil },
                       color: Format.color(profit.map(Amount.init)).opacity(0.4))  // not realized: muted
        }
        .monospacedDigit()
        .fixedSize(horizontal: true, vertical: false)  // every cell: never cut short ("…")
    }

    /// History: Delta = actual return − expected return, in money and percentage points; muted green / red, small
    /// like the expected line above it.
    @ViewBuilder
    private func deltaText(_ row: ExecutionRow) -> some View {
        if let difference = row.difference {
            let points = row.returnPct.flatMap { actual in row.expectedReturnPct.map { actual.value - $0.value } }
            Text("Delta \(Format.signedMoney(difference, base))"
                 + (points.map { " (\(Format.signedPercent(Amount($0))))" } ?? ""))
                .font(.caption)
                .foregroundStyle(Format.color(difference).opacity(0.4))  // muted, like unrealized returns
        }
    }

    /// The ticker's whole return (realized + unrealized) with the expected return under it; nothing else.
    private func tickerTotal(_ row: ExecutionRow) -> some View {
        GridRow(alignment: .top) {
            Text("Total")
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(8)
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
        .fixedSize(horizontal: true, vertical: false)  // every cell: never cut short ("…")
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
