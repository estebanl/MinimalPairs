// Create a labelled sheet from actual native JVM screenshots; no geometry is retouched.
import AppKit
let images = CommandLine.arguments.dropFirst(2).map { NSImage(contentsOfFile: $0)! }
let width: CGFloat = 840, height: CGFloat = 1220
let result = NSImage(size: NSSize(width: width, height: height))
result.lockFocus()
NSColor(calibratedWhite: 0.95, alpha: 1).setFill()
NSRect(x: 0, y: 0, width: width, height: height).fill()
func label(_ text: String, _ x: CGFloat, _ y: CGFloat, _ size: CGFloat) {
    (text as NSString).draw(at: NSPoint(x: x, y: y), withAttributes: [
        .font: NSFont.systemFont(ofSize: size), .foregroundColor: NSColor.black])
}
label("Mouth section — actual JVM Compose renders", 20, 1180, 22)
label("Before", 20, 1145, 18); label("After: section fill + diffuse shading", 430, 1145, 18)
for (i, image) in images.enumerated() {
    image.draw(in: NSRect(x: 20 + CGFloat(i) * 410, y: 35, width: 390, height: 1100))
}
label("Same /ʃ/ mesh bytes. Illustrative fill; no measured internal anatomy or device validation.", 20, 10, 13)
result.unlockFocus()
let bitmap = NSBitmapImageRep(data: result.tiffRepresentation!)!
try bitmap.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
