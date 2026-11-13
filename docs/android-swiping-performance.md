# Android swipe comparison — 10 November 2026

The earlier indexing/month-cell changes improve month paging on the connected
Pixel 9, but **do not eliminate its long frames**. Day paging shows no meaningful
improvement. These results do not establish that the reported iPhone stutter is
fixed. The subsequent iPhone investigation identified an installed Debug build
and Google response decoding on the main thread; those are outside this pair.
The final experiment below separately measures removal of month-cell shared
transitions, after the user reported that iPhone Release was still unusable.

## Measured results

Each number below is the median of three complete runs per build. Each run has
six alternating swipes and a 30-second Perfetto trace. These are actual
FrameTimeline durations, not FPS or XCTest interaction clock times.

| View / metric | Before | After |
| --- | ---: | ---: |
| Month p95 frame duration | 20.356 ms | 13.307 ms |
| Month p99 frame duration | 86.100 ms | 46.304 ms |
| Month worst frame per run | 105.453 ms | 68.583 ms |
| Month frames tagged Self Jank | 8.52% | 4.22% |
| Month App Deadline Missed | 4.21% | 1.54% |
| Month main-thread CPU per trace | 3.775 s | 2.355 s |
| Day p95 frame duration | 6.854 ms | 6.635 ms |
| Day p99 frame duration | 14.469 ms | 13.888 ms |
| Day frames tagged Self Jank | 0.18% | 0.36% |
| Day main-thread CPU per trace | 1.220 s | 1.229 s |

Month p99 improves 46.2% and main-thread CPU drops 37.6%. Its before p99 range
is 85.478–89.490 ms; after is 46.037–50.113 ms. The improvement repeats across
all three runs, but the updated app still has 64–76 ms worst frames. Day's
before/after p99 ranges overlap; its jank counts are too small to infer an
improvement (1–2 frames before, 1–4 after).

Perfetto's [FrameTimeline documentation](https://github.com/google/perfetto/blob/main/docs/data-sources/frametimeline.md)
defines its actual-frame and jank attribution data. `Self Jank` is the recorded
app attribution; it is not interchangeable with gfxinfo's janky-frame count or
a fixed 16.67 ms threshold. Frame counts vary with the rendered workload.

## What remains expensive

Named Compose tracing finds 3,276 `DayCell` executions per before trace versus
873 after. These include composition and recomposition; they are not a count
of recompositions alone. In the first updated month trace, the largest
`AndroidOwner:measureAndLayout` slice is 47.209 ms and `Compose:deactivate` is
30.596 ms. Prefetch/apply and disposal still appear among the longest main-thread
slices. These inclusive durations overlap and must not be added together.

This points to constructing, measuring, and removing month-page content as
remaining work. It does not isolate an individual modifier's contribution or
prove that the same work dominates the iPhone. Simply retaining more pages
could hide round-trip costs while increasing initial/forward-page work and
memory; that change needs its own forward-paging experiment.

## Controls and limits

- Pixel 9, Android 17, fixed Asia/Kolkata time zone; separate benchmark app ID.
- Both apps are Release builds with the same Compose tracing dependencies and
  pinned Kotlin Toolchain. Each install was followed by app-only
  `cmd package compile -m speed -f`.
- A preserved source snapshot was used to avoid concurrent account-integration
  edits. Only `CalendarDates.kt`, `MonthView.kt`, and `DayCell.kt` differ. Before
  restores those three files from HEAD; after contains the earlier swipe fixes.
  The background Google projection/cache changes are present in both builds.
- Both use the same 900-event UI fixture over October–December 2026, five
  calendars, and nine holidays. This bypasses repository UI output; it does not
  measure Google network requests, response decoding, or cache throughput.
- Month swipes alternate November/December; day swipes alternate November 8/9
  after scrolling to visible daytime events. Drags last 400 ms, followed by a
  one-second pause. First/last page changes are checked in the UI hierarchy.
- Capture order is B1, A1, A2, B2, B3, A3, with month then day at each step.
  This is interleaved, not randomized. All 12 traces passed completion,
  APK/fixture fingerprint, configuration, and trace-health checks. Only the
  permitted informational compact-scheduler skip counters were nonzero.
- Recorded battery temperatures range from 31.7 to 33.7°C, at 100% charge with
  AC power reported. Global thermal status is zero at recorded boundaries;
  some local sensors report status 1. These checks do not exclude every thermal
  effect. Tracing and accessibility queries also add overhead to both builds.
- Three repetitions on one Android device are not a confidence interval or a
  substitute for iPhone frame measurements with the actual synced account.

[`android-swiping-results.json`](android-swiping-results.json) contains each
accepted run, per-run ranges, binary/fixture/trace hashes, observed page changes,
health counters, and device conditions without device identifiers. Raw artifacts
are under ignored `captures/performance/android-swiping/`.

## Repeat and inspect

The saved `source/`, `updated-inputs/`, and `fixture/` retain the compared inputs.
The fixture replicates the existing 300-event November fixture into October,
November, and December, preserving each event's local duration and prefixing its
ID with its start month. Titles intentionally retain the original November
labels used by the day-workload visibility checks. The normal `--fixture` build
flag supplies 300 events and is not the 900-event workload measured here.

For each signed artifact, install it, compile only the benchmark package with
`-m speed -f`, and capture each scenario with the same arguments:

```sh
python3 scripts/performance/android_benchmark.py --serial "$ANDROID_SERIAL" --scenario month --iterations 6 --compose-tracing --apk "$SIGNED_APK" --fixture-manifest captures/performance/android-swiping/fixture/fixture-manifest.json --recorder "$PERFETTO_RECORDER" --output "$RUN_DIR"
python3 scripts/performance/analyze_android.py trace --processor "$TRACE_PROCESSOR" --trace "$RUN_DIR/trace.perfetto-trace" --output "$RUN_DIR/analysis"
python3 scripts/performance/analyze_android.py compare --root captures/performance/android-swiping --scenario month --runs 3
python3 scripts/performance/analyze_android.py compare --root captures/performance/android-swiping --scenario day --runs 3
```

Use `--scenario day` for the day captures. The optional
`android_queries/page_work.sql` query reports main-thread lifecycle hotspots.
The collector now derives capture order from device timestamps instead of
assuming all before runs preceded all after runs. Harness regression tests:
`python3 -m unittest discover -s scripts/performance -p 'test_*.py'` (16 passed).

Final Android Month/Day layouts and screenshots were inspected. The month grid
shows all six weeks; Day shows the populated timed-event viewport. No benchmark
package crash-log entries were found for the measurement interval.

## Follow-up: remove month-cell shared transitions

The second pair keeps the first comparison's updated indexing/cell code on both
sides. Only Month's per-date/per-event shared modifiers and their unused
visibility arguments are removed. The navigation scene fade remains. It uses
the same fixture, release/tracing settings, app-only compilation, six swipes,
30-second trace, and B1/A1/A2/B2/B3/A3 ordering. All six runs are accepted.

| Metric, median of three runs | Previous updated build | Without month-cell shared modifiers |
| --- | ---: | ---: |
| p95 frame duration | 13.996 ms | 13.209 ms |
| p99 frame duration | 49.395 ms | 23.768 ms |
| Worst frame per run | 66.058 ms | 34.120 ms |
| Self Jank | 4.44% | 2.38% |
| App Deadline Missed | 1.36% | 0.00% |
| Main-thread CPU per trace | 2.318 s | 1.887 s |
| RenderThread CPU per trace | 1.746 s | 1.822 s |
| `DayCell` executions | 873 | 504 |

The p99 range is 47.042–49.767 ms before and 21.094–25.524 ms after, a 51.9%
median reduction. Median main CPU drops 18.6%. Typical frame duration changes
little, and RenderThread CPU rises 4.4%; rendered frame counts also rise.
The final run has one App Deadline Missed frame, so the zero median does not
mean no deadlines were missed. Worst frames still reach 32–35 ms.

Battery boundaries range from 32.4 to 33.7°C; global thermal status remains zero,
with local sensor warnings retained in the results. These are fresh comparison
runs, not a pooled continuation of the first pair. The JSON's
`month_animation_followup` section retains their fingerprints and individual
measurements. Raw data and the inspected final screenshot are under
`captures/performance/android-swiping/month-animation/`.
