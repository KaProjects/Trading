import SwiftUI
import TraderCore

/// Order warning thresholds (D5.4, D5.5). Saved encrypted by the service; needs the app unlocked.
struct SettingsView: View {
    let model: AppModel
    @State private var fields: [Field: String] = [:]
    @State private var message: String?
    @State private var saving = false

    enum Field: CaseIterable {
        case pctWarn, pctStrong, valueWarn, quantityWarn, priceDeviation

        var label: String {
            switch self {
            case .pctWarn: "Warn when order is above (% of account)"
            case .pctStrong: "Strong warning above (% of account)"
            case .valueWarn: "High order value (base currency)"
            case .quantityWarn: "High quantity (shares)"
            case .priceDeviation: "Limit price away from last price (%)"
            }
        }
    }

    var body: some View {
        Form {
            Section("Order warnings — never block, shown under the inputs and in the summary") {
                ForEach(Field.allCases, id: \.self) { field in
                    TextField(field.label, text: Binding(get: { fields[field] ?? "" }, set: { fields[field] = $0 }))
                        .multilineTextAlignment(.trailing)
                }
            }
            HStack {
                if let message { Text(message).foregroundStyle(.secondary) }
                Spacer()
                Button("Save") { Task { await save() } }
                    .keyboardShortcut(.defaultAction)
                    .disabled(saving || model.locked || thresholds == nil)
            }
        }
        .formStyle(.grouped)
        .frame(width: 520)
        .task(id: model.locked) { await load() }
    }

    private var thresholds: RuleThresholds? {
        func value(_ field: Field) -> Amount? { DecimalInput.parse(fields[field] ?? "").map(Amount.init) }
        guard let pctWarn = value(.pctWarn), let pctStrong = value(.pctStrong), let valueWarn = value(.valueWarn),
              let quantityWarn = value(.quantityWarn), let deviation = value(.priceDeviation) else { return nil }
        return RuleThresholds(pctOfAccountWarn: pctWarn, pctOfAccountStrong: pctStrong, orderValueWarn: valueWarn,
                              quantityWarn: quantityWarn, priceDeviationWarn: deviation)
    }

    private func load() async {
        guard !model.locked else {
            message = "Unlock the app to change settings."
            return
        }
        do {
            let current = try await model.api.ruleThresholds()
            fields = [.pctWarn: current.pctOfAccountWarn.description, .pctStrong: current.pctOfAccountStrong.description,
                      .valueWarn: current.orderValueWarn.description, .quantityWarn: current.quantityWarn.description,
                      .priceDeviation: current.priceDeviationWarn.description]
            message = nil
        } catch {
            message = "Service not available."
        }
    }

    private func save() async {
        guard let thresholds else { return }
        saving = true
        defer { saving = false }
        do {
            _ = try await model.api.saveRuleThresholds(thresholds)
            message = "Saved."
        } catch ServiceError.http(_, _, let detail) {
            message = detail ?? "Invalid values (strong warning must be ≥ warning)."
        } catch {
            message = error.localizedDescription
        }
    }
}
