import AppKit
import WebKit
import XCTest

// A native WebKit unit test of actual GLB/ImageBitmap decoding and CSP enforcement.
// It loads an isolated in-memory fixture, without browser navigation or a WebGL renderer.
final class TextureTests: XCTestCase {
    private var session: TextureSession?

    private func runFixture(_ name: String, decoded: Int, blockedBlob: Bool) throws {
        let directory = try XCTUnwrap(ProcessInfo.processInfo.environment["MOUTH_TEXTURE_FIXTURES"])
        let html = try String(contentsOfFile: directory + "/" + name + ".html", encoding: .utf8)
        let completed = expectation(description: "Actual texture decoding under \(name) CSP")
        DispatchQueue.main.async {
            _ = NSApplication.shared
            self.session = TextureSession(html: html) { result in
                XCTAssertNil(result["error"])
                XCTAssertEqual((result["declared"] as? NSNumber)?.intValue, 10)
                XCTAssertEqual((result["pngCount"] as? NSNumber)?.intValue, 10)
                XCTAssertEqual((result["decoded"] as? NSNumber)?.intValue, decoded)
                XCTAssertEqual(result["allImageBitmaps"] as? Bool, decoded == 10)
                XCTAssertEqual(result["networkBlockedByCsp"] as? Bool, true)
                XCTAssertEqual(result["blobBlockedByCsp"] as? Bool, blockedBlob)
                completed.fulfill()
            }
        }
        wait(for: [completed], timeout: 60)
        session?.close()
        session = nil
    }

    func testBundledPolicyDecodesEveryEmbeddedPngWithoutNetworkAccess() throws {
        try runFixture("blob-only", decoded: 10, blockedBlob: false)
    }
    func testOriginalConnectNonePolicyFailsRealEmbeddedBitmapDecoding() throws {
        try runFixture("connect-none", decoded: 0, blockedBlob: true)
    }
}

private final class TextureSession: NSObject, WKScriptMessageHandler {
    private var webView: WKWebView!
    private var finished = false
    private let completion: ([String: Any]) -> Void
    init(html: String, completion: @escaping ([String: Any]) -> Void) {
        self.completion = completion
        super.init()
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        configuration.userContentController.add(self, name: "textureResult")
        webView = WKWebView(frame: CGRect(x: 0, y: 0, width: 100, height: 100), configuration: configuration)
        webView.loadHTMLString(html, baseURL: nil)
    }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard !finished, let result = message.body as? [String: Any] else { return }
        finished = true
        completion(result)
    }
    func close() {
        webView.configuration.userContentController.removeScriptMessageHandler(forName: "textureResult")
        webView.stopLoading()
        webView = nil
    }
}
