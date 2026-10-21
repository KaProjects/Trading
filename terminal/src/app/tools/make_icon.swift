// Renders the app icon: a vector redraw of Trading-web's favicon (green bars, up arrow, dollar sign)
// on a macOS-style rounded tile. Usage: swift tools/make_icon.swift <output-1024.png>
import AppKit

let size: CGFloat = 1024
let output = URL(fileURLWithPath: CommandLine.arguments[1])

func color(_ hex: UInt32) -> NSColor {
    NSColor(srgbRed: CGFloat(hex >> 16 & 0xFF) / 255, green: CGFloat(hex >> 8 & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
}

/// Favicon artwork was drawn on a 256 grid (y down); map it into the tile.
func p(_ x: CGFloat, _ y: CGFloat) -> NSPoint { NSPoint(x: 140 + x * 2.9, y: size - (120 + y * 2.9)) }

let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(size), pixelsHigh: Int(size), bitsPerSample: 8,
                           samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB,
                           bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)

// Tile: macOS icon grid (824 pt body, ~185 pt corner radius) with a soft shadow.
let tile = NSBezierPath(roundedRect: NSRect(x: 100, y: 100, width: 824, height: 824), xRadius: 185, yRadius: 185)
NSGraphicsContext.saveGraphicsState()
let shadow = NSShadow()
shadow.shadowColor = NSColor.black.withAlphaComponent(0.25)
shadow.shadowOffset = NSSize(width: 0, height: -10)
shadow.shadowBlurRadius = 24
shadow.set()
NSGradient(starting: .white, ending: color(0xEEF5F0))!.draw(in: tile, angle: -90)
NSGraphicsContext.restoreGraphicsState()

// Bars, dark to light green.
let bars: [(x0: CGFloat, x1: CGFloat, top: CGFloat, hex: UInt32)] = [
    (25, 62, 160, 0x136B35), (64, 104, 128, 0x1A8A45), (106, 149, 97, 0x22A259),
    (151, 192, 143, 0x3CC477), (194, 235, 143, 0x55D68E),
]
for bar in bars {
    let topLeft = p(bar.x0, bar.top), bottomRight = p(bar.x1, 215)
    color(bar.hex).setFill()
    NSBezierPath(rect: NSRect(x: topLeft.x, y: bottomRight.y, width: bottomRight.x - topLeft.x,
                              height: topLeft.y - bottomRight.y)).fill()
}

// Up-right arrow.
let arrowColor = color(0x3FD07F)
let shaft = NSBezierPath()
shaft.move(to: p(25, 105))
shaft.line(to: p(88, 52))
shaft.lineWidth = 34
shaft.lineCapStyle = .round
arrowColor.setStroke()
shaft.stroke()
let head = NSBezierPath()
head.move(to: p(108, 34))
head.line(to: p(64, 42))
head.line(to: p(100, 78))
head.close()
arrowColor.setFill()
head.fill()

// Dollar sign.
let font = NSFont.systemFont(ofSize: 330, weight: .heavy)
let dollar = NSAttributedString(string: "$", attributes: [.font: font, .foregroundColor: color(0x229052)])
let bounds = dollar.size()
let center = p(190, 78)
dollar.draw(at: NSPoint(x: center.x - bounds.width / 2, y: center.y - bounds.height / 2))

NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: output)
