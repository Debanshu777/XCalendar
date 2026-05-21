# Compose Compiler Metrics — Baseline

Captured: **2026-05-21** on `main @ 07deff3` (post-audit, pre-Phase-1).

## How to regenerate

```bash
./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinDesktop
```

Output lands in `composeApp/build/compose_compiler/`. `composeApp/build.gradle.kts` lines 38–45 configure `reportsDestination` + `metricsDestination`.

## Baseline summary

| Metric | Android | Desktop |
|--------|---------|---------|
| Composables (total) | 176 | (same — commonMain) |
| Restartable | 161 | — |
| Skippable | 92 | — |
| Skippable % | **52%** | — |
| Inferred stable classes | 121 | — |
| Inferred unstable classes | **40** | — |
| Marked-stable classes | 6 | — |
| Effectively stable | 127 / 177 (72%) | — |
| Known stable args | 2101 / 2163 (97%) | — |
| Memoized lambdas | 134 | — |
| Strong skipping | ✅ enabled | — |

## Unstable classes (40 — Phase 6 targets)

Domain models, repositories, view models, exception classes, serializers, store internals. See `composeApp-classes.txt`.

Notable for Phase 6 (`@Immutable` annotations):
- domain models (Event, Holiday, Calendar, User, etc.) — likely inferred-stable but worth confirming
- `ScheduleStateHolder`, `DateStateHolder`, `CalendarViewModel`, `EventViewModel` — flagged unstable (expected; VMs hold mutable state, callers should not equality-check them)
- API response models (Response, HolidayResponse, Meta, etc.) — unstable due to mutable serializer fields

## Phase 6 success criteria

- Skippable % → **≥ 70%** (currently 52%)
- Unstable class count → **< 25** (currently 40)
- All domain models flagged `@Immutable`, re-verify metrics shrink unstable count

## Files

- `composeApp-classes.txt` — full class stability inference
- `composeApp-composables.txt` — per-composable skippability flags
- `composeApp-composables.csv` — same as above, CSV form for diffing
- `composeApp-composables.log` — compiler warnings (1 known applier disagreement in CalendarTopAppBar.kt:128)
- `android-module.json` / `desktop-module.json` — aggregate counters
