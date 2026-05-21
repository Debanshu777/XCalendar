# Perf baselines

Captured: **2026-05-21** on `main @ 07deff3` (Phase 0 setup, pre-Phase-1).
Hardware: dev machine (Apple Silicon).

## ScheduleStateHolder microbench

Source: `composeApp/src/commonTest/kotlin/com/debanshu/xcalendar/perf/ScheduleStateHolderPerfBaselineTest.kt`

| Test | Baseline | Budget (5x slack) |
|------|----------|-------------------|
| `init 12-month window with 500 events` | **36 ms** | 1000 ms |
| `loadMoreForward × 6 months` | **5 ms** | 500 ms |
| `refreshItems with 1000 events` | **1 ms** | 1500 ms |

Item counts (sanity): init=48, after 6 fwd=405. (Items only include `DayEvents` for days with events/holidays; sparse fake data.)

## How to run

```bash
./gradlew :composeApp:desktopTest --tests "*ScheduleStateHolderPerfBaselineTest*"
```

Output captured to `composeApp/build/test-results/desktopTest/`.

## Caveats

- Microbench only — does not exercise Compose recomposition.
- Fake data uses sequential timestamps (1 event/day for 500 days). Real-world distribution (multiple events/day, sparse months) may differ.
- Apple Silicon dev machine is faster than target devices. Re-run on a Pixel-class device for absolute numbers.
- Budgets intentionally loose (5x slack) — catches major regressions, not micro-drifts.

## Phase 7 success criteria

- `init 12-month window` ≤ 36 ms × 1.2 = **43 ms** (no regression, ideally drops with sweep-line groupBy in F9)
- `refreshItems` ≤ 1 ms (already trivial)

## Future work

- Add Compose macrobench (`androidx.benchmark.macro`) in Phase 7 measuring scroll frame-time on Pixel device with 500-event month.
- Add UI-recomposition counter test using `Recomposer` debug APIs in Phase 6.
