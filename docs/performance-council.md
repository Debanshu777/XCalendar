# Performance council follow-up

This follow-up extends the [initial performance review](performance.md) with
three independent reviewers for tracing, UI, and correctness. Scores are review
judgments out of 10, not measured performance units or proof of a global optimum.
The stopping criteria are reproducible workload completion, valid traces,
reviewed device screenshots, passing relevant regressions, and no remaining
high-confidence defect in the changed user flows. Hardware and simulator limits
remain explicit rather than forcing every reviewer to award 10.

## Review loops

| Review | Performance | UI | Correctness | Findings driving the next loop |
| --- | ---: | ---: | ---: | --- |
| Initial council | 7.5 | 4.5 | 6.5 | Sparse benchmark coverage; navigation viewport escaped its clipping; incomplete editor and persistence handling |
| First fixes | 8.1 | 8 | 8 | Deferred inset reads verified; require dense data and real iOS keyboard evidence; account refresh and Store write errors found |
| Further fixes, provisional | 8.1 | 8.5 | 9 | Account refresh, demo import, and failed-save propagation repaired; dense runs and final device checks required |
| Populated Week idle fix | 8.3 | — | — | Clean trace verifies that continuous idle drawing/layout has stopped |
| Dense Android and iOS evidence | 8.8 | — | — | Three accepted runs per build; current editor trace required |
| Final review | 9 | 9 | 9.5 | Editor trace, real keyboards, navigation frames, persistence restarts and all-day reachability accepted; no remaining must-fix in reviewed flows |

The final performance score is **9/10** after reviewing the current editor
trace: evidence quality 2.4/2.5, frame behavior 2.1/2.5, CPU and
composition 2.4/2.5, coverage 2.1/2.5. Remaining transition outliers and simulator
limits prevent a perfect score. The [sanitized results JSON](performance-council-results.json)
retains individual samples, artifact hashes, trace health and score history.

## Navigation and editor changes

The reported Day → 3 Day bottom-padding jump came from tall shared elements
being promoted into the theme's shared-transition overlay. The columns escaped
their scrolling viewport during the animation, then returned to its bottom
boundary. Time columns, day columns, and event cards now render inside that
viewport throughout the transition. Date headers retain their shared transition.
The iOS default slide/veil also left transient gray strips around moving shared
elements; calendar modes now use explicit 300 ms crossfades around those shared
bounds animations.

Scaffold inset values are read during measurement. Opening the month dropdown
therefore no longer invalidates the calendar's parent composition on every
animation frame. Three diagnostic captures per build compare the pre-council
release with the first council rendering revision:

| Dropdown metric | Before median (range) | After median (range) |
| --- | ---: | ---: |
| NavigationHost executions | 479 (475–480) | 0 |
| CalendarApp content executions | 489 (485–490) | 0 |
| Main-thread CPU | 3,210.787 ms (3,186.026–3,248.096) | 2,958.263 ms (2,909.097–2,958.825) |
| Frame p95 | 15.858 ms (15.555–15.864) | 15.372 ms (15.236–15.474) |
| Frame p99 | 21.680 ms (21.086–22.509) | 21.761 ms (21.050–25.230) |

All six traces pass health checks. Main-thread CPU is 7.9% lower, with
non-overlapping run ranges; p99 is neutral. Aggregate app deadline misses are
6/1,642 before and 1/1,643 after; Self Jank is 8/1,642 and 7/1,643. Those small
counts do not establish zero-jank behavior. The runs are grouped before/after,
not randomized. Device UTC/timezone were not captured in the before group.
These dropdown results describe that **first council revision**, not the later
dense v2 build or the final PR commit. Its after APK SHA-256 is
`8efcf2e6827749b67263b1015a77d2125acbd36dcba5bbe1b8c04032a8bc535b`;
the before APK checksum was not recorded.

The editor now pins Cancel/Save above a bounded scrolling form, supports actual
editing, validates dates and times, preserves unchanged exact timestamps and
reminders, and keeps a draft open after a failed write. Save is guarded against
duplicate submission and only dismisses after persistence succeeds. Android
and iOS screenshots confirm that the software keyboard leaves the actions and
the scrolled description reachable. An unavailable calendar explicitly disables
Save. See the [correctness review](performance.md#correctness-review) for the
account cascade and static-demo import fixes.
Form errors remain until retry or cancellation; a snackbar behind the sheet
cannot clear them. Save/delete failures use a friendly message while validation
errors retain their actionable explanation.

## Populated workload

The deterministic fixture supplies one account, five calendars, nine holidays,
and 300 events: 180 overlapping daytime events, 60 overnight events, and 60
all-day events including multi-day spans. Its selected date is November 8, 2026.
The original `adbd2c8` and current release receive identical generated UI state.

- Data SHA-256: `1bf189c4a0c701687e4b92b1febd987abe417cd7977b8d7a5c8c7b1be4bd6346`.
- Generated source SHA-256: `96b55ab93ce8ed312a776be212ca6f669230beea83e34bec962f4e89dbd0082a`.
- The temporary source and CalendarApp binding are restored after the build,
  including normal interruption. Neither fixture nor diagnostics ship in the
  normal application.
- Repository initialization still runs. This measures populated UI rendering,
  not database, network, synchronization, or write throughput.
- Local timestamps use each device's system timezone at initialization. Today
  and the clock remain live. Compare within the same local date/timezone and do
  not press Today. Schedule anchors to currentDate, so this fixture's Schedule
  workload must be run during November 2026 or deliberately updated on both
  sides with new fixture hashes. It is not frozen-clock replay.

Each Android capture visits Day, 3 Day, Week, Schedule, and Month. Fixed drags
reach working hours; assertions require 9 am and an on-screen daytime event,
excluding all-day or off-screen matches. Schedule must also contain a visible
timed event. Month paging crosses dense November and mostly empty December,
then returns. Three fresh-process captures are taken per build. The equivalent
iOS XCTest measures three iterations, restarting the app outside each measured
interval (including warmup), with caches retained. This prevents the original
pager's stale month state from changing the next iteration's initial workload.
The measurement boundary uses XCTest's [manual start option](https://developer.apple.com/documentation/xctest/xctmeasureoptions/invocationoptions-swift.struct/manuallystart).
Absolute metrics must not be compared across platforms.

Overall journey percentiles combine multiple views. Corrected overlap/range
handling can display cards the original app omitted. Composition counts include
initial composition, and inclusive slice times overlap. Perfetto health checks
are required before accepting a capture.

### Findings from the rejected first populated run

The first updated Android run failed the Week visibility assertion and was
rejected. All-day events occupied full-height timed lanes, connecting separate
overlap groups and reducing ordinary events to thin strips. They now occupy a
separate, aligned header, showing up to three rows with scrolling for additional
items. They remain clickable and are excluded from timed overlap allocation.

That run also overflowed its 256 MiB trace buffer: 73,768,960 bytes were
overwritten and 483 incremental-state-invalid packets were recorded. Retained
idle buckets showed roughly 600 frames and layout passes per five seconds,
with zero recomposition. Spanning events had reused the same shared-transition
identity on several dates. Shared-event keys now include the rendered date,
preserving matches across views while separating distinct day segments. Narrow
cards retain their full accessible title independently of text fitting.

The rejected run is diagnostic evidence only, never an accepted performance
comparison. Enlarging the buffer would not repair unnecessary idle drawing.
The clean v2 Week follow-up independently verifies **zero frames, layout passes
and recompositions in its final 20 seconds**, with 0.437 ms main-thread CPU and
zero RenderThread CPU. The first complete v2 journey also has zero frame,
layout and recomposition calls during its final 10 seconds in Month. No residual
Month idle loop was observed. Occurrence identity and all-day placement changed
together; these traces verify the combined fix, not each change in isolation.
The 15.7 MB Week trace and rejected 268.8 MB trace have different durations and
workloads, so their file sizes are not a performance speedup measure.

### Dense Android results

All six original/v2 captures pass health checks and complete the fixed populated
journey. They use the same 300-event fixture and 256 MiB trace configuration on
a Pixel 9 running Android 17. The three original runs were collected first,
then the three v2 runs; they are not randomized or interleaved pairs. Battery
temperatures were 32.7–33.3°C before and 33.0–33.2°C after. Global thermal status
was zero throughout, while some local sensors reported status 1 in both groups;
these are similar recorded conditions, not proof of identical thermal behavior.

| Metric | Original median (range) | Dense v2 median (range) |
| --- | ---: | ---: |
| Main-thread CPU | 5,267.739 ms (5,216.792–5,373.628) | 4,140.554 ms (4,055.932–4,169.164) |
| RenderThread CPU | 3,046.067 ms (2,994.331–3,130.021) | 2,252.022 ms (2,237.343–2,350.858) |
| Frame p50 | 7.293 ms (7.044–7.491) | 5.966 ms (5.891–6.041) |
| Frame p95 | 15.487 ms (15.272–15.805) | 15.819 ms (15.266–15.848) |
| Frame p99 | 28.158 ms (27.063–30.407) | 28.650 ms (25.919–29.459) |
| Worst frame per run | 117.281 ms (111.457–123.079) | 68.153 ms (67.971–70.563) |
| Bottom navigation executions | 484 (482–484) | 15 |
| NavigationHost executions | 5 | 5 |
| Month DayCell executions | 1,543 | 1,470 |
| Compose:recompose executions | 2,186 (2,186–2,188) | 252 (249–252) |

Main-thread CPU is 21.4% lower and RenderThread CPU 26.1% lower, with
non-overlapping run ranges. The median worst frame is 41.9% lower, but
68–71 ms outliers remain. **p95 and p99 are neutral in this small sample**:
their ranges overlap and their after medians are slightly higher, so neither
percentile supports an improvement claim. Aggregate app deadline misses are
60/3,301 frames (1.82%) before and 42/2,661 (1.58%) after. Self Jank is nearly
unchanged as a proportion: 137/3,301 (4.15%) versus 110/2,661 (4.13%).
The frame counts differ because the completed UI/animations differ; fewer
absolute jank frames alone do not prove smoother rendering.

In the first v2 trace, the 67.971 ms worst frame occurs during Month paging:
43.499 ms of measure/layout includes 17.931 ms page composition and 11.440 ms
onRemembered work. A 63.986 ms Week-entry frame includes 37.380 ms onMeasure.
These are timestamp-correlated examples, not per-view percentiles. The fixed
Month grid moves child composition into the parent's inclusive slice, so its
parent duration cannot be compared directly with the old lazy-grid wrapper.
Existing remembered indexes already bound work to the visible dates. No further
renderer change had a sufficiently isolated, low-risk benefit to justify it.

### Dense iOS simulator results

Both release executables ran `testPopulatedJourney` on the same iPhone 18 Pro
simulator, iOS 27/Xcode 27, on an Apple Silicon Mac. Three samples per build
follow a discarded warmup, with app state reset before every closure and
launch excluded from measurement. System caches remain warm. Values below
are individual samples in collection order; RSD is relative standard deviation.

| Metric | Original samples | Dense v2 samples | RSD original / v2 |
| --- | --- | --- | ---: |
| Retired instructions, billions | 109.3432, 110.2226, 109.7146 | 91.5008, 91.8701, 91.3174 | 0.328% / 0.251% |
| CPU time, seconds | 13.436, 10.045, 9.866 | 8.579, 8.861, 8.656 | 14.775% / 1.372% |
| Physical memory, kB | 118,868.584, 110,512.744, 109,546.064 | 107,809.360, 100,797.008, 99,797.608 | 3.705% / 3.468% |
| Clock time, seconds | 21.786, 21.184, 21.186 | 21.134, 21.098, 21.100 | 1.325% / 0.078% |

Mean retired instructions fall 16.58% with low variance, and mean physical
memory falls 9.01% (112,975.797 → 102,801.325 kB). Mean CPU time falls 21.74%
(11.115 → 8.698 s), but the original's 14.775% RSD makes that magnitude
uncertain. The more conservative median reduction is 13.83%
(10.045 → 8.656 s); every after sample is below every before sample.
Clock time is dominated by fixed gestures, sleeps and accessibility checks,
so its 1.28% mean reduction is not a direct responsiveness claim. Three
sequential samples do not remove host/order effects or predict physical-device
GPU, battery or thermal behavior.

### Measured artifact identity and remaining evidence

The dense original source is `adbd2c8fcb2cac463556b4942d503b03ecb8cfc0` with the
temporary UI fixture. The v2 artifacts came from the working tree; no exact
final PR commit is asserted for them.

- Original Android APK: `a27cccdd4f664ce1bec91ef4842a6701a2a299471daa8c3e7ed7b219a90f3c95`.
- Dense v2 Android APK: `3d41febc419859866ee242ab5ca6f9825ed4cc90111f343faaf76f8e1faf209a`.
- Original iOS executable: `7e5d5a0e78ca8a477664b49185aef9d47548c03ab9f301c509ace326fd130edb`.
- Dense v2 iOS executable: `a3b4e20e6645a7126d78a5dd46d83b0909903be6e079581eab92ce29bdc2fbfb`.

These measurements precede final event-text contrast and bounded DST/subminute
placement fixes. The fixture uses exact-minute times outside DST folds, so
those boundary fixes do not alter its event geometry; final binaries still
have distinct identities. The final editor trace below uses the application
source committed as `fe3830a`; earlier editor measurements predate the rewritten
form and are not reused as current performance evidence.

Saved local evidence is under `captures/performance/council/`: the accepted
Android groups are `dense/before-populated-{1,2,3}` and
`dense/after-populated-v2-{1,2,3}`, summarized in `dense/comparison-v2.json`.
iOS samples come from `dense/ios-before-metrics.json` and
`dense/ios-after-v2-metrics.json`, with executable verification in each runner's
`app-build.json`. The sanitized committed JSON retains the portable metrics
without device identifiers, absolute paths or raw thermal sensor names.

### Final Android editor trace

Ten editor open/Back-dismiss cycles on the final normal-data diagnostic build produce
2,361 frames, 28 Self Jank frames and 24 app deadline misses; p95 is 12.142 ms,
p99 18.777 ms, and the worst frame 77.365 ms. All ten openings occur inside the
79.864-second capture, and both the outer editor and form-content scopes execute
exactly ten times. The final ten seconds contain no frame, layout, or recomposition
work. Trace health passes; the installed APK SHA-256 is
`20fb20a9e6504c67c568d4b3179d8f44fb2d16612fc1f0f34c70e66d1fbae3f6`.

The worst opening includes 60.326 ms traversal, with 22.466 ms window composition,
9.429 ms measurement and 5.046 ms window relayout. Later openings still contain
71.053/63.905 ms frames. These after-only observations confirm functional coverage
and bounded composition, not an editor speedup or zero-jank behavior. Saved
results are `final/android-editor/` beneath the local council captures.

### iOS trace coverage

A separate app-scoped, 60.765-second Instruments capture of the populated
navigation/month journey exports 10,857 symbolized samples. Sampled weight is
8,229 ms on the main thread and 2,628 ms elsewhere. Month DayCell has 1,264 ms
inclusive weight and MonthView 425 ms; Kotlin concurrent marking appears in
491 ms of leaf samples. These overlapping statistical weights identify review
areas, not exact function calls or Compose recomposition counts. Native release
compilation was active on the host during this qualitative capture, so it is
not used for before/after timing claims or compared with the unprofiled XCTest
samples above.

The first attempt was interrupted when the shared-test command shut down the
simulator; it is excluded. The subsequent isolated UI test completed and the
app-specific `time-profile` table exported successfully. Instruments emitted
its existing auxiliary-table “unknown input source” warning; that does not
supply missing data for the warning table. Saved evidence is
`final/ios-profile-valid/` beneath the local council captures.

## Final validation

The final source passes 80 JVM shared tests, 71 Android target unit tests, and
71 iOS simulator shared tests. The Android tests are host-run unit tests, not
instrumentation. Regression coverage includes real Room/Store failures and
transactions, refresh preservation, editor retries/cancellation, DST boundaries,
sub-minute placement, overlap allocation, shared keys, and event-text contrast.
The benchmark tooling's 15 Python tests also pass. Normal Android release,
iOS simulator release, and desktop builds all pass through the pinned `./kotlin`
wrapper. Application source is committed as `fe3830a`; the reviewed harness is
`436cd18`. The final normal iOS release also passes the launch/month-round-trip
XCTest smoke check.

Device inspection confirms the reported Day/3 Day viewport issue is fixed on
Android and in both directions on iOS. The final Android app creates four
all-day events, retains them after a force-stop/relaunch, scrolls the fourth
into view in its bounded header, opens details, and retains its edited title
through a second restart. This checks both header reachability and persistence.
Android and iOS failed-save checks retain a friendly error beyond the former
snackbar timeout and leave the same draft available for retry. ViewModel tests
separately prove a second write attempt; screenshots alone do not prove it.

The iOS software-keyboard test types a title and scrolls Description fully above
the keyboard while Cancel/Save remain pinned and reachable. Extracted forward
and reverse transition frames show no gray veil strips or timed content behind
bottom navigation. The final event text foreground uses the higher black/white
contrast after compositing its actual background; the final Android screenshot
and common contrast tests cover this change.

Local evidence: `captures/performance/council/final/` contains test/build logs,
Android hierarchies/screenshots, and iOS XCTest bundles and exported screenshots.
The final UI council score is **9/10** and correctness is **9.5/10**, with no
remaining must-fix in these tested flows. Wider device/font-size coverage and clearer discoverability for scrolling
more than three all-day items remain opportunities, not claimed validations.

## Reproduction additions

Follow the toolchain, signing, and device selection instructions in the initial
report. Build the same fixture in each source checkout, retaining each manifest:

```sh
python3 scripts/performance/build_android.py --fixture --tracing --output captures/performance/dense/app.apk
python3 scripts/performance/android_benchmark.py --serial "$ANDROID_SERIAL" --scenario populated --iterations 1 --compose-tracing --apk captures/performance/dense/app.apk --fixture-manifest captures/performance/dense/app-fixture/fixture-manifest.json --recorder "$PERFETTO_RECORDER" --output captures/performance/dense/after-populated-1
python3 scripts/performance/analyze_android.py trace --processor "$TRACE_PROCESSOR" --trace captures/performance/dense/after-populated-1/trace.perfetto-trace --output captures/performance/dense/after-populated-1/analysis
python3 scripts/performance/analyze_android.py compare --root captures/performance/dense --scenario populated --runs 3
```

Sign/install the generated APK before collecting a run. Repeat for indices 1–3
under both `before-populated-*` and `after-populated-*`. The collector reports
missing/incomplete/rejected runs and checks recorded fixture/configuration
consistency; it summarizes per-run percentiles rather than pooling frames.

For iOS, wrap the pinned application build, then install that exact output:

```sh
python3 scripts/performance/fixture.py --output captures/performance/dense/ios-fixture -- ./kotlin build -m iosApp -p iosSimulatorArm64 -v release
python3 scripts/performance/ios_benchmark.py --device "$IOS_SIMULATOR" --expected-app "$RELEASE_APP" --output captures/performance/dense/ios-populated --only testPopulatedJourney
```

Run iOS measurements after application compilers finish and retain the host
conditions. `--expected-app` verifies the installed executable's SHA-256.
`testNavigationViewport` exercises Day/3 Day transitions after scrolling.
`testEventDialog` requires visible software keys (enable the software keyboard
in Device Hub). The opt-in `testSaveFailureRetainsDraft` uses the fixture's
nonexistent database calendar to exercise a real failed write and retry; it is
not part of the default suite or a write-performance measurement.

The benchmark runner is an XCTest-only project under ignored captures, not a
second application build system. Physical iOS device performance, production
server synchronization, and deletion tombstones remain outside these results.
