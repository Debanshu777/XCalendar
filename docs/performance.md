# Android and iOS performance review

The initial measurements below compare the release app at `adbd2c8` with
application commit `3956546`. The subsequent
[council review and populated-calendar measurements](performance-council.md)
cover the additional navigation, editor, and persistence fixes in this PR.
It covers cold-process launch, all five calendar views, month paging, schedule
scrolling, the month dropdown, idle composition, and the event editor.
Measurements are observations on the devices below, not universal performance
budgets or a claim that every possible workload is optimized.

## Environment and interpretation

- Android: physical Pixel 9, Android 17; default refresh-rate and power settings.
  The benchmark uses `com.debanshu.xcalendar.benchmark`, preserving the installed
  shipping app and its data. The APK is a release build signed locally for testing.
- iOS: iPhone 18 Pro simulator, iOS 27, Xcode 27, on an Apple Silicon Mac.
  Simulator results depend on the host and do not predict physical-device GPU,
  thermal, battery, or memory behavior.
- Kotlin Toolchain 0.13.0, Kotlin 2.4.20, Compose 1.12.1; no toolchain upgrade.
- Calendar dates are November 2026. The demo API's older events leave these views
  empty/sparse. These runs measure layout/navigation, not a dense production
  calendar or backend throughput. Interval and overlap regression tests cover
  populated data separately.
- Android startup is `am start -S -W` TotalTime (first display), ten cold **process**
  starts with caches retained. iOS uses ten `XCTApplicationLaunchMetric` samples
  with `waitUntilResponsive: true`. Those metrics are not interchangeable.
- iOS journey: Week → 3 Day → Day → Schedule → Month, then two opposite drags
  covering 70% of screen width with 0.5 s settling pauses, five measured iterations.
  Clock time includes those pauses and XCTest interaction/idle waits;
  CPU time is app-specific. Screenshots remain outside measurement; UI-target assertions execute during the
  journey. Separate functional tests check the editor and a month round trip.
- Startup and XCTest headline measurements are unprofiled. Android diagnostic
  traces include Compose runtime tracing in both builds; instrumentation adds
  overhead. Compare like-for-like runs, not traced versus untraced timings.

## Results

The [machine-readable results](performance-results.json) retain individual
startup/XCTest samples and the accepted Android capture locations.

| Android diagnostic workload | Baseline | After | Interpretation |
| --- | ---: | ---: | --- |
| Month frame p99 | 51.55 ms | 35.48 ms | 31.2% lower |
| Month Self Jank | 53/866 (6.12%) | 33/904 (3.65%) | 2.47 percentage points lower |
| Month app deadline misses | 25/866 (2.89%) | 6/904 (0.66%) | About 77% lower rate |
| Month lazy prefetch measurement | 338.73 ms | 23.57 ms | 93.0% less cumulative work |
| Navigation frame p95 | 15.85 ms | 12.07 ms | 23.8% lower |
| Navigation frame p99 | 27.34 ms | 23.30 ms | 14.8% lower |
| Navigation Self Jank | 19/1,033 (1.84%) | 15/822 (1.82%) | Essentially unchanged |
| Navigation parent executions | 1,033 | 30 | 97.1% fewer |
| Schedule frame p95 / p99 | 8.15 / 11.92 ms | 8.20 / 12.46 ms | Neutral/slightly slower tail |
| Schedule Self Jank | 7/1,057 (0.66%) | 7/1,055 (0.66%) | Unchanged |
| Dropdown Self Jank | 37/548 (6.75%) | 4/549 (0.73%) | 6.02 percentage points lower |
| Dropdown frame p99 | 27.24 ms | 22.13 ms | 18.8% lower |
| Dropdown month-cell executions | 19,236 | 0 | Cells skip during height animation |

These are Perfetto FrameTimeline durations and tags, not `gfxinfo`'s separate
janky-frame definition. The month worst frame remains 89.71 ms, so long frames
are not eliminated. Month RenderThread CPU rose 2.4% while main-thread CPU fell
16.4%; the changes primarily reduce composition and layout work. Dropdown's
worst individual frame increased from 40.95 to 56.80 ms despite its improved
percentiles; its RenderThread CPU rose 6.4%. Five successful editor open/close
cycles were traced after the fix (p99 15.65 ms, worst 69.87 ms across app/dialog
surfaces). The baseline editor did not open, so no speedup is claimed for it.

Unprofiled Android cold-process startup median improved from **268.5 to 230 ms**
(14.3% lower; ten starts each). Baseline range: 258–301 ms; after: 218–251 ms.
This measures first display, not completion of network refresh or full usability.

| iOS simulator final journey, five iterations | Baseline mean | After mean | Interpretation |
| --- | ---: | ---: | --- |
| App CPU time | 2.189 s | 1.881 s | 14.1% lower |
| Absolute physical memory | 118,082 kB | 96,996 kB | 17.9% lower |
| Peak physical memory | 119,665 kB | 98,831 kB | 17.4% lower |
| Interaction clock time | 9.696 s | 9.751 s | Essentially unchanged (+0.6%) |

CPU relative standard deviation is 3.50% before and 3.47% after; clock variation
is 0.58% and 0.63%. Memory is measured during this short journey and does not
establish whether a leak exists. The improvements describe this simulator,
release pair, and workload; physical iOS device performance remains unmeasured.

iOS responsive-launch mean was 1.987 s before. The three after runs measured
2.053, 1.894, and 1.936 s (ten launches each). That variation does not demonstrate
a consistent launch improvement; all samples are retained rather than choosing
the fastest run.

Earlier iOS exploratory journeys used XCTest's default slow swipes, which could
snap back without changing months. The final paired journey uses identical,
explicit screen coordinates on both releases. Exploratory samples remain in
the JSON but are excluded from the final journey comparison. The original
baseline also dropped the pager modifier/test identifier; a failed attempt to
target that identifier produced no usable measurements.

## Trace findings and changes

The validated baseline Android month trace has 866 frames, 53 Self Jank frames
(6.12%), 25 app deadline misses, a p99 of 51.55 ms, and a worst frame of 107.92 ms.
That frame spends 75.52 ms in `measureAndLayout`, including 71.06 ms on CPU.
Ten lazy prefetch measurements consume 338.73 ms; 855 deactivations consume
365.96 ms. This is CPU/layout work, not a long network or binder wait.

The month view has only 42 visible dates. Six fixed rows remove nested lazy
subcomposition/prefetch and the over-height last row. Date/event indexing runs
once per visible range instead of filtering the full event list per cell. The
hour-grid background is drawn in one Canvas per day instead of 24 measured
boxes. Bottom space keeps the navigation bar from covering calendar content.

In the baseline navigation trace, `CalendarBottomNavigationBar` executes 1,033
times for ten selections (295.31 ms inclusive), and `rememberGlassEffect` also
executes 1,033 times. Animation reads now belong to a smaller `NavBarSurface`
composition scope, while layer-only transforms remain in `graphicsLayer`.
The validated after trace reduces parent executions to 30 (−97.1%), with
223 executions of the smaller surface. Main-thread running CPU falls from
3,998.82 to 2,516.25 ms (−37.1%). Navigation p95 falls from 15.85 to 12.07 ms;
Self Jank remains approximately 1.8%, so this is not a jank-rate collapse.

Counts include initial composition; they are **not** exact recomposition-only
counts. Nested inclusive slice durations must not be added together.

Instruments Time Profiler resolves Kotlin/Native Compose symbols and identifies
month `DayCell` composition and Compose layout/rendering in the simulator
journey. Its samples are statistical CPU evidence, not per-composable execution
counts. Android Perfetto supplies the named composition slices. The accepted
Android baselines showed no continuing recomposition after the UI became idle.
The after captures also contain no composition after settling, over observed
idle tails of 24.8–41.9 seconds.

The final app-scoped, 60 s Instruments capture exports 5,778 symbolized samples.
Main-thread sampled weight is 4,136 ms, including 718 ms in `DayCell` and
1,591 ms in Compose's Metal redraw path (inclusive, overlapping stacks).
Month-cell composition, rendering, and Kotlin/Native GC remain visible costs.
The earlier baseline profile used the exploratory gestures, so its total sample
weights are not a matched timing comparison. Instruments warned that one table
had no known input source; the time-profile table exported successfully with
resolved app symbols. These samples support qualitative hotspot analysis only.

Initial Android traces were rejected for producer packet loss. Validated runs
use a 128 MiB service buffer (256 MiB for dropdown animations), periodic incremental-state clearing, only the
`rendering` SDK category, the benchmark app's producer, and an 8 MiB producer
shared-memory request. Check trace health before interpreting any counts.
The accepted baseline navigation trace predates the app-only producer filter
and shared-memory preference; both navigation traces contain complete app data,
but that configuration difference limits this comparison. Month, schedule, and
dropdown pairs use matching producer settings. Each diagnostic workload has one
accepted capture per build; small differences are not statistically conclusive.

## Correctness review

- Refresh failures now surface an error instead of filtering out every error
  response and suspending forever waiting for data.
- Cancellation propagates through repositories, network calls, Store
  bookkeeping, and event use cases rather than becoming an ordinary failure.
- Empty event/holiday streams no longer prevent cached accounts and calendars
  from reaching the UI; Add Event can open while remote data is unavailable.
- Removed duplicate perpetual holiday-refresh collectors during initialization.
  Holidays on the same date retain their separate identities.
- Date-range SQL includes events crossing either boundary, with exclusive end
  boundaries. Month, day/week, dropdown, and schedule indexing include overnight
  events, excluding the day of a midnight end.
- Overlapping events receive separate columns, adjacent events reuse full width,
  and time-grid intervals clip to the visible day/hours.
- Single-event reads retain reminders and observe database updates. Fetched
  batches are written in one transaction. Room schema version 1 is unchanged.
- Pager callbacks read current state; external selections that do not align with
  a multi-day stride rebase the pager. Today refreshes the current date, and new
  events use the selected date. The ten-swipe Android baseline finishes with a
  stale December header; the fixed run correctly returns to November.
- Schedule starts at its intended month without first triggering backwards
  pagination. Cancelled navigation drags return to their resting state.
- iOS initializes Koin once outside composition/controller recreation. Top-level
  UI flow collection follows the lifecycle.

The subsequent council review also fixes persistence and editor defects:

- Account refresh updates the existing user through Room `@Upsert`. It preserves
  child calendars, locally created events, and reminders across app restarts;
  the previous replacement deleted them through foreign-key cascades.
- Event add/update checks Store's write response. Database errors reach the
  editor, and cancellation retains its original exception. A failed local write
  can no longer be reported as a successful save.
- The JSON event endpoint now has an explicit demo-seed policy: import missing
  IDs and their reminders in one transaction, preserving existing local edits.
  Later imports accept new IDs; changes to an existing ID in the source asset
  intentionally do not replace the local record. A failed import rolls back
  its new rows. Local editing retains the existing upsert path, and Room schema
  version 1 remains unchanged.
- Edit opens a populated form. Save dismisses only after persistence succeeds;
  errors retain the draft and its event ID for retry. Submission is guarded
  before launching a coroutine, preventing duplicate rapid taps. Cancel leaves
  the original event unchanged, and dismissal is disabled while saving.
- The editor has scrollable fields and reachable actions, controlled calendar
  selection for the displayed account, editable dates/times/location/description,
  and reminder choices. Existing recurrence metadata and multiple reminders are
  preserved; an empty reminder list differs from an at-start reminder.
- Existing timed events retain exact instants when their date/time fields are
  unchanged, including sub-minute precision and repeated daylight-saving hours.
  Changed inputs reject invalid intervals and nonexistent local times. All-day
  dates store an exclusive next-local-midnight end, including 23/25-hour days.
  The timed grid also keeps sub-minute and repeated-hour intervals visible.
  A fixed 24-hour axis projects reversed clock times using elapsed duration;
  details retain the original instants, and midnight ends remain exclusive.
  Details show both dates for overnight/multiday events and no longer invent
  meeting, guest-list, or default-reminder information.

### Council correctness validation

Regression coverage exercises account refresh with existing child data, real
Room/Store write failures reaching create/update use cases, failed-write rollback,
repeated demo imports preserving edits/reminders, and new seed IDs. ViewModel
tests cover duplicate submissions, persistence completion, failure/retry,
cancellation, and selection changes while saving. Editor/formatter tests cover
exclusive all-day ends, daylight-saving transitions, exact timestamp preservation,
overnight dates, year boundaries, and zero-minute reminders. Final executed test
counts and device checks are recorded in the [council validation](performance-council.md#final-validation).

The static JSON endpoint is read-only: there is no remote write synchronization
or conflict-resolution protocol. Repository-level deletion of an imported event
can allow that ID to be seeded again because no durable deletion tombstone is
stored; the app currently exposes no Delete control. The seed policy is specific
to the demo backend and must be replaced with a defined synchronization policy
before supporting a write-capable server.

Search, recurrence expansion, and live clock/time-zone changes remain separate
product work. Draft restoration after process death is not claimed. The initial
event query remains bounded around today, so browsing far beyond that window is
not a complete historical-data experience.

## Reproduce Android

Use the installed Google Android CLI and the same SDK as `./kotlin`. Select a
serial from `adb devices -l`; never commit it. Keep the phone unlocked and idle,
and record power/thermal conditions. Avoid changing device settings between runs.

```sh
python3 scripts/performance/build_android.py --output captures/performance/run/app-unsigned.apk
# Sign with a local test keystore using the SDK's apksigner.
apksigner sign --ks "$BENCHMARK_KEYSTORE" --out captures/performance/run/app.apk captures/performance/run/app-unsigned.apk
android install --device="$ANDROID_SERIAL" --apks=captures/performance/run/app.apk
python3 scripts/performance/android_benchmark.py --serial "$ANDROID_SERIAL" --scenario startup --iterations 10 --output captures/performance/run/startup
```

For diagnostic runs, rebuild with `--tracing`, sign/install again, then run each
workload sequentially. `--recorder` points to the official Perfetto
`record_android_trace` helper. The script waits for recording to finish before
returning, so the next workload cannot overlap the trace's idle tail.

```sh
python3 scripts/performance/build_android.py --tracing --output captures/performance/run/tracing-unsigned.apk
# Sign/install as above, then repeat for month, schedule, dropdown, and dialog.
python3 scripts/performance/android_benchmark.py --serial "$ANDROID_SERIAL" --scenario navigation --iterations 10 --compose-tracing --recorder "$PERFETTO_RECORDER" --output captures/performance/run/navigation
python3 scripts/performance/analyze_android.py trace --processor "$TRACE_PROCESSOR" --trace captures/performance/run/navigation/trace.perfetto-trace --output captures/performance/run/navigation/analysis
```

`build_android.py` temporarily changes only the application ID/profileable
manifest and optional diagnostic dependencies, restoring the original inputs
in `finally`. Do not run other builds while it runs. Shipping builds do not
include tracing binaries. Use distinct output directories for each run.

The runner records frame stats, memory snapshots, battery/thermal metadata,
accessibility hierarchy, and traces. Check screenshots and app-scoped crash logs
as well: command success alone does not verify the UI. Memory snapshots from
short runs do not establish whether a leak exists.

## Reproduce iOS

Build the app with the pinned wrapper, install it on a booted simulator, then
run the standalone XCTest harness. Its generated Xcode project builds only the
test runner; the app continues to use the Kotlin Toolchain.

```sh
./kotlin build -m iosApp -p iosSimulatorArm64 -v release
xcrun simctl install "$IOS_SIMULATOR" build/tasks/_iosApp_buildIosAppIosSimulatorArm64Release/bin/Release-iphonesimulator/XCalendar.app
python3 scripts/performance/ios_benchmark.py --device "$IOS_SIMULATOR" --expected-app build/tasks/_iosApp_buildIosAppIosSimulatorArm64Release/bin/Release-iphonesimulator/XCalendar.app --output captures/performance/run/ios-tests
python3 scripts/performance/ios_benchmark.py --device "$IOS_SIMULATOR" --only testProfileJourney --trace --output captures/performance/run/ios-profile
xcrun xctrace export --input captures/performance/run/ios-profile/cpu.trace --xpath '/trace-toc/run[@number="1"]/data/table[@schema="time-profile"]' --output captures/performance/run/ios-samples.xml
python3 scripts/performance/analyze_ios_samples.py captures/performance/run/ios-samples.xml
```

`--expected-app` verifies the installed executable against the chosen release
and records its SHA-256. Boot the simulator before installing; a failed install
must not be followed by a benchmark against a stale app.

The diagnostic test pauses after launching the app, allowing Time Profiler to
attach to `XCalendar` before the journey starts. It is excluded from ordinary
measurement runs. Keep XCTest results and screenshots under ignored `captures/`.
Run simulator measurements after compilation and other CPU-heavy work finishes.

For baseline comparisons, use an isolated checkout at `adbd2c8` with the same
benchmark scripts, SDK, test signing key, data, and device. Do not mix the normal
and diagnostic APKs in a timing comparison or clear only one build's caches.

## Validation and evidence

The following checks belong to the initial measurement revision. The expanded
[final council validation](performance-council.md#final-validation) records the
current test counts, editor/persistence checks, and platform builds.

Commands completed successfully for the initial revision:

- `./kotlin test -m shared -p jvm`: 45/45 tests.
- `./kotlin test -m shared -p android`: 40/40 target unit tests (not device instrumentation).
- `./kotlin test -m shared -p iosSimulatorArm64`: 40/40 tests.
- `./kotlin build -m androidApp -p android -v release`.
- `./kotlin build -m iosApp -p iosSimulatorArm64 -v release`.
- `./kotlin build -m desktopApp -p jvm`.

The Room tests cover reminder updates and atomic rollback as well as range
boundaries. On Android, all five views, dropdown/editor, event creation, two
overlapping events and event details were inspected through live UI hierarchy
and screenshots; app-scoped crash checks passed. The final iOS XCTest suite
passes 4/4: launch, five-view journey, editor open/cancel, and month round trip.
The earlier harness failures (text-field type and insufficient swipe distance)
were resolved without changing the application binary. No physical iOS performance
measurement or iOS device build was performed.

Raw traces, XCTest result bundles, screenshots, build logs, and query outputs
are local under ignored `captures/performance/`; they can contain device or UI
information and are not committed. The repository includes the workload scripts
and SQL so measurements can be repeated.

Primary references:
[Compose tracing](https://developer.android.com/develop/ui/compose/tooling/tracing),
[Compose performance](https://developer.android.com/codelabs/jetpack-compose-performance),
[Perfetto Android recording](https://perfetto.dev/docs/quickstart/android-tracing),
[Perfetto trace analysis](https://perfetto.dev/docs/quickstart/trace-analysis),
[XCTest launch metric](https://developer.apple.com/documentation/xctest/xctapplicationlaunchmetric).
