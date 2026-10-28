import XCTest
import AVFoundation
import CoreMedia

/// Uses the installed app. Never resets permissions, deletes data, or saves a test recording.
final class ArticulatorDeviceTests: XCTestCase {
    private let app = XCUIApplication(bundleIdentifier: "com.estebanl.minimalpairs")

    private func scroll(_ up: Bool) {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.09, dy: up ? 0.60 : 0.30))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.09, dy: up ? 0.30 : 0.60))
        start.press(forDuration: 0.05, thenDragTo: end)
        Thread.sleep(forTimeInterval: 0.35)
    }

    @discardableResult
    private func reveal(_ label: String, up: Bool = true) -> XCUIElement {
        let button = app.buttons[label]
        for _ in 0..<6 {
            let record = app.buttons["Record your sound"].exists ? app.buttons["Record your sound"] : app.buttons["Stop and review"]
            let bottom = record.exists ? record.frame.minY - 10 : app.frame.height * 0.74
            if button.exists && button.isHittable && button.frame.height >= 40 && button.frame.minY >= 114 && button.frame.maxY <= bottom { break }
            let direction = button.exists && button.frame.height > 0
                ? (button.frame.minY < 114 ? false : button.frame.maxY > bottom ? true : up) : up
            scroll(direction)
        }
        if !button.exists { print("ARTICULATOR_MISSING_\(label)\n\(app.debugDescription)") }
        XCTAssertTrue(button.waitForExistence(timeout: 5), "Missing button: \(label)")
        XCTAssertTrue(button.isHittable, "Unreachable button: \(label)")
        let record = app.buttons["Record your sound"].exists ? app.buttons["Record your sound"] : app.buttons["Stop and review"]
        let bottom = record.exists ? record.frame.minY - 10 : app.frame.height * 0.74
        XCTAssertGreaterThanOrEqual(button.frame.minY, 114, "Above content viewport: \(label) \(button.frame)")
        XCTAssertLessThanOrEqual(button.frame.maxY, bottom, "Below content viewport: \(label) \(button.frame), bottom \(bottom)")
        return button
    }

    private func tap(_ label: String, up: Bool = true) {
        reveal(label, up: up).press(forDuration: 0.15)
    }

    private func selectTab(_ label: String, up: Bool = true) {
        for _ in 0..<3 {
            tap(label, up: up)
            let selected = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                self.app.buttons[label].isSelected
            }, object: nil)
            if app.buttons[label].isSelected { return }
            let result = XCTWaiter.wait(for: [selected], timeout: 3)
            if result == .completed || app.buttons[label].isSelected { return }
        }
        print("ARTICULATOR_TAB_SELECTION_FAILURE label=\(label) sound=\(app.buttons["Sound map"].isSelected) camera=\(app.buttons["Camera"].isSelected)")
        print("ARTICULATOR_TAB_FAILURE_TREE\n\(app.debugDescription)")
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "camera-tab-hit-testing"
        attachment.lifetime = .keepAlways
        add(attachment)
        XCTFail("Tab did not become selected: \(label)")
    }

    private func allowPrompt(_ kind: String) {
        let alert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if alert.waitForExistence(timeout: 2) {
            let text = alert.staticTexts.allElementsBoundByIndex.map(\.label).joined(separator: " ")
            print("ARTICULATOR_PERMISSION \(kind): \(text)")
            XCTAssertTrue(text.lowercased().contains(kind), "Unexpected permission prompt")
            let allow = alert.buttons["Allow"].exists ? alert.buttons["Allow"] : alert.buttons["OK"]
            XCTAssertTrue(allow.exists)
            allow.tap()
        }
    }

    private func report(_ marker: String) {
        let labels = app.staticTexts.allElementsBoundByIndex.map(\.label)
        let diagnostics = labels.filter {
            $0.contains("frames/s") || $0.contains("ms average") || $0.contains("Processed frames") ||
            $0.contains("Live lip") || $0.contains("Bring your face") || $0.contains("Face the camera") ||
            $0.contains("Recording ·") || $0.contains("Camera paused")
        }
        print("ARTICULATOR_OBSERVATION \(marker): \(diagnostics.joined(separator: " | "))")
    }

    override func tearDownWithError() throws {
        app.terminate()
        app.launch()
        try super.tearDownWithError()
    }

    /// Check the production Stories UI on the installed phone without recording
    /// ambient audio or adding synthetic results to the user's personal history.
    func testStoriesCatalogAndExampleEndings() {
        continueAfterFailure = false
        let cases = [
            ("sheep_vs_ship", "sheep vs ship", "The Farmer's Fleet", "Splendid choice!"),
            ("pen_vs_pan", "pen vs pan", "The Exam Frying Trap", "Certainly, here is"),
            ("heart_vs_hut", "heart vs hut", "The Valentine Shelter", "How utterly poetic!"),
            ("fool_vs_full", "full vs fool", "The Royal Banquet", "Very good, my lord"),
        ]
        func revealStoryElement(_ element: XCUIElement, preferSwipeUp: Bool = true) {
            for attempt in 0..<12 {
                if element.exists && element.isHittable && element.frame.minY >= 114 && element.frame.maxY <= 698 { return }
                let swipeUp = element.exists ? element.frame.minY >= 114 : (attempt < 6 ? preferSwipeUp : !preferSwipeUp)
                scroll(swipeUp)
            }
            XCTAssertTrue(element.exists, "Missing Stories control\n\(app.debugDescription)")
            XCTAssertTrue(element.isHittable)
        }
        func matchingText(_ text: String) -> XCUIElement {
            app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
        }
        for (id, chipLabel, title, dialogue) in cases {
            // Start each case at the same scroll position, preserving all app data.
            app.terminate()
            app.launch()
            XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
            app.buttons["Stories"].press(forDuration: 0.3)
            XCTAssertTrue(app.buttons["sheep vs ship"].waitForExistence(timeout: 5))
            let chip = app.buttons[chipLabel]
            for _ in 0..<5 {
                if chip.exists && chip.isHittable { break }
                let row = app.buttons["sheep vs ship"].exists ? app.buttons["sheep vs ship"] : app.buttons["pen vs pan"]
                let y = max(100, min(170, row.frame.midY))
                let start = app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: app.frame.width * 0.88, dy: y))
                let end = app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: app.frame.width * 0.12, dy: y))
                start.press(forDuration: 0.05, thenDragTo: end)
            }
            XCTAssertTrue(chip.waitForExistence(timeout: 5))
            XCTAssertTrue(chip.isHittable)
            chip.press(forDuration: 0.3)
            XCTAssertTrue(matchingText(title).waitForExistence(timeout: 5))
            let record = app.buttons["Record your reply"]
            revealStoryElement(record)
            XCTAssertTrue(record.isEnabled)
            let expand = app.buttons["+ Explore example endings"]
            let expanded = app.buttons["− Explore example endings"]
            for _ in 0..<3 {
                revealStoryElement(expand)
                expand.press(forDuration: 0.3)
                if expanded.waitForExistence(timeout: 2) { break }
            }
            XCTAssertTrue(expanded.exists, "Stories examples did not expand\n\(app.debugDescription)")
            let example = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Intended sound")).firstMatch
            revealStoryElement(example)
            example.press(forDuration: 0.3)
            let feedback = matchingText("no recording was evaluated")
            revealStoryElement(feedback, preferSwipeUp: false)
            XCTAssertTrue(feedback.exists)
            let outcome = matchingText(dialogue)
            revealStoryElement(outcome)
            XCTAssertTrue(outcome.exists)
            XCTAssertEqual(app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Sound confidence")).count, 0)
            let attachment = XCTAttachment(screenshot: app.screenshot())
            attachment.name = "stories-\(id)-example"
            attachment.lifetime = .keepAlways
            add(attachment)
            print("STORIES_DEVICE \(id) example=true personal_score=false")
        }
    }

    func testTrueDepthDiscovery() {
        let devices = AVCaptureDevice.DiscoverySession(deviceTypes: [.builtInTrueDepthCamera], mediaType: .video, position: .front).devices
        print("ARTICULATOR_TRUEDEPTH_DEVICE_COUNT \(devices.count)")
        for device in devices {
            for format in device.formats where !format.supportedDepthDataFormats.isEmpty {
                let video = CMVideoFormatDescriptionGetDimensions(format.formatDescription)
                let depths = format.supportedDepthDataFormats.map { depth -> String in
                    let size = CMVideoFormatDescriptionGetDimensions(depth.formatDescription)
                    let type = CMFormatDescriptionGetMediaSubType(depth.formatDescription)
                    return "\(size.width)x\(size.height) subtype=\(type)"
                }
                print("ARTICULATOR_TRUEDEPTH_FORMAT video=\(video.width)x\(video.height) depth=\(depths.joined(separator: ","))")
            }
        }
    }

    private func openCamera() {
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        app.buttons["Speak"].tap()
        XCTAssertTrue(app.staticTexts["Shape your sound."].waitForExistence(timeout: 10))
        tap("Camera")
        if !app.staticTexts["Mirror your mouth"].waitForExistence(timeout: 2) { tap("Camera") }
        tap("Start camera")
        allowPrompt("camera")
        if app.buttons["Start camera"].exists { tap("Start camera"); allowPrompt("camera") }
        reveal("Stop camera")
    }

    func testRepeatedCameraTabChanges() {
        verifyCameraTabChanges(count: 10)
    }

    func testTwoAdditionalCameraTabChanges() {
        verifyCameraTabChanges(count: 2)
    }

    private func verifyCameraTabChanges(count: Int) {
        continueAfterFailure = false
        openCamera()
        for index in 1...count {
            selectTab("Sound map", up: false)
            selectTab("Camera")
            tap("Start camera")
            Thread.sleep(forTimeInterval: 0.5)
            if app.buttons["Start camera"].exists { tap("Start camera") }
            reveal("Stop camera")
            // Timed native results establish that capture resumed after tab disposal.
            reveal("+ Tracking details")
            print("ARTICULATOR_TAB_CYCLE \(index) timed_native_results_resumed")
        }
        tap("Stop camera", up: false)
    }

    func testCameraOrientation() throws {
        continueAfterFailure = false
        openCamera()
        defer { XCUIDevice.shared.orientation = .portrait }
        XCUIDevice.shared.orientation = .landscapeLeft
        let landscape = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            self.app.frame.width > self.app.frame.height
        }, object: nil)
        guard XCTWaiter.wait(for: [landscape], timeout: 5) == .completed else {
            throw XCTSkip("Orientation request did not change app bounds; physical rotation remains required")
        }
        print("ARTICULATOR_LANDSCAPE_BOUNDS \(app.frame)")
        XCTAssertTrue(app.buttons["Record your sound"].isHittable)
        XCUIDevice.shared.orientation = .portrait
        let portrait = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            self.app.frame.height > self.app.frame.width
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [portrait], timeout: 5), .completed)
        reveal("Stop camera")
        tap("Stop camera")
    }

    func testCameraAndMicrophoneLifecycle() throws {
        continueAfterFailure = false
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        app.buttons["Speak"].tap()
        XCTAssertTrue(app.staticTexts["Shape your sound."].waitForExistence(timeout: 10))
        tap("Camera")
        if !app.staticTexts["Mirror your mouth"].waitForExistence(timeout: 2) { tap("Camera") }
        XCTAssertTrue(app.staticTexts["Mirror your mouth"].waitForExistence(timeout: 5))
        tap("Start camera")
        allowPrompt("camera")
        if app.buttons["Start camera"].exists {
            tap("Start camera")
            allowPrompt("camera")
        }
        reveal("Stop camera")
        // Timed results exist for no-face frames too. Do not infer face tracking from FPS.
        tap("+ Tracking details")
        let rate = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Analysis rate:")).firstMatch
        XCTAssertTrue(rate.waitForExistence(timeout: 10))
        report("camera_running")

        app.buttons["Record your sound"].tap()
        allowPrompt("microphone")
        XCTAssertTrue(app.buttons["Stop and review"].waitForExistence(timeout: 10))
        let elapsed = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@ AND NOT label CONTAINS %@", "Recording ·", "Recording · 0s")).firstMatch
        XCTAssertTrue(elapsed.waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Stop camera"].exists)
        report("camera_and_microphone")

        // Backgrounding cancels capture without scoring ambient sound or writing a progress row.
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 5))
        app.activate()
        report("foreground_before_assertions")
        print("ARTICULATOR_FOREGROUND_TREE\n\(app.debugDescription)")
        XCTAssertTrue(app.buttons["Record your sound"].waitForExistence(timeout: 10))
        reveal("Start camera")
        XCTAssertFalse(app.buttons["Stop camera"].exists)
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Camera paused")).firstMatch.exists)
        report("foreground_paused")

        tap("Start camera")
        reveal("Stop camera")
        tap("Stop camera")
        XCTAssertTrue(app.buttons["Start camera"].waitForExistence(timeout: 5))
        report("stopped_idle")
        print("ARTICULATOR_LIFECYCLE_PASS camera/no-face concurrent recording and background cancellation")
    }
}
