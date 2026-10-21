import SwiftUI
import TraderCore

/// T3 main window content: funds, positions, open orders, fills (last 7 days). Read-only.
struct AccountView: View {
    let snapshot: AccountSnapshot
    let isLive: Bool
    @State private var tab: Tab = .positions

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
            Picker("", selection: $tab) {
                Text("Positions (\(snapshot.positions.count))").tag(Tab.positions)
                Text("Open orders (\(snapshot.orders.count))").tag(Tab.orders)
                Text("Fills · 7 days (\(snapshot.fills.count))").tag(Tab.fills)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            switch tab {
            case .positions: PositionsTable(positions: snapshot.positions)
            case .orders: OrdersTable(orders: snapshot.orders)
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

    var body: some View {
        Table(positions) {
            TableColumn("Symbol") { Text($0.contract.symbol).fontWeight(.semibold) }
                .width(min: 60, ideal: 70)
            TableColumn("Name") { Text($0.contract.name) }
            TableColumn("Exchange") { Text($0.contract.primaryExchange) }
                .width(min: 60, ideal: 70)
            TableColumn("Quantity") { Text(Format.quantity($0.quantity)).monospacedDigit() }
                .alignment(.trailing)
            TableColumn("Avg cost") { Text(Format.money($0.avgCost, $0.contract.currency)).monospacedDigit() }
                .alignment(.trailing)
            TableColumn("Cost basis") { Text(Format.money($0.costBasis, $0.contract.currency)).monospacedDigit() }
                .alignment(.trailing)
        }
    }
}

struct OrdersTable: View {
    let orders: [Order]

    var body: some View {
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
            TableColumn("Status") { Text($0.status) }
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
}
