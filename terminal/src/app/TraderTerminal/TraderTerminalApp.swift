import AppKit
import SwiftUI
import TraderCore

@main
struct TraderTerminalApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate

    var body: some Scene {
        // First scene: opens at launch, and again when the app is opened while already running.
        Window("Trader Terminal", id: MainWindow.id) {
            MainWindow(model: delegate.model)
        }
        .defaultSize(width: 720, height: 460)
        .commands { AppCommands(model: delegate.model) }

        Settings {
            SettingsView(model: delegate.model)
        }

        Window("Export Transactions", id: ExportTransactionsView.id) {
            ExportTransactionsView(model: delegate.model)
        }
        .windowResizability(.contentSize)

        MenuBarExtra {
            MenuContent(model: delegate.model)
        } label: {
            Image(nsImage: StatusIcon.image(for: delegate.model.presentation))
        }
    }
}

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    let model = AppModel()

    /// No macOS window state restoration: it restored the main window to a stale monitor. The window frame is
    /// remembered by `rememberWindowFrame` instead.
    func applicationWillFinishLaunching(_ notification: Notification) {
        UserDefaults.standard.set(false, forKey: "NSQuitAlwaysKeepsWindows")
    }

    func applicationShouldSaveApplicationState(_ app: NSApplication) -> Bool { false }
    func applicationShouldRestoreApplicationState(_ app: NSApplication) -> Bool { false }

    func applicationDidFinishLaunching(_ notification: Notification) {
        signal(SIGPIPE, SIG_IGN)  // a write to a dead service's stdin must fail, not kill the app
        model.start()
        NSApp.activate()
    }

    /// `open` on the running app (Finder, Spotlight, Terminal) brings the window back.
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows: Bool) -> Bool {
        NSApp.activate()
        return true
    }

    func applicationWillTerminate(_ notification: Notification) {
        model.shutdown()
    }
}

enum StatusIcon {
    static func image(for presentation: StatusPresentation) -> NSImage {
        let symbol = presentation.isLocked ? "lock.fill" : "chart.line.uptrend.xyaxis.circle.fill"
        let base = NSImage(systemSymbolName: symbol, accessibilityDescription: presentation.title) ?? NSImage()
        guard presentation.indicator != .grey else {
            base.isTemplate = true  // follows the menu bar's light/dark appearance
            return base
        }
        let config = NSImage.SymbolConfiguration(pointSize: 15, weight: .regular)
            .applying(.init(paletteColors: [color(presentation.indicator)]))
        let image = base.withSymbolConfiguration(config) ?? base
        image.isTemplate = false
        return image
    }

    static func color(_ indicator: Indicator) -> NSColor {
        switch indicator {
        case .grey: .secondaryLabelColor
        case .red: .systemRed
        case .yellow: .systemYellow
        case .green: .systemGreen
        }
    }
}
