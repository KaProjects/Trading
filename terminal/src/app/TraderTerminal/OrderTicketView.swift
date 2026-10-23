import SwiftUI
import TraderCore

/// T5 order ticket sheet. Size warnings appear under the inputs while typing and again in the summary.
struct OrderTicketView: View {
    @Bindable var ticket: OrderTicketModel
    let environment: BrokerEnvironment
    let accountId: String?
    let close: () -> Void
    @FocusState private var symbolFocused: Bool
    @State private var moreOptions = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(title).font(.title2.weight(.semibold))
                Spacer()
                if let accountId { Text(accountId).foregroundStyle(.secondary) }
                EnvironmentBadge(environment: environment)
            }
            switch ticket.step {
            case .editing: editor
            case .reviewing, .sending: summary
            case .sent(let order): result(order)
            case .failed(let message): failure(message)
            }
        }
        .padding(20)
        .frame(width: 560)
        .onAppear { if ticket.contract == nil && !ticket.isModification { symbolFocused = true } }
    }

    private var title: String {
        if let order = ticket.modifying {
            return ticket.step == .editing ? "Modify order #\(order.orderId)" : "Confirm change #\(order.orderId)"
        }
        guard ticket.step == .editing else { return "Confirm order" }
        switch ticket.helper {
        case .buy: return "Buy"
        case .quickSell: return "Quick sell"
        case .none: return "New limit order"
        }
    }

    // MARK: - Editing

    private var editor: some View {
        VStack(alignment: .leading, spacing: 12) {
            Form {
                TextField("Ticker", text: $ticket.symbol, prompt: Text("e.g. AAPL"))
                    .focused($symbolFocused)
                    .disabled(ticket.isModification)
                    .onSubmit { Task { await ticket.lookUp() } }
                if !ticket.candidates.isEmpty {
                    Picker("Listing", selection: Binding<Int?>(
                        get: { ticket.contract?.conid },
                        set: { conid in
                            if let pick = ticket.candidates.first(where: { $0.conid == conid }) {
                                Task { await ticket.select(pick) }
                            }
                        })) {
                        ForEach(ticket.candidates, id: \.conid) { contract in
                            Text("\(contract.symbol) · \(contract.name) · \(contract.primaryExchange) · \(contract.currency)")
                                .tag(Int?.some(contract.conid))
                        }
                    }
                    .pickerStyle(.radioGroup)
                }
                if let contract = ticket.contract {
                    LabeledContent("Contract") {
                        Text("\(contract.name) · \(contract.primaryExchange) · \(contract.currency) · #\(contract.conid)")
                    }
                    if let quote = ticket.quote {
                        LabeledContent("Last price") {
                            Text("\(Format.money(quote.last, contract.currency))  (\(Format.signedPercent(quote.changePct)))")
                                .monospacedDigit()
                        }
                    }
                }
                if ticket.helper == .none {
                    Picker("Side", selection: $ticket.side) {
                        Text("Buy").tag(OrderSide.buy)
                        Text("Sell").tag(OrderSide.sell)
                    }
                    .pickerStyle(.segmented)
                    .disabled(ticket.isModification)
                }
                if ticket.helper == .buy {
                    TextField("Exp. volume", text: $ticket.volumeText, prompt: Text("k"))
                }
                TextField("Quantity", text: $ticket.quantityText, prompt: Text("shares"))
                TextField("Limit price", text: $ticket.limitText,
                          prompt: Text(ticket.contract?.currency ?? "price"))
                if ticket.helper == .none {
                    orderOptions
                } else {
                    DisclosureGroup(isExpanded: $moreOptions) {
                        orderOptions
                    } label: {
                        // The whole row toggles, not only the arrow.
                        Button { withAnimation { moreOptions.toggle() } } label: {
                            Text("More options").foregroundStyle(.secondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .formStyle(.grouped)

            if let message = ticket.lookupMessage {
                Label(message, systemImage: "info.circle").foregroundStyle(.secondary)
            }
            if let preview = ticket.preview {
                PreviewNotes(preview: preview, baseCurrency: nil)
            }
            if let missing = ticket.missing {
                Label(missing, systemImage: "arrow.right.circle").foregroundStyle(.secondary)
            }
            HStack {
                Button("Cancel", role: .cancel, action: close)
                    .keyboardShortcut(.cancelAction)
                Spacer()
                Button("Review order") { ticket.review() }
                    .keyboardShortcut(.defaultAction)
                    .disabled(!ticket.canReview)
            }
        }
    }

    @ViewBuilder
    private var orderOptions: some View {
        Picker("Time in force", selection: $ticket.tif) {
            Text("Day").tag(TimeInForce.day)
            Text("Good till cancelled").tag(TimeInForce.gtc)
        }
        .disabled(ticket.isModification)
        Toggle("Allow outside regular trading hours", isOn: $ticket.outsideRth)
            .disabled(ticket.isModification)
    }

    // MARK: - Summary (confirmation dialog, D5.2 / D7.2)

    private var summary: some View {
        VStack(alignment: .leading, spacing: 14) {
            if environment == .production {
                Text("LIVE ACCOUNT").font(.headline).foregroundStyle(.white)
                    .frame(maxWidth: .infinity).padding(6).background(.red, in: RoundedRectangle(cornerRadius: 6))
            }
            Text(ticket.summary ?? "")
                .font(.title3.weight(.medium))
                .textSelection(.enabled)
                .fixedSize(horizontal: false, vertical: true)
            if let preview = ticket.preview {
                PreviewNotes(preview: preview, baseCurrency: nil)
            }
            HStack {
                Button("Back") { ticket.backToEditing() }
                Spacer()
                Button("Cancel", role: .cancel) {
                    Task { await ticket.cancelledInReview() }
                    close()
                }
                .keyboardShortcut(.cancelAction)
                Button(ticket.isModification ? "Send change" : "Send order") { Task { await ticket.send() } }
                    .keyboardShortcut(.defaultAction)
                    .buttonStyle(.borderedProminent)
                    .tint(ticket.side == .buy ? .green : .red)
                    .disabled(ticket.step == .sending)
            }
            if ticket.step == .sending {
                ProgressView().controlSize(.small)
            }
        }
    }

    private func result(_ order: Order) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Label("Order #\(order.orderId) \(order.status)", systemImage: "checkmark.circle.fill")
                .font(.title3).foregroundStyle(.green)
            Text(ticket.summary ?? "").foregroundStyle(.secondary)
            HStack {
                Spacer()
                Button("Done", action: close).keyboardShortcut(.defaultAction)
            }
        }
    }

    private func failure(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Label(message, systemImage: "xmark.octagon.fill").foregroundStyle(.red)
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Button("Back to order") { ticket.backToEditing() }
                Spacer()
                if ticket.isRetryable {
                    Button("Retry send") { Task { await ticket.send() } }
                }
                Button("Close", action: close).keyboardShortcut(.cancelAction)
            }
        }
    }
}

/// Value, share of account, warnings (never block) and the one hard block (sell > position).
struct PreviewNotes: View {
    let preview: OrderPreview
    let baseCurrency: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text("≈ \(Format.money(preview.value, preview.contract.currency))").monospacedDigit()
                if let pct = preview.pctOfAccount {
                    Text("· \(Format.percent(pct)) of account").foregroundStyle(.secondary)
                }
            }
            .font(.callout)
            if let block = preview.block {
                Label(block.message, systemImage: "nosign")
                    .foregroundStyle(.red).fontWeight(.semibold)
            }
            ForEach(preview.warnings, id: \.self) { warning in
                Label(warning.message, systemImage: warning.level == .info ? "info.circle" : "exclamationmark.triangle.fill")
                    .foregroundStyle(warning.level == .strong ? .red : warning.level == .info ? .secondary : .orange)
            }
        }
    }
}
