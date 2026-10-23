import SwiftUI
import TraderCore

/// Menu-bar dropdown (D2.1): status line, Log in / Stop, Open window, developer menu (Debug), Quit.
struct MenuContent: View {
    let model: AppModel
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        let shown = model.presentation
        Text(shown.title)
        if let detail = shown.detail {
            Text(detail)
        }
        if let error = model.lastError, !shown.isLocked {
            Text("⚠︎ \(error)")
        }

        Divider()

        if shown.isLocked {
            Button("Unlock with Touch ID") { Task { await model.unlock() } }
        }
        Button(shown.title.hasPrefix("Login failed") ? "Retry Log in" : "Log in") {
            Task { await model.logIn() }
        }
        .disabled(!shown.canLogIn)
        Button("Stop") { Task { await model.stop() } }
            .disabled(!shown.canStop)

        Divider()

        Button("Open Window") {
            openWindow(id: MainWindow.id)
            NSApp.activate()
        }
        Button("Export Transactions…") {
            openWindow(id: ExportTransactionsView.id)
            NSApp.activate()
        }
        if !shown.isLocked {
            Button("Lock") { model.lockNow() }
        }
        Toggle("Start at Login", isOn: Binding(get: { model.startsAtLogin }, set: { model.setStartsAtLogin($0) }))

        if AppEnvironment.isDebugBuild {
            Divider()
            DeveloperMenu(model: model)
        }

        Divider()
        Button("Quit Trader Terminal") { NSApp.terminate(nil) }
    }
}

/// D0.3: failure injection for the fake broker. Debug builds only.
struct DeveloperMenu: View {
    let model: AppModel

    var body: some View {
        Menu("Developer") { DeveloperItems(model: model) }
    }
}

/// Shared by the menu-bar dropdown and the top menu bar.
struct DeveloperItems: View {
    let model: AppModel

    var body: some View {
        Group {
            Section("Environment") {
                ForEach([BrokerEnvironment.fake, .paper], id: \.self) { environment in
                    Button("\(environment == model.environment ? "✓ " : "   ")\(environment.label)") {
                        model.switchEnvironment(environment)
                    }
                    .disabled(!AppEnvironment.available.contains(environment) || environment == model.environment)
                }
            }
            Section("Finnhub") {
                Button(model.finnhubOfflineSimulated ? "✓ Simulate Finnhub Unreachable" : "   Simulate Finnhub Unreachable") {
                    Task { await model.toggleFinnhubOfflineSimulated() }
                }
                .disabled(!model.canSimulateFinnhubOffline)
            }
            Group {
                Section("Prices") {
                    Button(model.priceFeed == "fake" ? "✓ Simulated Prices" : "   Simulated Prices") {
                        model.setPriceFeed("fake")
                    }
                    Button(model.priceFeed == "finnhub" ? "✓ Finnhub Prices (real)" : "   Finnhub Prices (real)"
                           + (model.hasFinnhubKey ? "" : " — add key in Settings")) {
                        model.setPriceFeed("finnhub")
                    }
                    .disabled(!model.hasFinnhubKey && model.priceFeed != "finnhub")
                }
                Section("Planner") {
                    Button(model.basket.allowPlacingWhenClosed ? "✓ Allow Placing When Market Closed"
                                                               : "   Allow Placing When Market Closed") {
                        model.toggleAllowPlacingWhenClosed()
                    }
                }
                Section("Dev data") {
                    ForEach(AppEnvironment.DevData.allCases, id: \.self) { item in
                        Button(model.devData.contains(item) ? "✓ \(item.label)" : "   \(item.label)") {
                            model.toggleDevData(item)
                        }
                    }
                }
                Section("Fake broker") {
                    Button(model.fakeAutoLogin ? "✓ Auto Log-in (no Touch ID)" : "Auto Log-in (no Touch ID)") {
                        model.toggleFakeAutoLogin()
                    }
                    Button(model.autoApprove2FA ? "✓ Auto-approve 2FA (1 s)" : "Auto-approve 2FA (1 s)") {
                        Task { await model.toggleAutoApprove2FA() }
                    }
                    Button("Approve 2FA") { Task { await model.dev(.approve2FA) } }
                    Button("Fail 2FA (timeout)") { Task { await model.dev(.fail2FA) } }
                    Button("Drop Connection") { Task { await model.dev(.dropConnection) } }
                    Button("Require Login (daily restart)") { Task { await model.dev(.requireLogin) } }
                    Button(model.slowResponses ? "✓ Slow Responses (2 s)" : "Slow Responses (2 s)") {
                        Task { await model.toggleSlowResponses() }
                    }
                    Button("Reset Fake Account") { Task { await model.dev(.reset) } }
                }
            }
            .disabled(model.phase != .running || model.environment != .fake)  // the rest is for the fake broker
        }
    }
}

/// Top menu bar: Session menu (and Developer in Debug builds) next to the standard app/Edit/Window menus.
struct AppCommands: Commands {
    let model: AppModel
    @Environment(\.openWindow) private var openWindow

    var body: some Commands {
        CommandGroup(replacing: .newItem) {
            Button("New Order…") { model.openTicket() }
                .keyboardShortcut("n")
                .disabled(!model.canTrade)
        }
        CommandGroup(replacing: .importExport) {
            Button("Export Transactions…") { openWindow(id: ExportTransactionsView.id) }
                .keyboardShortcut("e", modifiers: [.command, .shift])
        }
        CommandMenu("Session") {
            let shown = model.presentation
            Button(shown.title.hasPrefix("Login failed") ? "Retry Log in" : "Log in") {
                Task { await model.logIn() }
            }
            .keyboardShortcut("l", modifiers: [.command, .shift])
            .disabled(!shown.canLogIn)
            Button("Stop") { Task { await model.stop() } }
                .disabled(!shown.canStop)
            Divider()
            if shown.isLocked {
                Button("Unlock with Touch ID") { Task { await model.unlock() } }
            } else {
                Button("Lock") { model.lockNow() }
                    .keyboardShortcut("l")
            }
        }
        #if DEBUG
        CommandMenu("Developer") { DeveloperItems(model: model) }
        #endif
    }
}
