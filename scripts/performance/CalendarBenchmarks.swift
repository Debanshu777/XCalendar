import XCTest

/// Runs against the release app installed by the Kotlin Toolchain.
/// Simulator numbers are host-dependent; compare only on the same host/runtime.
final class CalendarBenchmarks: XCTestCase {
    private let app = XCUIApplication(bundleIdentifier: "com.debanshu.xcalendar.XCalendar")

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func tap(_ label: String) {
        let element = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", label)).firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Missing \(label)\n\(app.debugDescription)")
        element.tap()
    }

    private func swipeMonth(forward: Bool) {
        // Use the same screen coordinates on both builds: the baseline drops
        // the pager modifier, including its accessibility identifier.
        let left = app.coordinate(withNormalizedOffset: CGVector(dx: 0.15, dy: 0.5))
        let right = app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5))
        // A full-width drag reliably crosses the pager threshold. XCTest's
        // application-level slow swipe can stop short and snap back.
        (forward ? right : left).press(forDuration: 0.05, thenDragTo: forward ? left : right)
    }

    private func monthRoundTrip() {
        swipeMonth(forward: true)
        Thread.sleep(forTimeInterval: 0.5)
        swipeMonth(forward: false)
        Thread.sleep(forTimeInterval: 0.5)
    }

    func testColdLaunch() {
        let options = XCTMeasureOptions()
        options.iterationCount = 10
        measure(metrics: [XCTApplicationLaunchMetric(waitUntilResponsive: true)], options: options) {
            app.launch()
        }
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testCalendarJourney() {
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        let options = XCTMeasureOptions()
        options.iterationCount = 5
        measure(metrics: [XCTClockMetric(), XCTCPUMetric(application: app), XCTMemoryMetric(application: app)], options: options) {
            for label in ["Week", "3 Day", "Day", "Schedule", "Month"] {
                tap(label)
            }
            monthRoundTrip()
        }
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)
        print("FINAL_UI_HIERARCHY\n\(app.debugDescription)")
    }

    /// Opt-in dense UI fixture journey. One iteration visits all five views.
    /// Fixed gestures and assertions are part of both compared measurements.
    func testPopulatedJourney() {
        // Fixture slots 01–06 are timed daytime events. All-day rows (09–10)
        // and offscreen semantics nodes cannot satisfy this visibility check.
        let timedEventPredicate = NSPredicate(
            format: "label MATCHES %@", ".*November [0-9]{2} / 0[1-6] — .*planning:.*"
        )
        func hasVisibleTimedEvent() -> Bool {
            let viewport = app.frame
            let grid = CGRect(x: viewport.minX + viewport.width * 0.12,
                              y: viewport.minY + viewport.height * 0.15,
                              width: viewport.width * 0.88, height: viewport.height * 0.70)
            return app.descendants(matching: .any).matching(timedEventPredicate)
                .allElementsBoundByIndex.contains { !$0.frame.isEmpty && grid.intersects($0.frame) }
        }
        let options = XCTMeasureOptions()
        options.iterationCount = 3
        options.invocationOptions = [.manuallyStart]
        measure(metrics: [XCTClockMetric(), XCTCPUMetric(application: app), XCTMemoryMetric(application: app)], options: options) {
            // Restart outside the interval, including XCTest's warmup. The
            // original pager can leave its month state stale after a round trip;
            // each sample must start from the same fixture state on both builds.
            app.terminate()
            app.launch()
            XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
            startMeasuring()
            for label in ["Day", "3 Day", "Week"] {
                tap(label)
                Thread.sleep(forTimeInterval: 0.5)
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.6, dy: 0.75))
                    .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.6, dy: 0.35)))
                Thread.sleep(forTimeInterval: 0.5)
                let nine = app.descendants(matching: .any)
                    .matching(NSPredicate(format: "label == %@", "9 am")).firstMatch
                XCTAssertTrue(nine.waitForExistence(timeout: 5), "\(label) must reach working hours")
                let viewport = app.frame
                XCTAssertTrue(nine.frame.minY > viewport.minY + viewport.height * 0.15 &&
                              nine.frame.maxY < viewport.minY + viewport.height * 0.85,
                              "9 am must be visibly inside \(label), not outside the viewport\n\(app.debugDescription)")
                XCTAssertTrue(hasVisibleTimedEvent(),
                              "\(label) must show a timed daytime fixture event inside its visible grid\n\(app.debugDescription)")
            }
            tap("Schedule")
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7))
                .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3)))
            Thread.sleep(forTimeInterval: 0.5)
            XCTAssertTrue(hasVisibleTimedEvent(),
                          "Schedule must show a populated daytime fixture row\n\(app.debugDescription)")
            tap("Month")
            monthRoundTrip()
        }
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)
        print("POPULATED_FINAL_UI_HIERARCHY\n\(app.debugDescription)")
    }

    // Diagnostic run: attach Instruments to XCalendar during this pause.
    // Keep this separate from the unprofiled performance measurements.
    func testProfileJourney() {
        app.launch()
        print("PROFILE_READY")
        Thread.sleep(forTimeInterval: 10)
        for _ in 0..<3 {
            for label in ["Week", "3 Day", "Day", "Schedule", "Month"] { tap(label) }
            monthRoundTrip()
        }
    }

    func testEventDialog() {
        app.launch()
        tap("Add Event")
        // Compose's multiline title is exposed as a text view on iOS, not
        // necessarily an XCTest text field. Assert the editor's visible controls.
        let save = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Save")).firstMatch
        XCTAssertTrue(save.waitForExistence(timeout: 10), "Event editor must open with a cached account\n\(app.debugDescription)")
        let titleField = app.textViews.matching(NSPredicate(format: "label == %@", "Title")).firstMatch
        XCTAssertTrue(titleField.waitForExistence(timeout: 10), "Editor needs an editable title\n\(app.debugDescription)")
        titleField.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        // A fresh simulator may present the system keyboard's language tutorial.
        if app.staticTexts["Type English and Hindi"].waitForExistence(timeout: 2) {
            app.buttons["Continue"].tap()
        }
        app.typeText("Keyboard draft validation")
        let keyboard = app.keyboards.firstMatch
        let letterKey = keyboard.keys["q"]
        XCTAssertTrue(letterKey.waitForExistence(timeout: 5), "Enable the simulator software keyboard before this check")
        XCTAssertGreaterThan(keyboard.frame.height, 150, "A hardware-keyboard placeholder is not IME evidence")
        XCTAssertLessThan(letterKey.frame.maxY, app.frame.maxY, "Software keys must be on screen")
        let keyboardScreenshot = XCTAttachment(screenshot: app.screenshot())
        keyboardScreenshot.name = "Editor with keyboard"
        keyboardScreenshot.lifetime = .keepAlways
        add(keyboardScreenshot)
        // The actions stay reachable while form content scrolls above the IME.
        let description = app.textViews.matching(NSPredicate(format: "label == %@", "Description")).firstMatch
        for _ in 0..<3 {
            if description.isHittable { break }
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.55))
                .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.3)))
        }
        XCTAssertTrue(description.isHittable, "Description must scroll above the keyboard")
        XCTAssertLessThan(save.frame.maxY, app.keyboards.firstMatch.frame.minY, "Save must remain above the keyboard")
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)
        print("EDITOR_UI_HIERARCHY\n\(app.debugDescription)")
        tap("Cancel")
    }

    func testNavigationViewport() {
        app.launch()
        tap("Day")
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.6, dy: 0.75))
            .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.6, dy: 0.35)))
        print("NAVIGATION_VIDEO_READY")
        Thread.sleep(forTimeInterval: 2)
        for _ in 0..<3 {
            tap("3 Day")
            Thread.sleep(forTimeInterval: 1)
            tap("Day")
            Thread.sleep(forTimeInterval: 1)
        }
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Navigation viewport"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Opt-in: the UI-only fixture calendars intentionally do not exist in Room.
    /// The real database must reject saving; the editor must retain the draft.
    func testSaveFailureRetainsDraft() {
        app.launch()
        tap("Add Event")
        let title = app.textViews.matching(NSPredicate(format: "label == %@", "Title")).firstMatch
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        title.tap()
        app.typeText("Failed save draft")
        tap("Save")
        let error = app.descendants(matching: .any).matching(NSPredicate(
            format: "label == %@", "Couldn't save your changes. Please try again."
        )).firstMatch
        XCTAssertTrue(error.waitForExistence(timeout: 10), "The fixture must exercise an actual failed database write\n\(app.debugDescription)")
        Thread.sleep(forTimeInterval: 5)
        XCTAssertTrue(error.exists, "A snackbar behind the sheet must not clear the form's error")
        let draft = app.textViews.matching(NSPredicate(format: "value == %@ OR label CONTAINS %@", "Failed save draft", "Failed save draft")).firstMatch
        XCTAssertTrue(draft.exists, "Failed save must retain the draft")
        tap("Save")
        XCTAssertTrue(error.waitForExistence(timeout: 10), "Retry must remain possible without dismissing")
        XCTAssertTrue(draft.exists)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Failed save retains draft"
        attachment.lifetime = .keepAlways
        add(attachment)
        tap("Cancel")
    }

    func testMonthRoundTrip() {
        app.launch()
        let monthPattern = "(January|February|March|April|May|June|July|August|September|October|November|December)( [0-9]{4})?"
        let title = app.descendants(matching: .any).matching(NSPredicate(
            format: "label MATCHES %@", monthPattern
        )).firstMatch
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        let initialMonth = title.label
        swipeMonth(forward: true)
        // XCTest can consider UIKit idle before a Compose pager settles.
        let changed = app.descendants(matching: .any).matching(NSPredicate(
            format: "label MATCHES %@ AND label != %@", monthPattern, initialMonth
        )).firstMatch
        XCTAssertTrue(changed.waitForExistence(timeout: 10), "Header must advance\n\(app.debugDescription)")
        swipeMonth(forward: false)
        let restored = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", initialMonth)).firstMatch
        XCTAssertTrue(restored.waitForExistence(timeout: 10), "Header must follow the pager back to its initial month")
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
