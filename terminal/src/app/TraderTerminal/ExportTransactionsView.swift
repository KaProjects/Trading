import AppKit
import SwiftUI
import TraderCore

/// T11: every transaction between two dates (both included) as CSV for portfolio tracking, one row per order.
/// Reads the encrypted trade history, so the app has to be unlocked.
struct ExportTransactionsView: View {
    static let id = "export-transactions"

    let model: AppModel
    @State private var start = Calendar.current.date(from: Calendar.current.dateComponents([.year], from: Date()))!
    @State private var end = Date()
    @State private var message: String?
    @State private var exporting = false

    var body: some View {
        Form {
            DatePicker("From", selection: $start, displayedComponents: .date)
            DatePicker("To", selection: $end, in: start..., displayedComponents: .date)
            HStack {
                Text(message ?? (model.locked ? "Unlock the app first." : "\(model.environment.label) account"))
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
            let rows = max(0, String(decoding: csv, as: UTF8.self).split(separator: "\n").count - 1)
            message = "Saved \(rows) transaction\(rows == 1 ? "" : "s")."
        } catch {
            message = "Could not save: \(error.localizedDescription)"
        }
    }
}
