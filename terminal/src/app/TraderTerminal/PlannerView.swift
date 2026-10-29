import SwiftUI
import TraderCore

/// T8: account value − holdings = cash − reserved by open buys = available − planned purchases = left.
struct PlannerView: View {
    let model: AppModel
    let snapshot: AccountSnapshot
    let isLive: Bool
    @State private var ticker = ""
    @State private var candidates: [Contract] = []
    @State private var lookupMessage: String?

    var body: some View {
        let plan = PlannerSummary.make(account: snapshot.account, positions: snapshot.positions,
                                       orders: snapshot.orders, quotes: model.quotes, rows: model.effectivePlanRows)
        let base = plan.baseCurrency
        ScrollView {
            // Stretches with the window; scrolls sideways when the window is narrower than the columns need.
            StretchOrScroll {
                // 8 columns: name | exp. volume | exp. return | exp. profit | weight | quantity | price | cost / market value
                Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 6) {
                    totalRow("Account value", "if everything were sold", plan.accountValue, base, emphasis: .large)

                    // Holdings: weight · quantity · price · market value — the same last four columns as the plan.
                    sectionHeader("Holdings", columns: ["", "", "", "Weight", "Quantity", "Price", "Market value"])
                    // Positions bought by this plan go last, below a separator, until Finish.
                    let newConids = model.basket.newlyBoughtConids
                    let existing = plan.holdings.filter { !newConids.contains($0.position.contract.conid) }
                    let bought = plan.holdings.filter { newConids.contains($0.position.contract.conid) }
                    ForEach(existing + bought) { holding in
                        let currency = holding.position.contract.currency
                        if holding.id == bought.first?.id {
                            GridRow {
                                VStack(alignment: .leading, spacing: 2) {
                                    Divider()
                                    Text("Bought by this plan").font(.caption).foregroundStyle(.secondary)
                                }
                                .gridCellColumns(8)
                            }
                        }
                        GridRow(alignment: .firstTextBaseline) {
                            Text(holding.position.contract.symbol).fontWeight(.semibold)
                            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(3)
                            WeightText(value: plan.weight(of: holding))
                            Text(Format.quantity(holding.position.quantity)).gridColumnAlignment(.trailing)
                            Text(holding.quote.map { Format.money($0.last, currency) } ?? "—").gridColumnAlignment(.trailing)
                            // USD (base) value; a foreign listing (edge case) shows its own currency below in brackets.
                            VStack(alignment: .trailing, spacing: 0) {
                                Text((holding.isApproximate ? "≈ " : "") + Format.money(Amount(holding.marketValueBase), base))
                                if currency != base {
                                    Text("(\(Format.money(Amount(holding.marketValue), currency)))")
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            .gridColumnAlignment(.trailing)
                            .help(holding.isApproximate ? "No live price yet: cost basis used" : "")
                        }
                        .monospacedDigit()
                    }
                    totalRow("Holdings", nil, plan.holdingsTotal, base, sign: "−",
                             weight: plan.holdingsTotal > 0 ? 100 : nil)
                    Divider()
                    totalRow("Cash", "account value − holdings", plan.cash, base, sign: "=")
                    totalRow("Reserved by open buy orders", nil, plan.reservedByOpenBuys, base, sign: "−")
                    totalRow("Available to invest", nil, plan.available, base, sign: "=", emphasis: .bold)

                    sectionHeader("Planned purchases",
                                  columns: ["Exp. volume", "Exp. return", "Exp. profit", "Weight", "Quantity", "Price", "Cost"])
                    ForEach(plan.planned) { item in
                        plannedRow(item, base: base, weight: plan.weight(of: item))
                    }
                    let expected = PlanReturn.total(plan.planned.map { ($0.costBase, model.planReturnPercent($0.row.id)) })
                    if !model.basket.isFrozen {
                        GridRow {
                            addTicker.gridCellColumns(8)
                        }
                    }
                    totalRow("Planned", "of available to invest", plan.plannedTotal, base, sign: "−", emphasis: .large,
                             weight: plan.plannedWeight,
                             profit: expected.map { $0.amount })
                    Divider()
                    totalRow("Left after planned purchases", nil, plan.remaining, base, sign: "=",
                             negativeIsRed: true, weight: plan.remainingWeight)
                    GridRow {
                        planActions(plan).gridCellColumns(8)
                    }
                }
                .padding(20)
            }
            if model.basket.isFrozen {
                StretchOrScroll {
                    BasketView(model: model, account: snapshot.account, available: plan.available)
                        .padding(.horizontal, 20)
                        .padding(.bottom, 20)
                }
            }
        }
        .opacity(isLive ? 1 : 0.55)
    }

    // MARK: - Plan actions (T9)

    @ViewBuilder
    private func planActions(_ plan: PlannerSummary) -> some View {
        HStack(spacing: 12) {
            if model.basket.stage == .planning {
                Spacer()
                Button("Commit plan") { Task { await model.commitPlan() } }
                    .buttonStyle(.borderedProminent)
                    .disabled(!PlanCommit.canCommit(plan))
                    .help(PlanCommit.canCommit(plan)
                          ? "Freeze the plan and prepare buy orders"
                          : "Needs ≥ 1 row, a quantity and price on every row, and nothing left negative")
            } else {
                // Centred together: the label and, until orders are sent, Replan next to it.
                let executed = model.basket.stage == .placing || model.basket.stage == .placed
                HStack(spacing: 10) {
                    if executed {
                        Label("Plan executed", systemImage: "checkmark.seal.fill").foregroundStyle(.green)
                    } else {
                        Label("Plan committed", systemImage: "lock.fill").foregroundStyle(.secondary)
                    }
                    if model.basket.stage == .committed {
                        Button("Replan") { model.basket.replan() }
                            .help("Undo the commit: edit the plan again (nothing has been sent)")
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.top, 8)
    }

    // MARK: - Rows

    private enum Emphasis { case normal, bold, large }

    private func totalRow(_ title: String, _ hint: String?, _ value: Decimal, _ base: String, sign: String = "",
                          emphasis: Emphasis = .normal, negativeIsRed: Bool = false,
                          weight: Decimal? = nil, profit: Decimal? = nil) -> some View {
        GridRow(alignment: .firstTextBaseline) {
            HStack(spacing: 6) {
                Text(sign).foregroundStyle(.secondary).frame(width: 12)
                Text(title)
                if let hint { Text(hint).foregroundStyle(.secondary).font(.caption) }
            }
            .gridCellColumns(3)
            Text(profit.map { Format.money(Amount($0), base) } ?? "")  // Σ expected profit (Planned row)
                .monospacedDigit()
                .gridColumnAlignment(.trailing)
            WeightText(value: weight, negativeIsRed: negativeIsRed)
            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]).gridCellColumns(2)
            Text(Format.money(Amount(value), base))
                .foregroundStyle(negativeIsRed && value < 0 ? .red : .primary)
                .monospacedDigit()
                .gridColumnAlignment(.trailing)
        }
        .font(emphasis == .large ? .title3.weight(.semibold) : emphasis == .bold ? .body.weight(.semibold) : .body)
    }

    private func sectionHeader(_ title: String, columns: [String]) -> some View {
        GridRow {
            Text(title).font(.headline)
            ForEach(columns.indices, id: \.self) { Text(columns[$0]).font(.caption).foregroundStyle(.secondary) }
        }
        .padding(.top, 12)
    }

    private func plannedRow(_ item: PlannerSummary.Planned, base: String, weight: Decimal?) -> some View {
        let contract = item.row.contract
        return GridRow(alignment: .firstTextBaseline) {
            HStack(spacing: 6) {
                Button { model.removePlanRow(item.row.id) } label: { Image(systemName: "minus.circle") }
                    .buttonStyle(.borderless)
                    .disabled(model.basket.isFrozen)
                    .help("Remove")
                VStack(alignment: .leading, spacing: 0) {
                    Text(contract.symbol).fontWeight(.semibold)
                    Text("\(contract.name) · \(contract.primaryExchange)").font(.caption).foregroundStyle(.secondary)
                }
            }
            // Volume: positive number, decimals allowed (2.5 = 2 500); may stay empty.
            NumericField(placeholder: "k", text: Binding(get: { model.planVolumeText[item.row.id] ?? "" },
                                                         set: { model.setPlanVolume(item.row.id, text: $0) }),
                         allowsDecimal: true)
                .gridColumnAlignment(.trailing)
                .disabled(model.basket.isFrozen)
                .help("Amount to spend in thousands (30 = 30 000); fills Quantity, rounded up to whole shares")
            // Expected return: positive percent, decimals allowed; may stay empty.
            VStack(alignment: .trailing, spacing: 2) {
                NumericField(placeholder: "%", text: Binding(get: { model.planReturnText[item.row.id] ?? "" },
                                                             set: { model.setPlanReturn(item.row.id, text: $0) }),
                             allowsDecimal: true, width: 60)
                    .disabled(model.basket.isFrozen)
                    .help("Expected return in % of the cost (your estimate)")
                if let from = model.planReturnPrefilled[item.row.id] {
                    Text("Prefilled from plan of \(Format.day(from))")
                        .font(.caption).foregroundStyle(.secondary).fixedSize()
                }
            }
            .gridColumnAlignment(.trailing)
            Text(PlanReturn.amount(cost: item.costBase, percent: model.planReturnPercent(item.row.id))
                    .map { Format.money(Amount($0), base) } ?? "")
                .gridColumnAlignment(.trailing)
                .help("Expected profit = cost × expected return")
            WeightText(value: weight)
            // Quantity: whole shares, required (> 0) — empty or 0 blocks Commit.
            NumericField(placeholder: "qty", text: Binding(get: { model.planQuantityText[item.row.id] ?? "" },
                                                           set: { model.setPlanQuantity(item.row.id, text: $0) }),
                         isInvalid: (item.row.quantity ?? 0) <= 0)
                .gridColumnAlignment(.trailing)
                .disabled(model.basket.isFrozen)
                .help((item.row.quantity ?? 0) <= 0 ? "Enter a quantity (whole shares, more than 0)" : "")
            Text(item.quote.map { Format.money($0.last, contract.currency) } ?? "waiting…")
                .foregroundStyle(item.quote == nil ? .secondary : .primary)
            Text(item.costBase.map { Format.money(Amount($0), base) } ?? "—")  // planner is base currency (USD) only
                .gridColumnAlignment(.trailing)
        }
        .monospacedDigit()
    }

    private var addTicker: some View {
        HStack {
            TextField("Add ticker", text: $ticker, prompt: Text("e.g. F"))
                .frame(width: 140)
                .onSubmit { Task { await lookUp() } }
            Button("Add") { Task { await lookUp() } }
                .disabled(ticker.trimmingCharacters(in: .whitespaces).isEmpty)
            if !candidates.isEmpty {
                Menu("Pick listing") {
                    ForEach(candidates, id: \.conid) { contract in
                        Button("\(contract.symbol) · \(contract.name) · \(contract.primaryExchange) · \(contract.currency)") {
                            add(contract)
                        }
                    }
                }
                .fixedSize()
            }
            if let lookupMessage { Text(lookupMessage).foregroundStyle(.secondary).font(.callout) }
            Spacer()
            if !model.planRows.isEmpty {
                Button { model.clearPlan() } label: {
                    HStack(spacing: 2) {  // icon and text share the button's colour
                        Text("Clear plan")
                        Image(systemName: "paintbrush")
                            .imageScale(.small)
                            .scaleEffect(x: -1, y: -1)  // flipped horizontally and vertically
                    }
                }
                .buttonStyle(.borderless)
                .help("Remove all planned purchases")
            }
        }
    }

    private func lookUp() async {
        let symbol = ticker.trimmingCharacters(in: .whitespaces).uppercased()
        guard !symbol.isEmpty else { return }
        candidates = []
        lookupMessage = nil
        do {
            let all = try await model.api.contracts(symbol: symbol)
            // The planner works in the account currency (USD) only; other listings are an edge case for the
            // Overview / order ticket.
            let found = all.filter { $0.currency == snapshot.account.baseCurrency }
            if found.isEmpty, let other = all.first {
                lookupMessage = "Planner is \(snapshot.account.baseCurrency) only — \(symbol) trades in \(other.currency)"
                return
            }
            switch found.count {
            case 0: lookupMessage = "No contract found for \(symbol)"
            case 1: add(found[0])
            default:
                candidates = found
                lookupMessage = "Listed on several exchanges"
            }
        } catch {
            lookupMessage = "Lookup failed"
        }
    }

    private func add(_ contract: Contract) {
        model.addPlanRow(contract)
        ticker = ""
        candidates = []
        lookupMessage = nil
    }
}

/// "12.5 %" right-aligned; empty when there is nothing to compare against.
struct WeightText: View {
    let value: Decimal?
    var negativeIsRed = false

    var body: some View {
        Text(value.map { "\($0.formatted(.number.precision(.fractionLength(1)))) %" } ?? "")
            .monospacedDigit()
            .foregroundStyle(negativeIsRed && (value ?? 0) < 0 ? .red : .primary)
            .gridColumnAlignment(.trailing)
    }
}
