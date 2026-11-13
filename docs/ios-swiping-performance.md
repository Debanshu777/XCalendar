# iOS day/month swipe investigation — 10 November 2026

This review targets swiping after Google Calendar sync. It preserves the existing
Google integration work and does not change the toolchain, schema, or signing.

## Findings and fixes

1. **Every new page converted timestamps for the entire loaded event list.**
   `ui/utils/CalendarDates.kt:indexEventsByDate` converted both endpoints before
   checking whether an event touched the page. With 1,800 loaded events, opening
   one day performed 3,600 time-zone conversions even if only 20 events belonged
   to that day. It now computes the window's local-midnight boundaries once,
   rejects unrelated events using integer timestamps, and clips long events
   before conversion. Exclusive ends, overnight events, and daylight-saving
   boundaries retain their semantics.

2. **Google data preparation ran in composition/on the main coroutine.**
   `CalendarApp.kt` parsed every remote event into its UI model inside `remember`.
   A successful refresh changes `syncedAt`, invalidating that cache even when
   the events are unchanged. `GoogleCalendarRepository` also encoded/decoded
   the entire JSON cache on its caller's context (the ViewModel's main context).
   The new `ui/google/GoogleCalendarContent.kt` flow prepares immutable UI data
   on `Dispatchers.Default` and deduplicates by profile, calendar data, and display
   time zone. Cache serialization also uses `Dispatchers.Default`. Authentication
   and editor callbacks retain their existing context. Those initial changes
   left navigation fetch policy unchanged; the November 12 follow-up below
   addresses redundant fetches and stale window publication.

3. **Month pages had expensive per-cell shared-animation registrations.**
   Six-week grids overlap, so the initial implementation also registered adjacent
   copies with identical shared keys. The first comparison below limits those
   registrations to dates owned by each page's month. The final implementation
   removes the month grid's per-date/per-event shared modifiers entirely: creating
   and disposing them still caused long layout/lifecycle slices. Month navigation
   keeps its scene fade, while Day/Week shared transitions are unchanged. See the
   final Android isolation experiment below. Compose's
   [shared-element guidance](https://developer.android.com/develop/ui/compose/animation/shared-elements#unique-keys)
   explains the original duplicate-identity issue.

4. **Month cells repeated date and shape setup.**
   The current date/time zone is resolved once per month page instead of once
   per cell. The decorative cookie shape is used only for today's highlighted
   cell, and cell corner shapes are remembered.

5. **Build variant matters on iOS.**
   The latest physical-device output found during inspection was under
   `Debug-iphoneos`; the checked-in Run scheme and wrapper also default to Debug.
   That does not establish which binary is currently on the phone.
   Kotlin/Native [release builds enable substantial runtime optimizations](https://kotlinlang.org/docs/native-improving-compilation-time.html#don-t-build-unnecessary-release-binaries).
   Performance acceptance should use a release app without an attached debugger.

## Reproduce

Use the pinned wrapper. Choose the target from the live device list; do not commit
its identifier:

```sh
xcrun devicectl list devices
./kotlin run -m iosApp -p iosArm64 -v release --device-id <connected-device>
```

The historical indexing comparison used a temporary native microbenchmark with
1,800 synthetic events. That investigation-only harness has been retired; its
results remain below. Use the UI workload for ongoing performance checks.

For the UI workload, install the intended simulator release and use the existing
XCTest harness with `--only testPagingJourney`. Each iteration performs two month
round trips and one day round trip. `testMonthRoundTrip` separately checks that
the month header advances and returns. XCTest clock time includes gesture/idle
waits and fixed pauses; app CPU time is not a dropped-frame or hitch metric.

## Validation

The release paging comparison used one run per build with three measured
iterations (plus XCTest warmup). Installed executable hashes were checked against
the preserved baseline and updated `.app` bundles. Raw samples and hashes are in
[`ios-swiping-results.json`](ios-swiping-results.json).

| Release paging metric | Before mean | After mean |
| --- | ---: | ---: |
| App CPU time | 3.935 s | 2.932 s |
| XCTest interaction clock time | 11.543 s | 11.572 s |

App CPU time fell **25.5%** in this workload; interaction clock time was effectively
unchanged. This is a reduction in work, not evidence of a 25.5% frame-rate increase.
The baseline ran first, then the updated app, without interleaved repetitions.
The comparison combines indexing, month-cell, and animation-identity changes;
it does not isolate their individual effects.

The release pair predates concurrent multi-account edits in the working tree.
Those edits retain the background projection and serialization changes; final
compatibility checks are performed separately against the updated sources.

The accepted native indexing microbenchmark ran after compilation finished,
using the Debug test executable. These are median totals for **20 page ranges**,
five alternating samples each, with 1,800 synthetic events:

| Page size | Previous index | Updated index |
| --- | ---: | ---: |
| 1 day | 83.973 ms | 3.634 ms |
| 7 days | 87.996 ms | 11.475 ms |
| 42-day month grid | 98.895 ms | 56.077 ms |

Output equality was checked for every range. These isolate the index function,
not whole-page rendering or release-device performance. The earlier run that
overlapped compilation is retained as exploratory evidence and excluded here.

The working tree's baseline release and updated release use the same pinned
compiler/dependencies. Simulator testing uses a separate iPhone 18 Pro on iOS 27,
with 900 synthetic local events across October–December 2026. No Google account
or physical-iPhone data is used for this workload. Local simulator fixtures test
rendering/indexing, not live Google API or cache throughput.

Regression checks cover daylight-saving boundaries, exclusive midnight ends,
long/overnight events, metadata-only refreshes, edits, read-only calendars, and
sign-out. Physical-iPhone frame pacing still requires a release run with the
actual synced account; simulator measurements cannot establish that all jitter
is eliminated.

Final checks against the concurrent account updates:

- `./kotlin test -m shared -p jvm`: 102 passed.
- `./kotlin test -m shared -p android`: 90 passed.
- `./kotlin test -m shared -p iosSimulatorArm64`: 91 passed, including the
  temporarily installed opt-in benchmark; its source copy was removed afterwards.
- Final current-source builds passed: `./kotlin build -m androidApp -p android`,
  `./kotlin build -m desktopApp -p jvm`, and
  `./kotlin build -m iosApp -p iosSimulatorArm64` (Debug). Both release simulator
  builds used for the paired measurements also passed with `-v release`.
- Release XCTest checks: `testMonthRoundTrip`, `testPagingJourney`, and
  `testPopulatedJourney` passed. The populated journey checks that timed events
  and the 9 am label actually enter the viewport in Day/3 Day/Week, and that
  Schedule shows populated rows.
- Visually inspected `captures/performance/swipe-review/after-month.png`: all
  six weeks, today's highlight, event summaries, and navigation are visible.
- `git diff --check` passed. Existing compiler warnings about experimental
  Store/Koin APIs and Swift header imports remain.

Artifacts, raw XCTest logs/result bundles, binary fingerprints, and native
benchmark logs are under ignored `captures/performance/swipe-review/`.
No app was installed on the physical iPhone during that initial simulator
investigation. The subsequent device follow-up below addresses the user's
continued report of an unusable month view.

## Follow-up on the connected iPhone

The iPhone 13 on iOS 27.0.1 was running **Debug**, now verified rather than
inferred from build folders. An Instruments launch recording contains
`XCalendar.debug.dylib`, and the app executable UUID
`7579B511-FA6C-366F-AD07-5A43C55B6957` matches the local
`_iosApp_buildIosAppIosArm64Debug` artifact. Kotlin/Native runtime and garbage
collection work appear prominently in the sampled stacks. The Debug build is
not a valid proxy for Release responsiveness.

The recording also exposes a remaining main-thread sync path:
`GoogleCalendarApi.events` has 77 ms of inclusive main-thread CPU samples,
including 57 ms under `GoogleEventPage` deserialization. Ktor's suspend network
request did not make the subsequent `body()` conversion background work.
`GoogleCalendarApi` now decodes response bodies inside
`withContext(Dispatchers.Default)`. Token/auth callbacks and UI completions keep
their caller context. This extends the earlier cache/projection fixes to live
Google responses. It does not change sync frequency or calendar permissions.

These samples are diagnostic evidence, not a measured swipe-speed improvement.
The 58.771-second recording includes startup and has no verified gesture count;
most CPU samples occur near startup. Its 388.809 ms microhang begins 2.940 seconds
after recording starts and must not be presented as a measured month-swipe hitch.
Attaching to the existing process failed; recording through Instruments launch
succeeded. Raw traces, exports, and build logs are under ignored
`captures/performance/iphone-month-stutter/`.

The Release app was built through
`./kotlin build -m iosApp -p iosArm64 -v release`, signed with the existing local
XCalendar development profile, and installed as an update to the same bundle
identifier. No user data reset or Google sign-out was performed. The user reported
that this Release was **still unusable**. A second recording verified the installed
Release executable UUID against the signed artifact. It sampled event-page
deserialization on a background thread (10 ms), with none sampled on Main; the
earlier Debug capture sampled 57 ms on Main. These are different-duration launch
captures with compiler differences, not a decoding speedup comparison. Release
mode and background decoding were therefore not treated as a sufficient fix.

The regression `GoogleCalendarDecodingTest` verifies both paginated responses
decode away from the caller dispatcher, both token callbacks stay on that
dispatcher, and the result returns there. Validation after this change:

- Shared JVM: 119 tests passed.
- Shared Android: 99 tests passed.
- Shared iOS simulator: 100 passing test entries; the existing Keychain
  round-trip check exits early because the runner lacks its access entitlement.
  The new decoding/context test executed and passed.
- Android, desktop, and iPhone Release builds passed.

The [Android follow-up](android-swiping-performance.md) provides three repeated
before/after runs for each of month/day paging. Month p99 improves from 86.100 to
46.304 ms, but long layout/disposal frames remain. This is evidence of partial
improvement on Android, not proof that the reported iPhone stutter is resolved.

## Final month-rendering change

`DayCell` no longer applies `sharedDateElement` or `sharedEventElement`. The
unused visibility argument is removed through `MonthView`, `MonthScreen`, and
its navigation entry. This removes per-cell lookahead/animation lifecycle work
when a 42-cell page enters or leaves the pager. Date taps, event summaries,
cell scrolling, and the navigation scene fade remain. The visual tradeoff is
that individual month dates/events no longer morph into the Day view.

A separate Android comparison isolates this change against the previously
updated build, with the same 900-event fixture and three interleaved runs each:

| Month metric, median of three runs | Previous updated build | Final rendering change |
| --- | ---: | ---: |
| p99 frame duration | 49.395 ms | 23.768 ms |
| Worst frame per run | 66.058 ms | 34.120 ms |
| Self Jank | 4.44% | 2.38% |
| Main-thread CPU per 30-second trace | 2.318 s | 1.887 s |

This further improves p99 by 51.9%; it still leaves occasional long frames.
The final iPhone Release was signed, installed over XCalendar, and launched
without a debugger for a new user check. That check remains pending unless
explicitly recorded as accepted; Android results do not establish iOS acceptance.

After the rendering change, Android, desktop, iPhone Release, and iOS simulator
builds passed. The simulator `testMonthRoundTrip` passed. The Android final
month layout was visually inspected. The opt-in `testProfileMonthPaging`
automates six header-verified swipes while activating an app already launched
by Instruments, so profiling can stay on one PID. Its device runner built,
but installation failed because Apple's free development profile already has
three apps installed. The attempted device run verified zero swipes and is
excluded from performance results. No other app was removed to free a slot.

## Loading follow-up — 12 November 2026

The user confirmed the month-rendering build was improved but still stuttered,
and described events loading late and jumping around. A new manual iPhone trace
was captured under `captures/performance/iphone-month-followup/`. The code review
found that every selected-month change forced a sync even when data was cached.
The three-calendar-month window also omitted some overflow days in neighboring
six-week grids. A response for a month the user had already left could replace
the currently visible snapshot.

Navigation now checks the complete 42-day grid against every account's cached
range. Fetch windows include the full previous, current, and next month grids.
Cached navigation therefore avoids a network refresh, while explicit refresh,
startup refresh, and the periodic refresh remain active. A month change during
an operation queues a coverage recheck for the latest selection; it does not
queue a fetch for every intermediate month.

Sync publication checks that its requested month is still selected, both before
commit and after suspending cache work. Obsolete data may remain a valid stored
cache window but cannot replace the visible snapshot. Event responses are
ordered by ID, and date indexing breaks equal-start-time ties by ID, so response
order changes do not reshuffle equal-time event summaries.

This is a loading-policy change, separate from the earlier rendering benchmarks.
No frame-time improvement is claimed for it without a new device comparison.

Validation for this follow-up: `./kotlin test -m shared -p jvm` passed 125 tests;
`./kotlin test -m shared -p android` passed 103 tests; and
`./kotlin test -m shared -p iosSimulatorArm64` completed with 104 passing entries
(the existing Keychain round-trip returns early because the simulator test runner
lacks the required entitlement). Android and desktop builds passed. The JVM
integration tests exercise actual repository and ViewModel behavior: cached
neighbor navigation adds no fetch, leaving coverage fetches, manual refresh
still fetches, and navigation during cache persistence cannot publish stale
results. Raw validation logs are under `captures/performance/loading-followup/`.

The physical-iPhone Release build also passed (`./kotlin build -m iosApp -p iosArm64 -v release`), was code-signature verified, installed as an update, and
launched successfully. The executable SHA-256 is
`a1135a5f33902f610931941f2058b3ac69c6f94b50cb2f535fb92f99750d561b`.
Post-update user confirmation and frame-time measurement remain pending.


## Expanded cache window — 12 November 2026

The user confirmed the loading update stopped events jumping around, but navigating
from December to August and back evicted December from the small window. The sync
window now spans one year before and after the selected month, including complete
boundary month grids. December 2026 therefore covers December 2025 through
December 2027. This remains a bounded, persisted per-account snapshot; navigating
outside it fetches a new window. Explicit, startup, and periodic refresh behavior
is unchanged, so the reduction applies to navigation-triggered fetches. Initial
syncs and regular refreshes can transfer more events and require more API pages.

The ViewModel integration test now checks December → August → December and both
year boundaries without another fetch, then checks fetching outside coverage and
manual refresh. Coverage tests also verify the first month outside each boundary.

A thin indeterminate loading strip now sits below the app bar, above the calendar
weekday header. Its 2 dp slot remains reserved while idle, so starting or ending
sync does not move the calendar. It reflects Google operations other than event
saving; cached navigation does not start the indicator by itself.

Validation: 125 JVM tests and 103 Android tests passed; the iOS simulator suite
completed with 104 passing entries (the existing Keychain check returns early
without its entitlement). Android, desktop, and physical-iPhone Release builds
passed. The iPhone update installed and launched, and the user reported “looks
better in ios”. Its executable SHA-256 is
`4b04d48587ca00f0636cca728a22502894236db5808d6c79112c36760a0425fb`.
An isolated Android preview with the loader forced visible was built and its
month layout inspected; the shipping loading condition was restored afterward.
The normal Android app could not be updated with the local debug signature and
was left intact. Logs and captures are under `captures/performance/year-cache/`.


## Restart cache policy — 12 November 2026

The user reported another fetch after killing and reopening the app. Repository
restore already read persisted snapshots, but the ViewModel then unconditionally
synced, and the foreground timer forced another sync every minute. Startup and
timer checks now reuse complete cached coverage when every account's successful
sync is less than 15 minutes old. Manual refresh bypasses freshness. Missing,
expired, or future timestamps require refresh; stale cached events remain visible
while it runs. This changes the earlier startup/periodic policy described above.

The regression test closes and reopens a file-backed Room database and recreates
the repository and ViewModel, asserting identical restored snapshots and no extra
fetch. It also checks automatic refresh reuse, navigation coverage, and forced
manual refresh. Freshness boundaries are covered by common tests.

Validation: all 126 JVM tests passed, including the file-backed reopen regression.
Android, desktop, and iPhone Release builds passed. The signed Release was
installed as an update; manual restart feedback remains pending. Executable
SHA-256: `0652ad7d8d66c2891972357e60c869ef358a2d4efd65d7dd096dcef9c4197e49`.
Logs are under `captures/performance/restart-cache/`. An initial build raced
concurrent ViewModel edits; final checks ran after that task was paused, with its
changes preserved.
