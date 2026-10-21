import SwiftUI
import TraderCore

/// T3 main window content: funds, positions, open orders, fills (last 7 days). Read-only.
struct AccountView: View {
    let snapshot: AccountSnapshot
    let isLive: Bool
    var quotes: [String: Quote] = [:]
    /// Opens the order ticket pre-filled from a position row (T5). Nil while not connected.
    var trade: ((Contract, OrderSide) -> Void)?
    /// Opens an empty order ticket (T5). Nil while not connected.
    var newOrder: (() -> Void)?
    /// T6 actions on open orders. Nil while not connected.
    var orderActions: OrderActions?
    @State private var tab: Tab = .positions
    @State private var confirmCancelAll = false

    enum Tab: Hashable { case positions, orders, fills }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !isLive {
                Label("Not connected — saved data as of \(snapshot.asOf.formatted(date: .abbreviated, time: .shortened))",
                      systemImage: "clock.arrow.circlepath")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            FundsView(account: snapshot.account)
            HStack {
                Picker("", selection: $tab) {
                    Text("Positions (\(snapshot.positions.count))").tag(Tab.positions)
                    Text("Open orders (\(snapshot.orders.count))").tag(Tab.orders)
                    Text("Fills · 7 days (\(snapshot.fills.count))").tag(Tab.fills)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .fixedSize()
                Spacer()
                if let orderActions, tab == .orders, !snapshot.orders.isEmpty {
                    Button("Cancel All…", role: .destructive) { confirmCancelAll = true }
                        .confirmationDialog("Cancel all \(snapshot.orders.count) open orders?",
                                            isPresented: $confirmCancelAll) {
                            Button("Cancel All Orders", role: .destructive) { orderActions.cancelAll() }
                            Button("Keep Orders", role: .cancel) {}
                        }
                }
                if let newOrder {
                    Button("New Order", systemImage: "plus", action: newOrder)
                        .buttonStyle(.borderedProminent)
                        .help("New limit order (⌘N)")
                }
            }
            switch tab {
            case .positions: PositionsTable(positions: snapshot.positions, quotes: quotes, trade: trade)
            case .orders: OrdersTable(orders: snapshot.orders, actions: orderActions)
            case .fills: FillsTable(fills: snapshot.fills)
            }
        }
        .padding(16)
        .opacity(isLive ? 1 : 0.55)
    }
}

struct FundsView: View {
    let account: AccountSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                tile("Net liquidation", account.netLiquidation)
                tile("Available funds", account.availableFunds)
                tile("Buying power", account.buyingPower)
                tile("Total cash", account.totalCash)
            }
            HStack(spacing: 16) {
                Text("Cash").foregroundStyle(.secondary)
                ForEach(account.cash, id: \.currency) { balance in
                    Text(Format.money(balance.amount, balance.currency)).monospacedDigit()
                }
                Spacer()
                Text(account.accountId).foregroundStyle(.secondary).font(.caption)
            }
            .font(.callout)
        }
    }

    private func tile(_ title: String, _ amount: Amount) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(Format.money(amount, account.baseCurrency)).font(.title3.weight(.semibold)).monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 8))
    }
}

struct PositionsTable: View {
    let positions: [Position]
    var quotes: [String: Quote] = [:]
    var trade: ((Contract, OrderSide) -> Void)?

    var body: some View {
        // Re-evaluate staleness every few seconds even when no price arrives.
        TimelineView(.periodic(from: .now, by: 5)) { context in
            table(now: context.date)
        }
    }

    private func table(now: Date) -> some View {
        let rows = positions.map { PricedPosition(position: $0, quote: quotes[$0.contract.feedSymbol], now: now) }
        return Table(rows, columns: {
            TableColumn("Symbol") { (row: PricedPosition) in Text(row.position.contract.symbol).fontWeight(.semibold) }
                .width(min: 60, ideal: 70)
            TableColumn("Name") { (row: PricedPosition) in Text(row.position.contract.name) }
            TableColumn("Quantity") { (row: PricedPosition) in
                Text(Format.quantity(row.position.quantity)).monospacedDigit()
            }
            .alignment(.trailing)
            TableColumn("Avg cost") { (row: PricedPosition) in
                Text(Format.money(row.position.avgCost, row.position.contract.currency)).monospacedDigit()
            }
            .alignment(.trailing)
            TableColumn("Last") { (row: PricedPosition) in
                Text(row.quote.map { Format.money($0.last, row.position.contract.currency) } ?? "—")
                    .monospacedDigit()
                    .foregroundStyle(row.isStale ? .secondary : .primary)
                    .help(row.isStale ? "No price update for over a minute" : "")
            }
            .alignment(.trailing)
            TableColumn("Day") { (row: PricedPosition) in
                Text(row.quote.map { Format.signedPercent($0.changePct) } ?? "—")
                    .monospacedDigit()
                    .foregroundStyle(Format.color(row.quote?.changePct))
            }
            .alignment(.trailing)
            TableColumn("Market value") { (row: PricedPosition) in
                Text(row.marketValue.map { Format.money($0, row.position.contract.currency) } ?? "—").monospacedDigit()
            }
            .alignment(.trailing)
            TableColumn("Unrealized P&L") { (row: PricedPosition) in
                Text(row.unrealizedPnL.map { pnl in
                    "\(Format.signedMoney(pnl, row.position.contract.currency))"
                        + (row.unrealizedPct.map { "  \(Format.signedPercent($0))" } ?? "")
                } ?? "—")
                .monospacedDigit()
                .foregroundStyle(Format.color(row.unrealizedPnL))
            }
            .width(min: 190, ideal: 220)
            .alignment(.trailing)
        })
        .contextMenu(forSelectionType: Int.self) { ids in
            if let trade, let id = ids.first, let position = positions.first(where: { $0.id == id }) {
                Button("Buy \(position.contract.symbol)…") { trade(position.contract, .buy) }
                Button("Sell \(position.contract.symbol)…") { trade(position.contract, .sell) }
            }
        }
    }
}

struct OrderActions {
    let modify: (Order) -> Void
    let cancel: (Order) -> Void
    let cancelAll: () -> Void
}

struct OrdersTable: View {
    let orders: [Order]
    var actions: OrderActions?
    @State private var toCancel: Order?

    var body: some View {
        table
            .contextMenu(forSelectionType: Order.ID.self) { ids in
                if let actions, let id = ids.first, let order = orders.first(where: { $0.id == id }) {
                    Button("Modify Order #\(order.orderId)…") { actions.modify(order) }
                    Button("Cancel Order #\(order.orderId)…", role: .destructive) { toCancel = order }
                }
            } primaryAction: { ids in
                if let actions, let id = ids.first, let order = orders.first(where: { $0.id == id }) {
                    actions.modify(order)  // double-click
                }
            }
            .confirmationDialog(toCancel.map { "Cancel order #\($0.orderId)?" } ?? "",
                                isPresented: Binding(get: { toCancel != nil }, set: { if !$0 { toCancel = nil } }),
                                presenting: toCancel) { order in
                Button("Cancel Order", role: .destructive) { actions?.cancel(order) }
                Button("Keep Order", role: .cancel) {}
            } message: { order in
                Text("\(order.side.rawValue) \(Format.quantity(order.remaining)) \(order.contract.symbol) "
                     + "LMT \(Format.money(order.limitPrice, order.contract.currency)) — remaining quantity")
            }
    }

    private var table: some View {
        Table(orders) {
            TableColumn("Symbol") { Text($0.contract.symbol).fontWeight(.semibold) }
                .width(min: 60, ideal: 70)
            TableColumn("Side") { SideText(side: $0.side) }
                .width(min: 40, ideal: 50)
            TableColumn("Quantity") { Text(Format.quantity($0.quantity)).monospacedDigit() }
                .alignment(.trailing)
            TableColumn("Limit") { Text(Format.money($0.limitPrice, $0.contract.currency)).monospacedDigit() }
                .alignment(.trailing)
            TableColumn("Filled / remaining") {
                Text("\(Format.quantity($0.filled)) / \(Format.quantity($0.remaining))").monospacedDigit()
            }
            .alignment(.trailing)
            TableColumn("Status") { Text($0.statusText).foregroundStyle($0.isPartiallyFilled ? .orange : .primary) }
            TableColumn("TIF") { Text($0.tif + ($0.outsideRth ? " · ext" : "")) }
                .width(min: 40, ideal: 60)
        }
    }
}

struct FillsTable: View {
    let fills: [Fill]

    var body: some View {
        Table(fills) {
            TableColumn("Time") { Text($0.time.formatted(date: .abbreviated, time: .shortened)) }
                .width(min: 110, ideal: 140)
            TableColumn("Symbol") { Text($0.contract.symbol).fontWeight(.semibold) }
                .width(min: 60, ideal: 70)
            TableColumn("Side") { SideText(side: $0.side) }
                .width(min: 40, ideal: 50)
            TableColumn("Quantity") { Text(Format.quantity($0.quantity)).monospacedDigit() }
                .alignment(.trailing)
            TableColumn("Price") { Text(Format.money($0.price, $0.contract.currency)).monospacedDigit() }
                .alignment(.trailing)
            TableColumn("Commission") { Text(Format.money($0.commission, $0.commissionCurrency)).monospacedDigit() }
                .alignment(.trailing)
        }
    }
}

struct SideText: View {
    let side: OrderSide

    var body: some View {
        Text(side.rawValue)
            .fontWeight(.semibold)
            .foregroundStyle(side == .buy ? .green : .red)
    }
}

enum Format {
    static func money(_ amount: Amount, _ currency: String) -> String {
        amount.value.formatted(.currency(code: currency).precision(.fractionLength(2...4)))
    }

    static func quantity(_ amount: Amount) -> String {
        amount.value.formatted(.number.precision(.fractionLength(0...4)))
    }

    static func percent(_ amount: Amount) -> String {
        "\(amount.value.formatted(.number.precision(.fractionLength(0...1)))) %"
    }

    static func signedMoney(_ amount: Amount, _ currency: String) -> String {
        (amount.value >= 0 ? "+" : "") + money(amount, currency)
    }

    static func color(_ amount: Amount?) -> Color {
        guard let value = amount?.value, value != 0 else { return .secondary }
        return value > 0 ? .green : .red
    }

    static func signedPercent(_ amount: Amount) -> String {
        "\(amount.value.formatted(.number.precision(.fractionLength(2)).sign(strategy: .always()))) %"
    }
}
