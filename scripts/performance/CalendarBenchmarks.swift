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
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)
        print("EDITOR_UI_HIERARCHY\n\(app.debugDescription)")
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
