import SwiftUI
import TraderCore

/// Main window: one header row (session on the left, Overview/Planner in the middle, lock on the right) above the
/// account content.
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
                    if model.mainSection == .planner {
                        PlannerView(model: model, snapshot: account, isLive: model.accountIsLive)
                    } else if model.mainSection == .execution {
                        ExecutionView(model: model, snapshot: account, isLive: model.accountIsLive)
                    } else if model.mainSection == .history {
                        HistoryView(model: model, snapshot: account, isLive: model.accountIsLive)
                    } else {
                        AccountView(snapshot: account, isLive: model.accountIsLive, quotes: model.quotes,
                                    trade: model.canTrade ? { model.openTicket(contract: $0, side: $1) } : nil,
                                    quickSell: model.canTrade ? { model.openQuickSell($0) } : nil,
                                    newOrder: model.canTrade ? { model.openTicket() } : nil,
                                    buy: model.canTrade ? { model.openBuy() } : nil,
                                    orderActions: model.canTrade ? OrderActions(
                                        modify: { model.openModify($0) },
                                        cancel: { order in Task { await model.cancel(order) } },
                                        cancelAll: { Task { await model.cancelAll() } }) : nil,
                                    tab: Binding(get: { model.overviewTab }, set: { model.overviewTab = $0 }))
                    }
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
        .rememberWindowFrame("TraderTerminal.main", sceneId: MainWindow.id)
        .onDisappear { model.windowClosed() }
        .sheet(item: Binding(get: { model.ticket.map(TicketBox.init) }, set: { if $0 == nil { model.closeTicket() } })) {
            OrderTicketView(ticket: $0.ticket, environment: model.environment,
                            accountId: model.status?.accountId, close: model.closeTicket)
        }
    }

    private func header(_ shown: StatusPresentation) -> some View {
        // Left and right take equal flexible width, so the tabs stay centred in the window.
        HStack(spacing: 12) {
            SessionControl(model: model, shown: shown)
                .frame(maxWidth: .infinity, alignment: .leading)

            if !shown.isLocked, model.account != nil {
                Picker("", selection: Binding(get: { model.mainSection }, set: { model.mainSection = $0 })) {
                    Text("Overview").tag(MainSection.overview)
                    Text("Planner").tag(MainSection.planner)
                    Text("Execution").tag(MainSection.execution)
                    Text("History").tag(MainSection.history)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .fixedSize()
            }

            HStack {
                if !shown.isLocked {
                    Button { model.lockNow() } label: { Image(systemName: "lock") }
                        .help("Lock now")
                }
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

/// Header left side: power button (log in / disconnect) + status pill (dot, status, environment badge).
struct SessionControl: View {
    let model: AppModel
    let shown: StatusPresentation

    var body: some View {
        HStack(spacing: 8) {
            if !shown.isLocked {
                powerButton
            }
            HStack(spacing: 8) {
                Circle()
                    .fill(Color(nsColor: StatusIcon.color(shown.indicator)))
                    .frame(width: 8, height: 8)
                Text(pillText).font(.callout.weight(.medium)).lineLimit(1)
                EnvironmentBadge(environment: model.environment)
            }
            .padding(.leading, 10)
            .padding(.trailing, 4)
            .padding(.vertical, 4)
            .background(.quaternary.opacity(0.6), in: Capsule())
            .help(shown.detail ?? shown.title)
            if !shown.isLocked, let error = model.lastError {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
                    .help(error)
            }
        }
    }

    /// Green = connected (click disconnects); grey = disconnected (click logs in); spinner while logging in.
    @ViewBuilder private var powerButton: some View {
        let busy = model.status?.state == .loggingIn || model.status?.state == .reconnecting
        if busy {
            Button { Task { await model.stop() } } label: {
                ProgressView().controlSize(.small).frame(width: 18, height: 18)
            }
            .buttonStyle(.borderless)
            .help("Logging in… click to cancel")
        } else if shown.canStop {
            Button { Task { await model.stop() } } label: {
                Image(systemName: "power").font(.system(size: 15, weight: .semibold)).foregroundStyle(.green)
            }
            .buttonStyle(.borderless)
            .help("Disconnect from the broker session")
        } else {
            Button { Task { await model.logIn() } } label: {
                Image(systemName: "power").font(.system(size: 15, weight: .semibold)).foregroundStyle(.secondary)
            }
            .buttonStyle(.borderless)
            .disabled(!shown.canLogIn)
            .help("Log in")
        }
    }

    /// "Connected · FAKE0001" (the environment is the badge next to it), else the plain status.
    private var pillText: String {
        if model.status?.state == .connected, !shown.isLocked, let account = model.status?.accountId {
            return "Connected · \(account)"
        }
        return shown.title
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
