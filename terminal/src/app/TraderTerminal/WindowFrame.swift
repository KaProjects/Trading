import AppKit
import SwiftUI

/// Remembers a window's position, size and screen across launches (NSWindow frame autosave).
///
/// SwiftUI places the window itself at launch (its own `NSWindow Frame <scene id>` entry, which went stale and put
/// the window on the wrong monitor), `setFrameUsingName` reports success without moving a SwiftUI window to another
/// screen, and attaching the autosave name resets the frame. So: drop SwiftUI's entry, disable restoration, and once
/// the window is on screen read our saved frame, attach the autosave name, then set the frame directly.
struct WindowFrameAutosave: NSViewRepresentable {
    let name: String
    let sceneId: String

    func makeNSView(context: Context) -> NSView {
        UserDefaults.standard.removeObject(forKey: "NSWindow Frame \(sceneId)")
        return PlacingView(name: name)
    }

    /// Hides the window the moment it is attached (before it is drawn), moves it, then shows it — no visible jump.
    final class PlacingView: NSView {
        let name: String
        private var placed = false

        init(name: String) {
            self.name = name
            super.init(frame: .zero)
        }

        required init?(coder: NSCoder) { fatalError("not used") }

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            guard let window, !placed else { return }
            placed = true
            window.isRestorable = false
            window.alphaValue = 0
            let name = name
            // SwiftUI positions the window right after it appears; place it after that.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                let saved = WindowFrameAutosave.savedFrame(name)  // read before attaching: attaching resets the frame
                window.setFrameAutosaveName(name)  // saves on every move/resize from now on
                // Only restore onto a connected screen (e.g. not after unplugging that monitor).
                if let saved, NSScreen.screens.contains(where: { $0.visibleFrame.intersects(saved) }) {
                    window.setFrame(saved, display: true)
                }
                window.alphaValue = 1
            }
        }
    }

    func updateNSView(_ nsView: NSView, context: Context) {}

    /// "x y w h screenX screenY screenW screenH" → the window rect.
    static func savedFrame(_ name: String) -> NSRect? {
        guard let text = UserDefaults.standard.string(forKey: "NSWindow Frame \(name)") else { return nil }
        let numbers = text.split(separator: " ").compactMap { Double($0) }
        guard numbers.count >= 4, numbers[2] > 100, numbers[3] > 100 else { return nil }
        return NSRect(x: numbers[0], y: numbers[1], width: numbers[2], height: numbers[3])
    }
}

extension View {
    func rememberWindowFrame(_ name: String, sceneId: String) -> some View {
        background(WindowFrameAutosave(name: name, sceneId: sceneId))
    }
}
