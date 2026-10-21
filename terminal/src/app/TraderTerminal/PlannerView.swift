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
                                       orders: snapshot.orders, quotes: model.quotes, rows: model.planRows)
        let base = plan.baseCurrency
        ScrollView {
            Grid(alignment: .leading, horizontalSpacing: 16, verticalSpacing: 6) {
                totalRow("Account value", "if everything were sold", plan.accountValue, base, emphasis: .large)

                sectionHeader("Holdings", columns: ["Quantity", "Last", "Market value", base])
                ForEach(plan.holdings) { holding in
                    let currency = holding.position.contract.currency
                    GridRow {
                        Text(holding.position.contract.symbol).fontWeight(.semibold)
                        Text(Format.quantity(holding.position.quantity)).gridColumnAlignment(.trailing)
                        Text(holding.quote.map { Format.money($0.last, currency) } ?? "—").gridColumnAlignment(.trailing)
                        Text((holding.isApproximate ? "≈ " : "") + Format.money(Amount(holding.marketValue), currency))
                            .gridColumnAlignment(.trailing)
                            .help(holding.isApproximate ? "No live price yet: cost basis used" : "")
                        Text(Format.money(Amount(holding.marketValueBase), base)).gridColumnAlignment(.trailing)
                    }
                    .monospacedDigit()
                }
                totalRow("Holdings", nil, plan.holdingsTotal, base, sign: "−")
                Divider()
                totalRow("Cash", "account value − holdings", plan.cash, base, sign: "=")
                totalRow("Reserved by open buy orders", nil, plan.reservedByOpenBuys, base, sign: "−")
                totalRow("Available to invest", nil, plan.available, base, sign: "=", emphasis: .bold)

                sectionHeader("Planned purchases", columns: ["Quantity", "Last", "Cost", base])
                ForEach(plan.planned) { item in
                    plannedRow(item, base: base)
                }
                GridRow {
                    addTicker.gridCellColumns(5)
                }
                totalRow("Planned", nil, plan.plannedTotal, base, sign: "−")
                Divider()
                totalRow("Left after planned purchases", nil, plan.remaining, base, sign: "=", emphasis: .large,
                         negativeIsRed: true)
            }
            .padding(20)
        }
        .opacity(isLive ? 1 : 0.55)
    }

    // MARK: - Rows

    private enum Emphasis { case normal, bold, large }

    private func totalRow(_ title: String, _ hint: String?, _ value: Decimal, _ base: String, sign: String = "",
                          emphasis: Emphasis = .normal, negativeIsRed: Bool = false) -> some View {
        GridRow {
            HStack(spacing: 6) {
                Text(sign).foregroundStyle(.secondary).frame(width: 12)
                Text(title)
                if let hint { Text(hint).foregroundStyle(.secondary).font(.caption) }
            }
            .gridCellColumns(4)
            Text(Format.money(Amount(value), base))
                .monospacedDigit()
                .foregroundStyle(negativeIsRed && value < 0 ? .red : .primary)
                .gridColumnAlignment(.trailing)
        }
        .font(emphasis == .large ? .title3.weight(.semibold) : emphasis == .bold ? .body.weight(.semibold) : .body)
    }

    private func sectionHeader(_ title: String, columns: [String]) -> some View {
        GridRow {
            Text(title).font(.headline)
            ForEach(columns, id: \.self) { Text($0).font(.caption).foregroundStyle(.secondary) }
        }
        .padding(.top, 12)
    }

    private func plannedRow(_ item: PlannerSummary.Planned, base: String) -> some View {
        let contract = item.row.contract
        return GridRow {
            HStack(spacing: 6) {
                Button { model.removePlanRow(item.row.id) } label: { Image(systemName: "minus.circle") }
                    .buttonStyle(.borderless)
                    .help("Remove")
                VStack(alignment: .leading, spacing: 0) {
                    Text(contract.symbol).fontWeight(.semibold)
                    Text("\(contract.name) · \(contract.primaryExchange)").font(.caption).foregroundStyle(.secondary)
                }
            }
            TextField("qty", text: Binding(get: { model.planQuantityText[item.row.id] ?? "" },
                                           set: { model.setPlanQuantity(item.row.id, text: $0) }))
                .multilineTextAlignment(.trailing)
                .frame(width: 90)
                .gridColumnAlignment(.trailing)
            Text(item.quote.map { Format.money($0.last, contract.currency) } ?? "waiting…")
                .foregroundStyle(item.quote == nil ? .secondary : .primary)
            Text(item.cost.map { Format.money(Amount($0), contract.currency) } ?? "—")
            Text(item.costBase.map { Format.money(Amount($0), base) } ?? "—")
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
                Button("Clear plan") { model.clearPlan() }.buttonStyle(.borderless)
            }
        }
    }

    private func lookUp() async {
        let symbol = ticker.trimmingCharacters(in: .whitespaces).uppercased()
        guard !symbol.isEmpty else { return }
        candidates = []
        lookupMessage = nil
        do {
            let found = try await model.api.contracts(symbol: symbol)
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
