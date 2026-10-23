import AppKit
import SwiftUI
import TraderCore

/// T11: the account's transactions between two dates (both included) as CSV for portfolio tracking, one row per
/// order — fetched from IBKR (Flex) on demand. Needs the app unlocked and the Flex query set up in Settings.
struct ExportTransactionsView: View {
    static let id = "export-transactions"

    let model: AppModel
    @State private var start = Calendar.current.date(from: Calendar.current.dateComponents([.year], from: Date()))!
    @State private var end = Date()
    @State private var message: String?
    @State private var exporting = false
    @Environment(\.dismissWindow) private var dismissWindow

    var body: some View {
        Form {
            DatePicker("From", selection: $start, displayedComponents: .date)
            DatePicker("To", selection: $end, in: start..., displayedComponents: .date)
            HStack {
                Text(message ?? (model.locked ? "Unlock the app first."
                                 : exporting ? "Asking IBKR…" : "\(model.environment.label) account, from IBKR"))
                    .foregroundStyle(.secondary)
                Spacer()
                Button("Export…") { Task { await export() } }
                    .keyboardShortcut(.defaultAction)
                    .disabled(model.locked || model.phase != .running || exporting)
            }
        }
        .formStyle(.grouped)
        .frame(width: 380)
    }

    private func export() async {
        exporting = true
        defer { exporting = false }
        let csv: Data
        do {
            csv = try await model.api.transactionsCSV(from: start, to: end)
        } catch ServiceError.http(_, _, let text?) {
            message = text
            return
        } catch {
            message = "Export failed: \(error.localizedDescription)"
            return
        }
        let day = Date.ISO8601FormatStyle(timeZone: .current).year().month().day()
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "transactions-\(model.environment.rawValue)-\(start.formatted(day))-\(end.formatted(day)).csv"
        panel.allowedContentTypes = [.commaSeparatedText]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            try csv.write(to: url, options: .atomic)
            message = nil
            dismissWindow(id: Self.id)  // done: close, and show the file in Finder
            NSWorkspace.shared.activateFileViewerSelecting([url])
        } catch {
            message = "Could not save: \(error.localizedDescription)"
        }
    }
}
