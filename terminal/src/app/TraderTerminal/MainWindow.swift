import SwiftUI
import TraderCore

/// Main window. T2: status shell only; account data arrives with T3.
struct MainWindow: View {
    static let id = "main"
    let model: AppModel

    var body: some View {
        let shown = model.presentation
        VStack(spacing: 0) {
            header(shown)
            Divider()
            Group {
                if shown.isLocked {
                    LockedView(model: model)
                } else if let account = model.account {
                    AccountView(snapshot: account, isLive: model.accountIsLive, quotes: model.quotes,
                                trade: model.canTrade ? { model.openTicket(contract: $0, side: $1) } : nil,
                                newOrder: model.canTrade ? { model.openTicket() } : nil,
                                orderActions: model.canTrade ? OrderActions(
                                    modify: { model.openModify($0) },
                                    cancel: { order in Task { await model.cancel(order) } },
                                    cancelAll: { Task { await model.cancelAll() } }) : nil)
                } else {
                    ContentUnavailableView {
                        Label(shown.title, systemImage: "chart.line.uptrend.xyaxis")
                    } description: {
                        Text(shown.detail ?? (shown.canLogIn ? "Log in to see your account." : ""))
                    } actions: {
                        if shown.canLogIn {
                            Button("Log in") { Task { await model.logIn() } }
                                .buttonStyle(.borderedProminent)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(minWidth: 520, minHeight: 320)
        .onDisappear { model.windowClosed() }
        .sheet(item: Binding(get: { model.ticket.map(TicketBox.init) }, set: { if $0 == nil { model.closeTicket() } })) {
            OrderTicketView(ticket: $0.ticket, environment: model.environment,
                            accountId: model.status?.accountId, close: model.closeTicket)
        }
    }

    private func header(_ shown: StatusPresentation) -> some View {
        HStack(spacing: 10) {
            Circle()
                .fill(Color(nsColor: StatusIcon.color(shown.indicator)))
                .frame(width: 10, height: 10)
            Text(shown.title).font(.headline)
            Spacer()
            if let error = model.lastError, !shown.isLocked {
                Text(error).foregroundStyle(.red).font(.callout).lineLimit(1)
            }
            EnvironmentBadge(environment: model.environment)
            if !shown.isLocked {
                if shown.canLogIn {
                    Button("Log in") { Task { await model.logIn() } }
                }
                if shown.canStop {
                    Button("Stop") { Task { await model.stop() } }
                }
                Button { model.lockNow() } label: { Image(systemName: "lock") }
                    .help("Lock now")
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }
}

struct EnvironmentBadge: View {
    let environment: BrokerEnvironment

    var body: some View {
        Text(environment.badge)
            .font(.caption.weight(.bold))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .foregroundStyle(.white)
            .background(color, in: Capsule())
    }

    private var color: Color {
        switch environment {
        case .fake: .purple
        case .paper: .blue
        case .production: .red
        }
    }
}

struct LockedView: View {
    let model: AppModel

    var body: some View {
        ContentUnavailableView {
            Label("Locked", systemImage: "lock.fill")
        } description: {
            Text("Unlock with Touch ID to see your account.")
        } actions: {
            Button("Unlock") { Task { await model.unlock() } }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut(.defaultAction)
        }
    }
}

/// `sheet(item:)` needs Identifiable; one ticket at a time.
private struct TicketBox: Identifiable {
    let ticket: OrderTicketModel
    var id: ObjectIdentifier { ObjectIdentifier(ticket) }
}
