import SwiftUI
import TraderCore

/// Number-only text field: characters that aren't allowed never appear (no letters, signs or spaces; decimals
/// only when `allowsDecimal`). `isInvalid` draws a red outline (e.g. empty or zero quantity).
struct NumericField: View {
    let placeholder: String
    @Binding var text: String
    var allowsDecimal = false
    var isInvalid = false
    var width: CGFloat = 80

    @State private var draft = ""

    var body: some View {
        TextField(placeholder, text: $draft)
            .multilineTextAlignment(.trailing)
            .frame(width: width)
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(isInvalid ? Color.red : .clear, lineWidth: 1.5))
            .onAppear { draft = text }
            .onChange(of: draft) { _, typed in
                let clean = NumericInput.sanitize(typed, allowsDecimal: allowsDecimal)
                if clean != typed {
                    draft = clean  // drop what isn't allowed; the next change pushes it on
                } else if clean != text {
                    text = clean
                }
            }
            .onChange(of: text) { _, updated in
                if updated != draft { draft = updated }  // e.g. quantity filled in from the volume
            }
    }
}
