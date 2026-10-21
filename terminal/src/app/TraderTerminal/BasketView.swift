import SwiftUI
import TraderCore

/// T9: review of the buy orders generated from a committed plan. Place sends them all; no extra confirmation.
struct BasketView: View {
    let model: AppModel
    let account: AccountSummary
    let available: Decimal

    private var basket: PlanBasketModel { model.basket }

    var body: some View {
        // Re-check the market clock on its own: Place enables at the open and disables at the close.
        TimelineView(.periodic(from: .now, by: 10)) { context in
            content(now: context.date)
        }
    }

    @ViewBuilder
    private func content(now: Date) -> some View {
        let base = account.baseCurrency
        let reason = basket.blockingReason(account: account, available: available, now: now)
        VStack(alignment: .leading, spacing: 10) {
            Divider()
            HStack {
                Text("Buy orders").font(.title3.weight(.semibold))
                Text("DAY · regular hours").font(.caption).foregroundStyle(.secondary)
                Spacer()
                marketClock(now: now)
            }
            Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 6) {
                GridRow {
                    ForEach(["", "Quantity", "Limit", "Price", "Value", " "], id: \.self) {
                        Text($0).font(.caption).foregroundStyle(.secondary)
                    }
                }
                ForEach(basket.lines) { line in
                    row(line, base: base)
                    if let notes = notes(line) {
                        GridRow {
                            Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
                            notes.gridCellColumns(5)
                        }
                    }
                }
            }
            HStack(alignment: .firstTextBaseline) {
                Spacer()
                actions(reason: reason)
            }
            .padding(.top, 6)
        }
    }

    // MARK: - Pieces

    /// Open: current time (Mac time zone). Closed: just "Market closed" with the moon.
    private func marketClock(now: Date) -> some View {
        Group {
            if USMarket.isRegularSession(now) {
                Text("\(now.formatted(date: .omitted, time: .shortened)) \(LocalTime.offsetLabel(for: now))")
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            } else {
                Label("Market closed", systemImage: "moon.zzz.fill")
                    .foregroundStyle(.orange)
                    .help("US regular session opens \(LocalTime.weekdayTime(USMarket.nextOpen(after: now)))")
            }
        }
        .font(.callout)
    }

    /// Limit vs. live price in percent (negative = the price moved above the limit: the buy may not fill).
    private func headroom(_ line: PlanBasketModel.Line) -> Decimal? {
        PlanCommit.limitHeadroom(limit: line.limit, last: model.quotes[line.contract.feedSymbol]?.last.value)
    }

    /// Only rows still to be sent are flagged; a placed order is already at the broker.
    private func priceAboveLimit(_ line: PlanBasketModel.Line) -> Bool {
        !line.isPlaced && (headroom(line) ?? 0) < 0
    }

    private func row(_ line: PlanBasketModel.Line, base: String) -> some View {
        let contract = line.contract
        let room = headroom(line)
        let value = line.quantity.flatMap { q in line.limit.map { q * $0 } }
        let editable = line.isEditable && (basket.stage == .committed || basket.stage == .placed)
        // First-text-baseline: the price lines up with the other numbers; its note hangs below.
        return GridRow(alignment: .firstTextBaseline) {
            HStack(spacing: 6) {
                Text("BUY").font(.caption.weight(.bold)).foregroundStyle(.green)
                Text(contract.symbol).fontWeight(.semibold)
            }
            NumericField(placeholder: "qty", text: Binding(get: { line.quantityText }, set: { basket.setQuantity(line.id, $0) }),
                         isInvalid: line.quantity == nil && !line.isPlaced)
                .disabled(!editable)
            NumericField(placeholder: "limit", text: Binding(get: { line.limitText }, set: { basket.setLimit(line.id, $0) }),
                         allowsDecimal: true, isInvalid: line.limit == nil && !line.isPlaced, width: 90)
                .disabled(!editable)
            VStack(alignment: .trailing, spacing: 0) {
                Text(model.quotes[contract.feedSymbol].map { Format.money($0.last, contract.currency) } ?? "—")
                if let room {
                    Text("\(Format.percent(Amount(abs(room)))) \(room < 0 ? "above" : "below") limit")
                        .font(.caption2)
                        .foregroundStyle(room < 0 ? .orange : .secondary)
                        .help("Live price vs. this order's limit — follows the price until the order is placed")
                }
            }
            .gridColumnAlignment(.trailing)
            // Planner orders are in the account currency (USD) only: one value column.
            Text(value.map { Format.money(Amount($0), contract.currency) } ?? "—").gridColumnAlignment(.trailing)
            HStack(spacing: 6) {
                status(line)
            }
        }
        .monospacedDigit()
        .background(priceAboveLimit(line) ? Color.orange.opacity(0.14) : .clear)
    }

    @ViewBuilder
    private func status(_ line: PlanBasketModel.Line) -> some View {
        switch line.result {
        case .placed(let orderId, let status):
            // Follows live order updates: Sending… → Working → Filled (or Cancelled / Rejected).
            Label("#\(orderId) \(Order.statusLabel(status))", systemImage: statusIcon(status))
                .foregroundStyle(statusColor(status))
                .font(.callout)
                .help("Order #\(orderId) — broker status \(status)")
        case .failed:
            Image(systemName: "xmark.octagon.fill").foregroundStyle(.red)
        case nil:
            EmptyView()
        }
    }

    private func statusIcon(_ status: String) -> String {
        switch Order.statusLabel(status) {
        case "Filled": "checkmark.circle.fill"
        case "Working": "clock"
        case "Sending…": "paperplane"
        case "Cancelled": "minus.circle"
        default: "xmark.octagon.fill"
        }
    }

    private func statusColor(_ status: String) -> Color {
        switch Order.statusLabel(status) {
        case "Filled": .green
        case "Working", "Sending…": .blue
        case "Cancelled": .secondary
        default: .red
        }
    }

    /// Block (red), failure (red), warnings (orange / red) and info (grey) under the row.
    private func notes(_ line: PlanBasketModel.Line) -> AnyView? {
        var items: [(String, Color)] = []
        if case .failed(let reason) = line.result { items.append((reason, .red)) }
        if !line.isPlaced {
            if let block = line.preview?.block { items.append((block.message, .red)) }
            // Live: follows the price shown right above (the service's check only knows the price at its time).
            if let room = headroom(line), abs(room) > basket.deviationWarn {
                items.append(("Limit price is \(room > 0 ? "+" : "−")\(Format.percent(Amount(abs(room)))) from the price",
                              .orange))
            }
            // "Market closed" is shown once above; the deviation warning is computed live (above).
            for warning in line.preview?.warnings ?? []
            where warning.code != "market_closed" && warning.code != "price_deviation" {
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
        case .committed:
            VStack(alignment: .trailing, spacing: 4) {
                Button("Place \(basket.lines.count) buy orders") {
                    Task { await model.placeBasket() }
                }
                .buttonStyle(.borderedProminent)
                .tint(.green)
                .disabled(reason != nil)
                if let reason { Text(reason).font(.caption).foregroundStyle(.secondary) }
            }
        case .placing:
            HStack(spacing: 6) {
                ProgressView().controlSize(.small)
                Text("Placing orders…")
            }
        case .placed:
            HStack(spacing: 10) {
                if basket.failedCount > 0 {
                    Button("Retry \(basket.failedCount) failed") { Task { await model.placeBasket() } }
                        .disabled(reason != nil)
                        .help(reason ?? "Resend only the failed orders (same references, never duplicated)")
                }
                if basket.allPlaced {
                    Label("All orders placed", systemImage: "checkmark.seal.fill").foregroundStyle(.green)
                    Button("Reset") { model.resetPlan() }
                        .buttonStyle(.borderedProminent)
                        .help("Clear the plan and start a new one")
                }
            }
        case .planning:
            EmptyView()
        }
    }
}
