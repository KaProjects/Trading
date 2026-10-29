import SwiftUI

/// Tables: as wide as the window when there is room (the content stretches), and once the window is narrower than
/// the content needs, the content keeps that width and scrolls sideways — never cut off, never squeezed.
struct StretchOrScroll<Content: View>: View {
    @ViewBuilder let content: Content
    @State private var width: CGFloat = 0

    var body: some View {
        ScrollView(.horizontal) {
            AtLeastWidth(width: width) { content }
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }
}

/// Proposes max(available width, the content's ideal width) to its one subview.
private struct AtLeastWidth: Layout {
    var width: CGFloat

    private func target(_ child: LayoutSubview) -> CGFloat {
        max(width, child.sizeThatFits(.unspecified).width)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let child = subviews.first else { return .zero }
        let target = target(child)
        let size = child.sizeThatFits(ProposedViewSize(width: target, height: nil))
        return CGSize(width: max(target, size.width), height: size.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard let child = subviews.first else { return }
        child.place(at: bounds.origin, anchor: .topLeading, proposal: ProposedViewSize(width: target(child), height: nil))
    }
}
