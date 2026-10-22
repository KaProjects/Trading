import SwiftUI
import TraderCore

/// T10: Holdings (as in the Planner) and executed plans still running (FIFO lots), oldest first. Read-only.
/// Closed plans (everything sold) move to History.
struct ExecutionView: View {
    let model: AppModel
    let snapshot: AccountSnapshot
    let isLive: Bool

    var body: some View {
        let holdings = PlannerSummary.make(account: snapshot.account, positions: snapshot.positions,
                                           orders: snapshot.orders, quotes: model.quotes, rows: [])
        let base = snapshot.account.baseCurrency
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                holdingsSection(holdings, base: base)
                if let report = model.execution {
                    // Oldest first (the report is newest first); History keeps newest first.
                    let running = report.plans.filter { !$0.isOutside && $0.status != "closed" }.reversed()
                    if running.isEmpty {
                        Text("No executed plans yet — commit and place a plan in the Planner.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(running) { plan in
                        PlanCard(plan: plan, base: base)
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

    /// Same format as the Planner's Holdings: full width, ticker left, weight · quantity · price · market value right.
    private func holdingsSection(_ plan: PlannerSummary, base: String) -> some View {
        Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 6) {
            GridRow {
                Text("Holdings").font(.headline)
                Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
                ForEach(["Weight", "Quantity", "Price", "Market value"], id: \.self) {
                    Text($0).font(.caption).foregroundStyle(.secondary)
                }
            }
            ForEach(plan.holdings) { holding in
                let currency = holding.position.contract.currency
                GridRow(alignment: .firstTextBaseline) {
                    Text(holding.position.contract.symbol).fontWeight(.semibold)
                    Color.clear.frame(maxWidth: .infinity, maxHeight: 0)  // stretches: numbers sit on the right
                    WeightText(value: plan.weight(of: holding))
                    Text(Format.quantity(holding.position.quantity)).gridColumnAlignment(.trailing)
                    Text(holding.quote.map { Format.money($0.last, currency) } ?? "—").gridColumnAlignment(.trailing)
                    VStack(alignment: .trailing, spacing: 0) {
                        Text((holding.isApproximate ? "≈ " : "") + Format.money(Amount(holding.marketValueBase), base))
                        if currency != base {
                            Text("(\(Format.money(Amount(holding.marketValue), currency)))")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .gridColumnAlignment(.trailing)
                }
                .monospacedDigit()
            }
            Divider()  // separator above the total
            GridRow {
                Text("Total")
                Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
                WeightText(value: plan.holdingsTotal > 0 ? 100 : nil)
                Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(2)
                Text(Format.money(Amount(plan.holdingsTotal), base)).monospacedDigit().gridColumnAlignment(.trailing)
            }
            .fontWeight(.semibold)
        }
    }
}

/// Finished plans: everything bought was sold again. Newest first.
struct HistoryView: View {
    let model: AppModel
    let snapshot: AccountSnapshot
    let isLive: Bool

    var body: some View {
        let base = snapshot.account.baseCurrency
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if let report = model.execution {
                    let closed = report.plans.filter { !$0.isOutside && $0.status == "closed" }
                    if closed.isEmpty {
                        Text("No finished plans yet — a plan lands here once everything it bought is sold.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(closed) { plan in
                        PlanCard(plan: plan, base: base)
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

/// One executed plan: a small table per ticker (the ticker is its title). Purchase is fixed (bought, purchase price,
/// cost, expected return for information); Execution sums up what was sold or is still held, with small Sold /
/// Held sub-rows when there is more than one.
struct PlanCard: View {
    let plan: PlanExecution
    let base: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            header
            Grid(alignment: .leading, horizontalSpacing: 18, verticalSpacing: 6) {
                ForEach(plan.rows) { row in
                    tickerHeader(row)
                    purchaseRow(row)
                    executionRow(row)
                    if row.lines.count > 1 {
                        ForEach(row.lines) { line in
                            lineRow(line, row: row)
                        }
                    }
                    Color.clear.frame(height: 6).gridCellUnsizedAxes(.horizontal)  // space between tickers
                }
                Divider()
                GridRow(alignment: .top) {
                    Text("Total")
                    Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(3)
                    // Value of every ticker's execution.
                    let values = plan.rows.map(executedValue)
                    let value: Decimal? = values.contains(where: { $0 == nil }) ? nil
                        : values.reduce(Decimal(0)) { $0 + $1! }
                    Text(value.map { Format.money(Amount($0), base) } ?? "—").gridColumnAlignment(.trailing)
                    ReturnText(amount: plan.profit.map { Format.signedMoney($0, base) },
                               percent: plan.returnPct.map { Format.signedPercent($0) }, color: Format.color(plan.profit))
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
            Text(row.symbol).font(.headline)
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

    /// Fixed at purchase; the expected return only for information.
    private func purchaseRow(_ row: ExecutionRow) -> some View {
        GridRow(alignment: .top) {
            Text("Purchase")
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
            Text(Format.quantity(row.bought)).gridColumnAlignment(.trailing)
            Text(row.avgPrice.map { Format.money($0, row.currency) } ?? "—").gridColumnAlignment(.trailing)
            Text(Format.money(row.cost, base)).gridColumnAlignment(.trailing)
            ReturnText(amount: row.expectedProfit.map { Format.money($0, base) },
                       percent: row.expectedReturnPct.map { Format.percent($0) }, color: .secondary)
        }
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
                Text(time.formatted(.dateTime.day().month(.abbreviated))).foregroundStyle(.secondary)
            }
        }
    }

    private var header: some View {
        HStack(spacing: 10) {
            Text("Plan").font(.headline)
            if let placed = plan.placedAt { Text(Format.dateTime(placed)).foregroundStyle(.secondary) }
            Text(plan.statusLabel)
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 6).padding(.vertical, 2)
                .background(statusColor.opacity(0.18), in: Capsule())
                .foregroundStyle(statusColor)
            if plan.targetReached {
                Label("Target reached", systemImage: "checkmark.seal.fill")
                    .font(.caption).foregroundStyle(.green)
            }
            Spacer()
        }
    }

    private var statusColor: Color {
        switch plan.status {
        case "waiting": .blue
        case "open": .green
        case "partly_sold": .orange
        default: .secondary
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
        .foregroundStyle(amount == nil ? .secondary : color)
        .gridColumnAlignment(.trailing)
    }
}
