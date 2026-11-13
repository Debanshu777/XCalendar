# Android, iOS, and macOS performance readiness — 13 November 2026

<!-- MONTH_CELL_COMBINED_START -->
## Rejected diagnostic: combined geometry and semantics (V4)

Decision: do not promote V4 or run the planned forty-capture confirmation with
this unchanged candidate. V4 combines V2's shared geometry with V3's consolidated
day semantics. Its patch base is V3, but the benchmark baseline remains accepted
V1. Only six diagnostic captures completed: **one pair per workload**. These
results screen the hypothesis; they do not establish a stable effect size or
constitute a forty-capture acceptance result.

Results: [month-cell-combined-diagnostic-results.json](month-cell-combined-diagnostic-results.json).
All six trace hashes and raw frame p95/p99 summaries were independently checked;
the working candidate matches the frozen source hashes.

| Profile | Window main CPU V1 → V4 | Full-trace p95 V1 → V4 | Full-trace p99 V1 → V4 | Late frames V1 → V4 |
| --- | ---: | ---: | ---: | ---: |
| Alternating months | 1,689.542 → 1,369.653 ms | 12.836 → 12.701 ms | 26.874 → 20.585 ms | 4.331% → 2.178% |
| Forward months | 1,700.827 → 1,310.036 ms | 12.922 → 13.701 ms | 20.802 → 22.372 ms | 3.781% → 4.537% |
| Loading and refresh | 2,766.274 → 2,309.756 ms | 13.690 → 13.912 ms | 22.843 → 23.258 ms | 5.310% → 3.883% |

CPU savings persist, but forward p95 worsens approximately 6.0% and its late
rate rises 0.756 percentage points. The loading maximum rises from 31.901 to
**62.060 ms**, inside the command-plus-settling interaction window. The hypothesis
that combining these changes removes the loading stall is therefore not supported
by this diagnostic. One pair cannot establish how frequently it would recur.
Window metrics are timing proxies and make no FPS or presentation claim.

Validation records 144 JVM tests, 119 iOS Simulator passing records (one Keychain
native operation skips for missing entitlement), 23 Python tests, Android release
fixtures, a desktop build, and visual/Day-navigation checks. DayCell is unchanged
from V3; its earlier TalkBack evidence is reused, not represented as a fresh V4
screen-reader session. At the extreme 1080×1100 viewport, busy dots touch date
digits and today's badge compresses. This inherited limitation is visible in
the retained screenshot and is not classified as a new V4 regression.

The implementation review's diagnostic-stage scores are **8.5 implementation /
5 demonstrated gains / 8 correctness**. The independent performance review scores
**8 implementation / 6 demonstrated gains / 8.5 correctness**. Limited sampling and the unresolved
stall prevent an acceptance-level gains assessment; the compact-layout limitation
also remains open. These scores do not replace the V3 scores below. The next
investigation concerns paging/prefetch work on the stalled frame; no subsequent
candidate is accepted or rated here. **120 FPS remains unproven.**
<!-- MONTH_CELL_COMBINED_END -->

<!-- MONTH_CELL_SEMANTICS_START -->
## Rejected follow-up: consolidated day-cell semantics (V3)

Decision: reject the current V3 candidate for production promotion. Preserve its
semantics improvement as an experiment, but retain V1 as the accepted baseline
until the repeated loading stalls are resolved. The complete confirmation shows
real CPU and deadline benefits alongside a repeatable tail regression. **120 FPS
remains unproven on Android, iOS, and macOS.**

Results: [month-cell-semantics-results.json](month-cell-semantics-results.json).
Forty fresh frozen-binary captures compare V3 with accepted V1: five interleaved
pairs each for alternating and forward paging, plus ten loading pairs. The six
exploratory captures are excluded. Fixtures, compilation policy, and query
signatures match within pairs; no host builds ran during confirmation. All forty
trace hashes and raw p95/p99 values were independently checked. V2 geometry
hoisting is not part of V3, and older runs are not mixed into this comparison.

V3 consolidates redundant child semantics into one semantic text value and action
per day cell, preserving visible full titles, totals, omissions, Today, and Day
navigation. Preview density and visual layout are unchanged. The sampled tree
shrinks from 278 to 80 nodes while retaining 42 day buttons. This measures tree
structure, not native ContentCapture payload delivery or frame rate.

### Android results and decision

Values below are medians of per-run statistics. Frame durations and late rates
cover the full trace; CPU covers the union of swipe-command intervals plus
500 ms settling, with scheduler-running intervals clipped to those windows.

| Profile | Window main CPU V1 → V3 | p95 V1 → V3 | p99 V1 → V3 | Late frames V1 → V3 |
| --- | ---: | ---: | ---: | ---: |
| Alternating months | 1,662.139 → 1,393.595 ms | 13.169 → 12.735 ms | 22.908 → 21.329 ms | 3.992% → 2.893% |
| Forward months | 1,582.985 → 1,336.019 ms | 12.969 → 12.764 ms | 20.400 → 21.661 ms | 4.275% → 2.747% |
| Loading and refresh | 2,765.711 → 2,271.754 ms | 13.4375 → 13.1195 ms | 23.1405 → 24.127 ms | 4.762% → 3.456% |

Interaction-window main CPU falls approximately 16.2%, 15.6%, and 17.9% across
these profiles, with improvement in all twenty paired runs. These are meaningful
savings, but below the prospective approximately 20% target in every profile.
Window RenderThread CPU rises approximately 9.2%, 5.2%, and 5.1%; medians of each
run's combined main-plus-RenderThread CPU improve by approximately 3.8%, 5.0%, and
6.2%. This is a CPU tradeoff, not an energy measurement.

Steady alternating p95 and late rates improve in every pair; forward late rates
also improve in every pair. Self Jank medians fall from 2.471% to 1.266% for
alternating, 2.559% to 1.280% for forward, and 3.300% to 1.294% for loading.
Forward p99 nevertheless worsens approximately 6.2%, including four of five
paired runs. Loading full-trace p99 worsens approximately 4.3%; its gesture-window
p99 improves from 22.8635 to 20.8485 ms. These different windows must not be
substituted selectively to hide the long-frame result.

The decisive regression is repeated loading stalls. Baseline has no frames of
at least 40 ms in ten runs. Candidate has six such frames in six runs, including
four of at least 50 ms: 54.050, 55.487, 65.173, and 66.001 ms. All six occur within
the interaction windows and overlap owner measure/layout work. Median per-run
maximum duration rises from 32.3085 to 43.771 ms; the largest observed duration
rises from 38.014 to 66.001 ms. This violates the frozen requirement of no worse
recurring >=40/50 ms tails. Overlap alone does not prove the causal call path.

The intended mechanism is supported: across the ten steady runs per build,
median inclusive ContentCapture change-checker time falls 497.627 → 132.318 ms;
loading falls 781.125 → 234.4335 ms. Semantics traversal also becomes cheaper.
These nested wall-time slices are not additive CPU measurements. Loading owner
measure/layout instead increases 473.0445 → 530.524 ms. The next experiment must
reduce the work on the stalled path, not merely relabel composition as measure.

Median end-of-workload PSS is 195,761 → 145,683 KB for alternating,
197,941 → 149,464 KB for forward, and 198,793.5 → 174,118 KB for loading. These are
single snapshots per run, not peak or retained-memory measurements. The separate
five-pair interleaved cold-process startup series is complete: medians are
218 → 223 ms (+2.3%), with a median paired increase of 8 ms and four of five
candidate starts slower. Ranges are 212–226 ms before and 215–227 ms after.
All ten raw Android `TotalTime` values were independently checked; launches use
`am start -S -W`. After pair three, the idle benchmark was force-stopped during
the before-install cooling guard, outside any timed sample; the thermal threshold
was unchanged and the intervention is logged. This small startup slowdown does
not change the rejection based on recurring loading stalls.
All runs retain 16.6 ms median scheduled app budgets, not a certified 8.33 ms
presentation cadence.

### Accessibility and compatibility

Evidence is recorded in
[month-cell-accessibility-validation.json](month-cell-accessibility-validation.json).
Actual TalkBack navigation and activation used a temporary Android UHID keyboard.
Recorded speech verifies one focus per sampled day, full visible titles, date,
totals, omissions, holiday-only loading, a focused count update, a visible
refreshed title on reread, and compact-today information. TalkBack activation and
ordinary Tab/Enter both open January 8. Device settings were restored and the
temporary keyboard removed.

Physical finger double-tap and iOS VoiceOver were not runtime-tested. A reread
after closing a help dialog generated two closely spaced focus utterances;
there is no claim that all announcements occurred exactly once. Annotated text
exposes visible titles in semantics, but delivered native ContentCapture payloads
were not independently inspected.

Validation currently records 144 JVM tests, 119 iOS Simulator passing records,
23 Python tests, Android release fixtures, and a desktop build. One iOS Keychain
test skips its native operation because the runner lacks entitlement. Default,
doubled-font, compact-today, and Day-navigation screenshots were inspected.

### macOS diagnostic and limits

One sequential before/after process pair completed using the full-classpath
packaged app, an isolated home directory, a 1100×800 window, and a stable-focus
start gate. Metal is reported at both boundaries. The window is active at start,
end, and every recorded round boundary; this is sampled activity evidence rather
than continuous focus telemetry. Each process contains one excluded warmup round
and five measured rounds. Those rounds are not five independent process pairs.

| Diagnostic, median of five measured rounds | V1 | V3 |
| --- | ---: | ---: |
| Process CPU per round | 2,367.230 ms | 2,419.774 ms |
| Frame-clock callback p95 | 9.138250 ms | 9.180667 ms |
| Frame-clock callback p99 | 10.041167 ms | 9.833417 ms |
| Sampled JVM used heap | 71,937,752 bytes | 51,629,648 bytes |

CPU rises approximately 2.22%; callback summaries are mixed. The largest measured
callback interval is 21.939709 ms before and 62.601250 ms after. Neither the lower
median callback p99 nor the lower sampled heap establishes a performance win.
Heap samples are affected by allocation and collection timing and are not
retained-memory or peak-memory measurements.

JFR records one explicit `System.gc()` in each process, initiated by
`org.jetbrains.skiko.FrameWatcher` on a dispatcher worker. The largest recorded
GC pause is 19.517542 ms before and 56.920459 ms after. The candidate's
`SystemGC` event begins at 05:16:42.996 local time and lasts about 57.042 ms; the
62.601250 ms callback occurs in round 4, transition 5. The long GC is a plausible
contributor, but the callback CSV lacks an absolute timestamp anchor, so exact
overlap and causality cannot be established from these files. It is not a GPU
stall attribution.

Frame-clock callbacks request frames, include settling, and do not establish
presentation cadence, GPU completion, or rendered page settlement. The probe
checks requested date state. The reported 120 Hz display mode is not 120 FPS
evidence. This single process pair is a diagnostic, not Apple acceptance.

The earlier `desktop-confirmation` pair was inactive and remains excluded; its
raw artifacts are retained. Accepted diagnostic artifacts are under
`captures/performance/month-cells-v3/desktop-active/`, including the exact measured
probe snapshot in `source/`. A subsequent probe cleanup adds a `finally` cleanup
path and is not represented as the exact measured source. Existing ViewModels
still initialize beneath the identical UI fixture and logged network errors;
this was not a network benchmark and neither process used the real user home.

### Independent review and next experiment

| Independent reviewer | Implementation /10 | Demonstrated gains /10 | Correctness /10 |
| --- | ---: | ---: | ---: |
| Implementation reviewer | 8.5 | 6.5 | 8.5 |
| Performance reviewer | 8 | 7 | 8.5 |
| Correctness reviewer | 8.5 | 7.5 | 8.5 |

This review is final for the forty-capture V3 candidate and the separate
five-pair startup check. Strong semantics/accessibility implementation and
repeatable CPU savings do not justify an 8-level gains score while loading
introduces repeated 50–66 ms frames. These are incremental-change ratings, not
120 FPS readiness scores.

A V4 experiment subsequently combined shared geometry with these semantics to
address whole-page composition inside owner measurement. Its rejected diagnostic
is documented above; V3's confirmation and tail evidence remain unchanged.
<!-- MONTH_CELL_SEMANTICS_END -->

<!-- MONTH_CELL_HOISTING_START -->
## Rejected follow-up: shared geometry above the pager (V2)

Decision: reject V2 and retain the accepted bounded-preview implementation (V1).
V2 hoisted viewport/text metrics and the weekday header above the month pager,
preserving preview density. It reduced measurement work but did not demonstrate
a sufficiently consistent improvement in interaction latency. Its source changes
were restored to V1 before beginning the next experiment.

Results: [month-cell-hoisting-results.json](month-cell-hoisting-results.json).
Forty fresh captures compare V2 against V1: five interleaved pairs each for
alternating and forward paging, plus ten loading pairs. Binaries were frozen,
fixtures matched, and no host builds ran during capture. The refresh-rate overlay
was unchanged between these two builds; its presence differs from the preceding
historical series, so those captures are not mixed into this comparison.

Values are medians of per-run statistics.

| Profile | p95 V1 → V2 | p99 V1 → V2 | Late frames V1 → V2 |
| --- | ---: | ---: | ---: |
| Alternating months | 12.922 → 13.215 ms | 21.647 → 20.584 ms | 3.202% → 3.766% |
| Forward months | 13.423 → 13.549 ms | 19.837 → 18.974 ms | 4.244% → 4.267% |
| Loading and refresh | 13.366 → 13.286 ms | 24.060 → 23.736 ms | 4.850% → 4.900% |

The modest p99 improvements coexist with worse steady p95 and a 0.565 percentage
point increase in alternating late frames. Forward median paired p99 improvement
is only 0.021 ms, despite the larger difference between group medians. Loading
owner measure/layout falls 451.381 → 388.351 ms (14%), but full-trace p99 improves
only 1.35%. Median DayCell and EventTag execution counts remain unchanged across builds.
Moving work between composition and measurement does not itself establish an
end-to-end gain.

An independent CPU-window audit corrects the initial full-trace interpretation.
Full-trace main-thread CPU medians rise 3.7% for alternating and 8.0% for forward,
but these include work outside the swipe windows. Within swipe-command intervals
plus 500 ms settling, main-thread CPU is 1,617.596 → 1,605.169 ms for alternating,
1,637.555 → 1,650.878 ms for forward, and 2,779.703 → 2,732.956 ms for loading.
Thus the forward window increase is approximately 0.8%, **not an 8% swipe-CPU
regression**. The audit is retained at
`captures/performance/month-cells-v2/independent-cpu-window-audit.json`. These
windows remain timing proxies rather than exact gesture attribution.

Across ten loading runs per build, full-trace frames of at least 40 ms fall from
three to two, and frames of at least 50 ms fall from one to zero. The largest
baseline frame is 57.509 ms; the largest candidate frame is 45.944 ms. These sparse
tail improvements are useful evidence but do not outweigh the mixed common-case
results. Five sequential cold-process starts give medians of 229 → 225 ms; this
small sample shows no observed startup regression. Median PSS changes are below
1% in each profile, with substantial loading snapshot variability.

Validation includes 141 JVM tests, 116 iOS Simulator passing records (the Keychain
native operation skips for missing entitlement), 23 Python tests, Android release
fixture builds, a desktop build, and default/large-font/compact-today/Day-navigation
visual checks. No Apple presentation result or 120 FPS certification is claimed.

| Independent reviewer | Implementation /10 | Demonstrated gains /10 | Correctness /10 |
| --- | ---: | ---: | ---: |
| Implementation reviewer | 8 | 3.5 | 8 |
| Performance reviewer | 8 | 6 | 8 |
| Correctness reviewer | 8.5 | Unrated | 8.5 |

These assess V2 relative to V1, and do not replace V1's scores below. The CPU-window
correction does not change the rejection: latency and deadline evidence remains
mixed. The subsequent V3 semantics experiment is assessed separately above.
<!-- MONTH_CELL_HOISTING_END -->

<!-- MONTH_CELL_FOLLOWUP_START -->
## Follow-up: bounded month-cell previews

Decision: accept bounded previews as an incremental production improvement. **120 FPS remains unproven on Android, iOS, and macOS.** The measurements
support this specific UI tradeoff on one Android device, not general performance
certification.

The implementation removes each cell’s vertical scroll container. One grid-level
measurement budgets a holiday-first preview of at most three combined entries,
reserves overflow space only when required, and counts hidden events and holidays
together. At the tested phone size, dense cells show two entries and a compact
`+N`; doubled font scaling shows one entry; short windows use a busy-day dot.
Tapping the cell still opens the full Day view. This reduces preview density;
it is a deliberate UI tradeoff, not identical pixels rendered faster.

Implementation and local benchmark tooling were delegated to GPT-6 Luna agents.
Three separate GPT-6 Astra reviewers independently assessed implementation,
performance evidence, and correctness. The parent integrates fixes and runs the
device captures; the council does not edit the code it scores.

Machine-readable results: [month-cell-performance-results.json](month-cell-performance-results.json).
Raw traces, APKs, source hashes, the measured patch, and detailed summaries remain
under ignored `captures/performance/month-cells/`.

### Android comparison

Thirty fresh captures completed: five interleaved pairs each for alternating
month paging, forward paging, and delayed full-year loading on a Pixel 9.
Odd repetitions ran before/after; even repetitions ran after/before, with workload
order also reversed. Both sides used the same 3,650-event fixture payloads and
per-install speed compilation. The steady profiles used six swipes and loading
used ten. Every visited month was checked; complete mock delivery and refresh
occurred within each loading trace. Trace hashes and raw frame summaries were
independently checked against the report.

Values below are medians of five per-run statistics, not pooled percentiles.

| Profile | p95 before → after | p99 before → after | Late frames before → after |
| --- | ---: | ---: | ---: |
| Alternating months | 14.554 → 12.945 ms | 23.436 → 21.339 ms | 5.163% → 3.396% |
| Forward months | 13.878 → 12.874 ms | 21.708 → 21.189 ms | 4.128% → 3.364% |
| Loading and refresh | 14.795 → 13.165 ms | 26.925 → 23.674 ms | 6.264% → 4.362% |

P95 improves by approximately 7–11%, and late-frame rates improve in all fifteen
paired comparisons. Alternating and loading p95 ranges do not overlap between
builds. Forward p99 improves only 2.4%, with overlapping run ranges; it does not
establish a substantial tail-latency improvement for that profile.

The tradeoffs remain visible. Alternating Self Jank increases from 2.264% to
2.403%. Median end-of-workload PSS increases from 191,099 to 197,578 KB (+3.4%)
for alternating and from 195,179 to 199,594 KB (+2.3%) for forward paging. Loading
PSS falls from 188,985 to 148,574 KB (−21.4%). These are snapshots, not peak memory
or allocation measurements. Although median per-run maximum duration improves
in every profile, the largest loading frame worsens from **44.874 to 57.056 ms**.

Five cold-process starts per build give a median of 228 → 221 ms, with ranges
225–267 and 220–228 ms respectively. This small sequential sample shows no
observed startup regression; it is not a strong startup speedup claim.

### Interpretation and limits

Removing scroll containers reduces DayCell composition work, but the grid-level
`BoxWithConstraints` also moves composition into measurement. For example,
alternating prefetch-measure inclusive time rises from 25.666 to 47.703 ms while
prefetch-compose falls from 44.553 to 8.893 ms. Overall measure/layout medians are
266.636 → 270.929 ms for alternating, 254.797 → 264.210 ms for forward, and
471.644 → 456.668 ms for loading. These nested slice durations overlap and must
not be added together. Comparing only the outer MonthView composition slice
would overstate the benefit. Main-thread CPU medians improve for the steady
profiles but increase approximately 1% during loading; RenderThread CPU falls
in all three profiles.

The secondary analysis selects frames starting within each device-timestamped
swipe-command interval plus 500 ms of settling. Steady results are unchanged;
loading p95 is 14.543 → 13.152 ms and p99 is 26.277 → 23.423 ms. The 57.056 ms
loading outlier remains inside this window. Command intervals include ADB
launch/return overhead and are proxies, not exact touch-event or presentation
timestamps. Full frame durations and deadline overruns are retained.

All thirty runs have a median scheduled app deadline budget of **16.6 ms**.
Neither these deadlines nor the display's mode certify an 8.33 ms presentation
cadence. Late-frame rates also remain above the proposed 1% gate. No new Apple
presentation measurements were made; shared iOS/JVM compilation is compatibility
evidence, not evidence of faster rendering on those platforms.

Battery temperature drifts from approximately 31.9°C to 35.6°C across the series.
Global thermal status remains zero, but nonzero individual sensor statuses are
recorded. Interleaving reduces order bias without eliminating thermal or runtime
noise. Host iOS native linking overlapped the initial captures; the workload was
rendered on the physical Android device, but host activity could affect command
timing. Recorded intervals are retained rather than treating the environment as
fully controlled.

The refresh mutates every tenth event title while preserving IDs. Some refreshed
items are outside the bounded Month preview; phase overlap demonstrates dataset
updates during the workload, not visible-title replacement for every update.
These are UI-state mocks, not network, database, or sync-throughput benchmarks.

### Correctness, final delta, and council review

Validation completed for the measured candidate: 141 shared JVM tests and 23
Python harness tests; Android release fixtures, JVM desktop build, and shared
iOS Simulator release build; inspected default, doubled-font, compact-height,
and Day-navigation screenshots. Preview tests cover empty lists, holiday-only
overflow, combined counts, the three-entry cap, and measured capacity boundaries.
Tapping January 8 opens the correct Day view with both fixture holidays. Full
TalkBack and non-Latin-title runtime validation remain uncovered.

Post-benchmark compact-today QA found the busy-day dot clipped by today's shaped
badge. The only subsequent UI change moves that fallback dot from `BottomEnd`
to `BottomCenter`. A separately built final APK was visually checked on the
compact November grid: today’s indicator is visible and its date unobscured.
The final JVM rerun passes all 141 tests, and the Python harness passes all 23.
The final iOS Simulator test run completes with 116 passing records, including
one Keychain test that skips its native operation because the runner lacks the
entitlement. Final shared iOS release compilation and the macOS desktop build pass. Timed APKs remain untouched: their default-height cells display `+N`
and never compose the fallback dot. The results JSON records separate measured
and final source hashes; the final source is not represented as the exact
benchmarked binary.

| Independent reviewer | Implementation /10 | Demonstrated gains /10 | Correctness /10 |
| --- | ---: | ---: | ---: |
| Implementation reviewer | 8 | 6.5 | 8 |
| Performance reviewer | 8 | 7 | 8 |
| Correctness reviewer | 8.5 | 8 | 8.5 |

The council accepts the bounded-preview change, including the visually verified
compact-today fix. These subjective scores assess
the change and its evidence, not 120 FPS readiness. Consistent p95/deadline gains
justify the reduced preview density, while steady memory cost, the loading
outlier, accessibility coverage, and Apple performance acceptance remain explicit
limitations.
Validation commands: `./kotlin test -m shared -p jvm -p iosSimulatorArm64 -v release`,
`./kotlin build -m desktopApp -p jvm`, the isolated Android release fixture builder,
`python3 -m unittest discover -s scripts/performance -p 'test_*.py'`, and
`git diff --check`. Android instrumentation tests and new Apple runtime frame
benchmarks were not run in this iteration.

### Next optimization

Investigate grid measurement and prefetch during batched delivery. The worst
loading frame contains 33.279 ms of Android owner measure/layout inside a
49.194 ms frame callback; these nested durations are not additive. Preserve this
loading workload and its outlier when evaluating the next change. Separately,
collect release presentation traces on high-refresh Android, iOS, and macOS
hardware before claiming 120 FPS readiness.
<!-- MONTH_CELL_FOLLOWUP_END -->

## First experiment: retained pages (historical)

Decision: keep the new fixtures and measurement tools; do not promote the
month-retention experiment to the production UI. The app is not yet certified
for 120 FPS on Android, iOS, or macOS. This iteration completes the Android
baseline and experiment; Apple high-refresh acceptance remains open.

Machine-readable measurements: [performance-readiness-results.json](performance-readiness-results.json).

## Scope and acceptance

The objective is smooth interaction on high-refresh displays, while preserving
calendar behavior. GPU acceleration is already part of the standard Compose
rendering paths. A 120 Hz display mode alone is not evidence of 120 presented
frames per second. Frame deadlines, presentation cadence, CPU work, and memory
are separate measurements.

This iteration establishes an unchanged-source baseline and tests one small
shared UI experiment: one beyond-viewport month page on each side of the pager.
Day/Three Day/Week retain their existing pager policy. This is not a strict
three-page cap: visible and prefetched pages can add to the composed range.

## Reproducible mock workloads

`scripts/performance/fixture.py` supports:

- Existing 300-event November workload.
- `--paging`: 900 events across October–December 2026, for continuity with the
  existing comparisons. Its forward journey eventually enters empty months;
  it is not accepted as a dense unseen-page test.
- `--year`: 3,650 events, ten starts on every day of 2026, five calendars,
  108 fictional holiday entries, and a January 8 selection. Six forward swipes
  remain inside the populated year. Overnight and all-day events can extend
  into January 2027; event ends remain exclusive.
- `--load-delay-ms`, `--batch-delay-ms`, and `--refresh-delay-ms`: delayed initial
  delivery, 100-event batches, and one refresh that updates every tenth title
  while preserving IDs. Phase markers establish when these updates happened.

These are UI-state mocks, not network, JSON API, database, authentication, or
sync-throughput benchmarks. Existing ViewModels/repositories still initialize.
The temporary fixture routing prevents a saved Google session from replacing
mock UI data. Large fixture payloads are generated as JSON string chunks to
avoid giant Kotlin methods; loading profiles prepare the data off the UI thread.
The application has no new production mock-data mode.

The desktop start gate also gates mocked loading. Its five-second settling
period and excluded warmup must be considered when deciding whether a profile
overlapped the measured rounds. Short profiles can be steady-state tests only.

Example preparation, from an isolated checkout:

```sh
python3 scripts/performance/build_android.py --fixture --year --tracing \
  --output captures/year/app-unsigned.apk --fixture-output captures/year/fixture

python3 scripts/performance/build_android.py --fixture --year --tracing \
  --load-delay-ms 15000 --batch-delay-ms 100 --refresh-delay-ms 2000 \
  --output captures/loading/app-unsigned.apk --fixture-output captures/loading/fixture

python3 scripts/performance/fixture.py --year --output captures/ios-fixture -- \
  ./kotlin build -m iosApp -p iosSimulatorArm64 -v release
```

Use a new output directory for each desktop run. The desktop harness uses the
repository's complete-classpath `packageDesktop` command because the pinned
toolchain's executable-JAR runner omits required redirect dependencies. Use
`--app-image --wait-for-start`, bring the packaged app to the foreground, and
create `<output>/start.signal` only after competing builds have stopped.

## Measurement methods

Android uses release benchmark APKs with a separate application ID, app-only
`speed` compilation, the same fixture on both sides, six fixed-duration swipes,
and Perfetto. Source, APK, fixture, and trace hashes retain provenance. UI page
checks and screenshots supplement command success. The full-year month checks
require fixture labels intersecting the calendar viewport.

The deadline query joins app surface frames to their expected end times and
rejects missing or ambiguous matches. `late_frames` is not the same as
`Self Jank` or `App Deadline Missed`; none is converted into FPS. The summary
rejects incomplete workloads, unverified APKs, and trace loss. It reports
medians/ranges of per-run statistics, not pooled percentiles or confidence
intervals. Display snapshots record boundary configuration, not per-frame
presentation cadence.

iOS uses an optimized simulator app and a prebuilt XCTest runner. CPU time and
gesture/idle-inclusive clock time are not hitch or FPS measurements. The
connected physical iPhone 13 cannot certify ProMotion performance; physical
high-refresh iPhone acceptance remains a separate requirement.

macOS uses a packaged app with a separate `user.home`, a fixed window size,
JFR, and a programmatic month-navigation diagnostic. Round zero warms the path;
five measured rounds follow. Frame-clock callbacks request frames and include
settling tails. They do not prove GPU completion, presentation rate, or every
rendered page settlement. Source-state assertions only verify requested dates.
Window activity and display metadata help detect unsuitable captures.

## Initial 900-event Android comparison

Five runs per build; each value below is the median of per-run statistics.
This initial comparison was sequential, with a slightly warmer candidate.
The full-year follow-up uses an interleaved order.

| Metric | Baseline | Retention experiment |
| --- | ---: | ---: |
| Month p95 frame duration | 13.651 ms | 13.947 ms |
| Month p99 frame duration | 24.583 ms | 18.695 ms |
| Month worst frame | 37.617 ms | 31.039 ms |
| Month Self Jank | 2.218% | 2.206% |
| Cold-process launch median, five starts | 208 ms | 233 ms |

The p99 reduction is about 24%; typical duration and app-attributed jank do not
improve. Retention has a memory and startup cost. This is a tail-latency
tradeoff, not a claim of universal improvement or 120 FPS readiness.

## Full-year Android before/after

Same 3,650-event fixture; three alternating runs per build, ordered
B1, A1, A2, B2, B3, A3. Each run has six swipes. “After” below means the isolated
retention experiment, not the production working tree.

| Metric | Before | Experiment after |
| --- | ---: | ---: |
| Median per-run p95 | 14.645 ms | 14.588 ms |
| Median per-run p99 | 24.682 ms | 21.900 ms |
| p99 range across three runs | 24.094–26.760 ms | 21.288–22.447 ms |
| Median per-run maximum | 31.265 ms | 31.009 ms |
| Largest observed frame | 31.901 ms | 58.868 ms |
| Median Self Jank | 2.569% | 2.773% |
| Median late-frame rate | 4.954% | 5.730% |
| Median PSS snapshot | 176,169 KB | 185,931 KB |

The repeated-page p99 improvement is **11.3%**. P95 is essentially unchanged,
late frames increase by 0.776 percentage points, and PSS increases about 5.5%.
PSS is an end-of-workload snapshot, not peak memory or allocation rate.

The dense forward journey uses January through July. One run per build yields
p99 **18.086 → 23.168 ms**. One loading run per build uses a 15-second initial
delay, 100-event batches every 100 ms, and a refresh after 2 seconds, with ten
swipes. Loading p99 is **24.295 → 23.176 ms**, but maximum frame duration is
**37.787 → 77.943 ms**. All 3,650 events and the refresh completed inside the
actual trace interval on both sides. These single pairs are exploratory and
need repetition; they cannot establish a stable improvement or regression size.

The tradeoff did not justify enabling retention globally. The investigation-only
patch was retired during benchmark tooling cleanup; the measurements above remain
historical evidence, not a currently reproducible checked-in variant.

## Apple results and acceptance limits

Both iOS simulator release variants built successfully. The full-year/loading
shared module also compiled for iOS Simulator, including its test sources;
that is not an executed test suite or a loading runtime benchmark. The identical
prebuilt paging XCTest uses three samples plus warmup. Baseline CPU samples are
3.130, 4.425, and 4.002 seconds (mean 3.852 seconds); baseline clock mean is
12.550 seconds. Candidate CPU samples are 3.713, 3.680, and 3.603 seconds
(mean 3.665 seconds); candidate clock mean is 12.342 seconds. Both tests passed.
The apparent 4.9% CPU reduction is within the noisy baseline's 14.0% relative
standard deviation and is not evidence of a reliable cross-platform gain.
No simultaneous agent compilation occurred during the final candidate test;
uncontrolled host activity still limits this sequential simulator comparison.

Both macOS variants packaged successfully. The latest baseline completed its
36 state requests, but reported `activeAtStart=false` and inactive status at
every round boundary. The summarizer now rejects it. Java reported a 120 Hz
display; this does not establish the app's actual presentation cadence. The
candidate capture was stopped before measurement to avoid ambiguous native
window selection. There is **no accepted macOS before/after improvement claim**.
The probe records backend metadata in future runs; the excluded existing
binaries predate that addition. Native window focus could not be verified through
the available UI controls.

## Validation performed

- Python harness suite: 23 tests passed, including fixture restoration,
  deterministic year coverage, deadline matching, trace health, and rejection
  of mock delivery outside the recorded interval.
- Isolated candidate shared JVM suite: 132 tests passed.
- Android release fixture builds: baseline and candidate for steady full-year
  and delayed/batched/refresh profiles passed; physical-device captures completed.
- iOS baseline/candidate simulator release builds passed; full-year/loading
  shared iOS compilation passed.
- macOS baseline/candidate full-classpath packaging passed.
- Android January fixture layout and screenshot inspected after loading;
  no XCalendar crash entries found in the device crash buffer.

Principal commands actually used, with local paths/device IDs omitted:

```sh
python3 -m unittest discover -s scripts/performance -p 'test_*.py'
./kotlin test -m shared -p jvm
./kotlin build -m iosApp -p iosSimulatorArm64 -v release
./kotlin build -m shared -p iosSimulatorArm64 -v release
./kotlin do packageDesktop -m desktopApp
python3 scripts/performance/analyze_android.py trace --processor <trace_processor> --trace <trace> --output <analysis>
python3 scripts/performance/analyze_android.py summary --root <capture-root> --output <summary.json>
xcodebuild test-without-building -xctestrun <prebuilt-runner> -destination <simulator> -only-testing:CalendarBenchmarks/CalendarBenchmarks/testPagingJourney
```

Builds/tests above ran in isolated snapshots. Shared Android/iOS test execution,
physical ProMotion testing, full-year/loading runtime captures on Apple platforms,
and a valid macOS presentation trace remain uncompleted checks.

## Next implementation steps

1. **Reduce month-cell work instead of retaining whole pages.** The baseline
   trace's largest prefetch measure was about 25.8 ms, and month composition
   about 10.0 ms. Profile expensive day-cell measurement and nested event lists;
   test a bounded visible-event layout that preserves overflow access and
   accessibility. Event indexing was not the dominant trace hotspot in this run.
2. **Repeat controlled experiments.** Use at least five interleaved runs each
   for alternating, forward, and loading profiles; compare deadline misses,
   p95/p99, startup, and memory together. Preserve exact build/fixture hashes.
3. **Complete Apple baselines.** Use a physical ProMotion iPhone with native
   hitch/presentation measurement. On macOS verify the active Metal backend,
   foreground window, native display cadence, and rendered page settlement;
   repair the Instruments capture before accepting hitch statistics.
4. **Set release gates.** On sustained 120 Hz interactions, compare actual
   presentation intervals and scheduled deadlines against the 8.33 ms cadence;
   proposed gate is under 1% late frames with no repeated multi-frame stalls,
   and no material startup/memory regression. Exercise cold/warm loading,
   refresh during swipes, all calendar views, edits, timezone/DST boundaries,
   and thermal steady state. These are proposed criteria, not results achieved.

A Rust renderer is not justified by these results. The measured issue is shared
UI composition/measurement work; replacing the renderer would not itself prove
that this work fits a high-refresh frame budget.

## Excluded and diagnostic evidence

- The first Android build failed Java certificate validation. Builds used a
  local trust configuration; no wrapper, dependency, or repository trust store
  was changed.
- The first desktop executable-JAR run failed with missing `ViewModel` classes;
  it produced no performance baseline.
- An occluded desktop run had roughly 309 ms callback intervals. Local Skiko
  source explicitly throttles occluded Metal windows by about 300 ms. Raising
  the packaged window removed this pattern; it is not evidence of calendar
  computation taking 300 ms.
- The first foreground desktop diagnostic overlapped later compilation and is
  excluded from clean before/after conclusions.
- The macOS Animation Hitches recording reported a corrupt/incomplete logging
  stream. Its partial trace is retained but not accepted as hitch evidence.

Raw files and source snapshots are under ignored
`captures/performance/readiness/`. The original baseline snapshot was restored
and checked against its source hashes after the isolated experiment inputs were
preserved separately. No benchmark binary or toolchain cache belongs in Git.

## Independent council review

The three agents inspected the code and raw Android results independently.
Scores assess this tooling and experiment, excluding pre-existing repository
changes. They are subjective review scores, not a 120 FPS readiness score.

| Reviewer | Implementation /10 | Performance gains /10 | Correctness /10 |
| --- | ---: | ---: | ---: |
| Implementation council | 8 | 4 | 7 |
| Performance council | 8 | 4 | 8 |
| Correctness council | 7.5 | 3 | 8 |

All three recommended keeping retention experimental. Their reviews identified
validation gaps that were subsequently fixed: full-year January coverage,
Google fixture routing, exact routing-anchor checks, desktop loading gates,
fresh output directories, Android delivery checks against both actual trace
boundaries, and rejection of inactive desktop captures. The implementation and performance reviews precede the final guard fixes and
iOS candidate result. The correctness reviewer rechecked those changes and
independently raised correctness from 7.5 to 8; the other scores were retained.
macOS rendered settlement and presentation evidence remain open.
