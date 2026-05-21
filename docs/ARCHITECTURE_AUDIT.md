# XCalendar — Architecture Audit & Phased Execution Plan

**Date**: 2026-05-21
**Auditors**: Three architect personas (CMP-Arch / Coro-Arch / Data-Arch)
**Scope**: KMP Compose Multiplatform calendar app (Android / iOS / Desktop)
**Stack**: Koin DI · Store5 (offline-first) · KMP-Room (alpha) · Ktor · kotlinx.coroutines/flow · kotlinx.datetime · Compose

---

## Status legend

- Severity: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low
- Council vote: ✅ confirmed · ⚠️ confirmed with nuance · ❌ refuted · ➕ added by reviewer
- Status: ⬜ Open · 🟦 In Progress · ✅ Done · 🚫 Won't Fix

---

## Findings (Council-Validated)

| # | Status | Finding | File:line | Sev | CMP | Coro | Data |
|---|--------|---------|-----------|-----|-----|------|------|
| F1 | ⬜ | `BaseRepository.ioDispatcher = Dispatchers.Default` — DB+net on compute pool. **KMP nuance**: `Dispatchers.IO` doesn't exist on native; needs `expect/actual` | `BaseRepository.kt:33` | 🔴 | — | ⚠️ | ✅ |
| F2 | ⬜ | Stale `currentDate`/`startTime`/`endTime` frozen at VM init; never updates → events miss after midnight or scroll past `endTime`. **`DateStateHolder.resetToToday()` exists but is never called** | `CalendarViewModel.kt:52-56, 110` | 🔴 | — | ✅ | — |
| F3 | ⬜ | `ScheduleStateHolder._items` is `SnapshotStateList` (snapshot-safe, NOT thread-safe). Three parallel `launch` blocks in `ScheduleScreen` mutate it concurrently → corruption under stress | `ScheduleStateHolder.kt:35, 66-110`; `ScheduleScreen.kt:87-167` | 🔴 | ✅ | ✅ | — |
| F4 | ⬜ | `CacheTimestampTracker` global `mutableMapOf`, no lock | `StoreValidator.kt:29-50` | 🟠 | — | ✅ | ✅ |
| F5 | ⬜ | TTL validators (`EventValidator.isStale`, `HolidayValidator.isStale`, `recordFetch`, `invalidate`) **never called**. Store cache never expires by age — only by explicit `clear()` | `StoreValidator.kt:78-131` | 🟠 | — | ✅ | ✅ |
| F6 | ⬜ | `StoreBookkeeper` read-then-write race: `getFailure()` → branch → `insert`/`increment`. **REPLACE strategy hides race, not benign** — failure count can regress. Mitigated only by Store5's single-dispatcher contract (undocumented) | `StoreBookkeeper.kt:30-43, 89-102` | 🟠 | — | ⚠️ | ✅ |
| F7 | ⬜ | Duplicate holiday flow chain — `holidays` shared flow + `initializeHolidays()` collect re-run the same `flatMapLatest/combine`. Refresh-on-empty should live in Fetcher, not VM | `CalendarViewModel.kt:75-96, 182-209` | 🟡 | — | ✅ | — |
| F8 | ⬜ | Store5 Fetchers call API without `withContext(IO)`. Compounds F1 | `EventStore.kt:55-72`, `HolidayStore.kt:41-56` | 🟠 | — | ✅ | ✅ |
| F9 | ⬜ | `groupOverlappingEvents` O(N²) `sortedBy` + nested `firstOrNull`, recomputed per recomposition per visible day column | `CalendarEventsGrid.kt:162, 205-230` | 🟠 | ✅ | — | — |
| F10 | ⬜ | TZ conversion `event.startTime.toLocalDateTime(TimeZone.currentSystemDefault())` per event per frame in grouping + draw paths | `CalendarEventsGrid.kt:169-171`; `SwipeableCalendarView.kt:69,79`; `MonthView.kt:50-54`; `ScheduleStateHolder.kt:120-126` | 🟠 | ✅ | — | — |
| F11 | ⬜ | `LazyVerticalGrid` missing `contentType=` → no cell recycling across month swipes. Each `DayCell` allocates own `rememberScrollState()` → ~42×3 = **126 scroll states preloaded** | `MonthView.kt:92-187`; `DayCell.kt:89` | 🟠 | ✅ | — | — |
| F12 | ⬜ | `ScheduleScreen` pagination: three parallel `snapshotFlow` + `delay(100)`/`delay(500)` magic numbers + linear `.firstOrNull` scans | `ScheduleScreen.kt:87-167` | 🟠 | ✅ | ✅ | — |
| F13 | ⬜ | `DaysHeaderRow.onGloballyPositioned { state.value = … }` + `BaseCalendarScreen.animateContentSize()` → layout→state-write→recomposition→layout loop with AnimationVector alloc/frame | `DaysHeaderRow.kt:70-73`; `BaseCalendarScreen.kt:73,89` | 🟠 | ✅ | — | — |
| F14 | ⬜ | `Clock.System.now()`/`.uppercase()`/`.toLocalDateTime` not memoized in headers | `WeekdayHeader.kt:27-31`; `WeekHeader.kt:24` | 🟡 | ✅ | — | — |
| F15 | ⬜ | `remember { Clock.System.now()…date }` with **no key** → "today" frozen for app lifetime. Visually wrong after midnight | `DayCell.kt:60-65` (and originally suspected `DayWithEvents.kt:38-44`) | 🟡 | ✅ | — | — |
| F16 | ⬜ | `noRippleClickable()` uses `composed { ... MutableInteractionSource() }` → fresh InteractionSource per cell per frame (42×3 cells). Should migrate to `Modifier.Node` API (Compose 1.6+) | `modifierExtension.kt:9-17`; consumed by `DayCell.kt:81` | 🟠 | ✅ | — | — |
| F17 | ⬜ | `MaterialShapes.Cookie9Sided.toShape()` allocates per cell per recomposition. Hoist to module-level `val` | `DayCell.kt:103`; `BaseCalendarScreen.kt:124` | 🟡 | ✅ | — | — |
| F18 | ⬜ | `combine(5)` + `debounce(30)` + `distinctUntilChanged` over full immutable lists → structural-equality cost on every emit; `.toImmutableList()` allocates per field per emit | `CalendarViewModel.kt:122-143` | 🟡 | — | ⚠️ | — |
| F19 | 🚫 | ~~`runCatching` swallows `CancellationException`~~ — **REFUTED**: modern Kotlin (1.6+) makes `CancellationException` escape `runCatching` automatically. Pattern is safe; smell only | `CalendarViewModel.kt:174-228` | 🟢 | — | ❌ | — |
| F20 | ⬜ | `DateStateHolder` `@Single` outlives VM; orphaned collectors + stale `selectedInViewMonth` after process restore. `onCleared()` never resets | `DateStateHolder.kt`; `CalendarViewModel.kt:258-261` | 🟡 | — | ✅ | — |
| F21 | ⬜ | `CalendarRepository`/`UserRepository` bypass Store5 → no coalescing, no TTL parity | `CalendarRepository.kt`; `UserRepository.kt` | 🟡 | — | — | ✅ |
| F22 | ⬜ | Event delete = DAO delete + `singleEventStore.clear(key)` outside Room transaction → cache/DB desync on partial failure | `EventRepository.kt:131-144` | 🟡 | — | — | ✅ |
| F23 | ⬜ | `Stores` + `Bookkeepers` `@Single` keyed on userId, but `CacheTimestampTracker` is **process-global without user partitioning** — multi-account leaks across users | `Koin.kt:103-138`; `StoreValidator.kt:29` | 🟠 | — | — | ➕ ✅ |
| F24 | ⬜ | Hardcoded country `"IN"` for holidays | `CalendarViewModel.kt:82-84, 191-193` | 🟢 | — | — | — |
| F25 | ⬜ | `collectAsState()` instead of `collectAsStateWithLifecycle()` — Android-only concern; commonMain can't use lifecycle-runtime-compose directly. Needs `expect/actual` shim | `ScheduleScreen.kt:42`; `BaseCalendarScreen.kt:65` | 🟡 | ⚠️ | ⚠️ | — |
| F26 | ⬜ | Ktor retry: 408 `REQUEST_TIMEOUT` caught as error, **never retried** (`HttpRequestRetry` only matches 5xx) | `Koin.kt:74-81`; `ClientWrapper.kt:56-57` | 🟡 | — | — | ✅ |
| F27 | ⬜ | `CalendarUiState` swap-on-copy invalidates every consumer even when only one field changed → wide recomposition | `CalendarViewModel.kt`; `CalendarUiState.kt` | 🟡 | ➕ ✅ | ✅ | — |
| F28 | ⬜ | `EventKey` range-keyed by exact `startTime:endTime` → every calendar pan = cache miss. Quantize to month buckets | `StoreKeys.kt:16-20`; `EventRepository.kt:75-91` | 🟠 | — | — | ➕ ✅ |
| F29 | ⬜ | **No outbox pattern**: `EventRepository.create/update/delete` persist locally only (backend writes commented out at `EventStoreFactory.kt:100-123`). User sees "saved", server never receives. When backend ships, writes orphaned | `EventStoreFactory.kt:100-123`; `Updater.kt:117` | 🔴 | — | — | ➕ ✅ |
| F30 | ⬜ | **No Room migrations**: `MIGRATIONS` array empty, `DATABASE_VERSION = 1`. Any future schema bump crashes app on upgrade | `AppDatabase.kt:19, 52-55` | 🟠 | — | — | ➕ ✅ |
| F31 | ⬜ | **No DB encryption** on any platform; SQLite plaintext on disk. iOS keychain wrap absent | `AppDatabase.kt` (all platforms) | 🟠 | — | — | ➕ ✅ |
| F32 | ⬜ | **Desktop Room context = `Dispatchers.Default`** (wrong — should be `IO`). Android correct (`IO`). iOS implicit Main (safe) | `Koin.desktop.kt:28` | 🟠 | — | — | ➕ ✅ |
| F33 | ⬜ | Domain models (`Event`, `Holiday`, `Calendar`, `User`) likely lack `@Immutable`/`@Stable` → composables consuming them flagged unstable by Compose Compiler → unnecessary recomposition | `domain/model/*.kt` | 🟠 | ➕ ✅ | — | — |
| F34 | ⬜ | `HorizontalPager` has no `beyondViewportPageCount` → no neighbour preload, jank on fast swipe | `SwipeablePager.kt:87-92` | 🟡 | ➕ ✅ | — | — |
| F35 | ⬜ | `ImmutableList`/`ImmutableMap` lookup keyed `eventsByDate[date]` — if parent passes new map reference each recomposition (even unchanged content) → slot-table churn. Wrap with `@Stable data class` | `CalendarEventsGrid.kt:85, 118`; `SwipeableCalendarView.kt:65-72` | 🟡 | ➕ ✅ | — | — |
| F36 | ⬜ | `viewModelScope.launch` inside `.collect` at `CalendarViewModel.kt:200` — NOT a leak (scoped) but **imperative refresh-on-empty smell**; piles up if year toggled fast | `CalendarViewModel.kt:196-208` | 🟡 | — | ⚠️ | — |
| F37 | ⬜ | Calendar API URL via `raw.githubusercontent.com/Debanshu777/XCalendar/main/` — implicit dependency on author's GitHub repo. Brittle. Add base-URL config | `RemoteCalendarApiService.kt:16` | 🟡 | — | — | ➕ ✅ |

---

## Council headline disagreements & nuance

**CMP-Arch dissent**
- F25 (collectAsStateWithLifecycle) only matters on Android; commonMain can't import lifecycle-runtime-compose directly. Solution = `expect/actual`, not flat replace.

**Coro-Arch dissent**
- F19 (runCatching + CancellationException) refuted. Kotlin 1.6+ makes `CancellationException` skip `runCatching` automatically. Pattern is safe.
- F6 (Bookkeeper race) confirmed at code level but **mitigated in practice** by Store5's single-executor contract. Document, don't necessarily fix.
- F1 (Dispatchers.Default) confirmed but native targets have no `Dispatchers.IO`. Must use `expect/actual`.

**Data-Arch additions**
- F23 (multi-account leak via global `CacheTimestampTracker`) — louder than the original concern.
- F29 (no outbox) — escalated to 🔴 because UI lies to user about save success.
- F30/F31 (no migrations, no encryption) — production-blocker class issues.
- F32 (desktop Dispatcher wrong) — concrete bug, easy fix.

**CMP-Arch additions**
- F33 (stability annotations) — cheapest win for recomposition count.
- F34 (`beyondViewportPageCount = 1`) — one-line UX fix.
- F35 (stable wrappers for grouped maps) — eliminates slot-table churn.

---

# Phased Execution Plan

Each phase = one PR (or small stack). Phases sequenced by **dependency, blast radius, and risk-of-regression**, not by raw severity. Foundation first, then perf, then polish.

---

## Phase 0 — Verification harness (prereq)

**Status**: ✅ Done (commit pending)
**Goal**: catch regressions in subsequent phases.

| Step | Status | Description |
|------|--------|-------------|
| 0.1 | ✅ | Compose Compiler metrics already configured (`composeApp/build.gradle.kts:38-45`). Baseline captured at `docs/compose-metrics/baseline/` — 161 restartable / 92 skippable (52%), 40 unstable classes. |
| 0.2 | ✅ | Added `kotlinx-coroutines-debug` to `desktopTest` source set (`libs.versions.toml` + `composeApp/build.gradle.kts`). Available for `DebugProbes.install()` in JVM tests. |
| 0.3 | ✅ | `Modifier.recomposeHighlighter()` added at `composeApp/src/commonMain/kotlin/com/debanshu/xcalendar/common/RecomposeHighlighter.kt`. Debug-only — uses `composed { }` itself (acceptable here). |
| 0.4 | ✅ | `atomicfu = "0.27.0"` wired in `libs.versions.toml`; added to `commonMain`. Ready for Phase 2 `AtomicReference<PersistentHashMap>` in `CacheTimestampTracker`. |
| 0.5 | ✅ | `ScheduleStateHolderPerfBaselineTest` seeded — init 36 ms, refresh 1 ms, 6× loadMoreForward 5 ms. See `docs/compose-metrics/baseline/perf-baselines.md`. |
| 0.6 | 🟦 | Commit Phase 0. |

**Exit criteria**: baseline metrics archived ✅, repro stress test passing on current main ✅.

---

## Phase 1 — Correctness foundation 🔴

**Status**: ⬜
Addresses F1, F2, F32.

1. **F1 + F32** — Convert `BaseRepository.ioDispatcher` to KMP `expect/actual val ioDispatcher: CoroutineDispatcher`.
   - androidMain: `Dispatchers.IO`
   - desktopMain: `Dispatchers.IO`
   - iosMain: `Dispatchers.Default` (no IO on native; document)
   - Fix `Koin.desktop.kt:28` Room context to `Dispatchers.IO`.
2. **F2** — Make date range reactive:
   - Drop `currentDate`/`startTime`/`endTime` from VM fields.
   - `events` flow becomes `dateStateHolder.currentDateState.flatMapLatest { ... getEventsForDateRangeUseCase(...) }` with sliding monthly window.
   - Wire midnight tick into `DateStateHolder` so `resetToToday()` fires when day rolls over.

**Verification**: mock-clock midnight test asserts new events appear; manual scroll past prior `endTime` loads events.

---

## Phase 2 — Thread safety 🔴🟠

**Status**: ⬜
Addresses F3, F4, F8, F23.

1. **F3** — `ScheduleStateHolder`:
   - Wrap `loadMoreBackward/Forward/refreshItems` in `Mutex.withLock`.
   - Collapse 3 parallel `snapshotFlow` collectors in `ScheduleScreen.kt:87-167` into single `snapshotFlow { firstVisible to lastVisible }.distinctUntilChanged().debounce(150).collect { ... }`.
   - Replace `delay(100/500)` with `.debounce(...)` upstream.
2. **F4** — `CacheTimestampTracker`: replace `mutableMapOf` with `atomicfu` `AtomicReference<PersistentHashMap>` (or `Mutex`).
3. **F8** — Wrap Fetchers in `withContext(ioDispatcher) { apiService.fetch*() }`.
4. **F23** — Partition `CacheTimestampTracker` keys by `userId` (full scoping in Phase 9).

**Verification**: 100-thread `launch { loadMoreForward/Backward }` fuzz asserts items unique & monotonic on `Dispatchers.Default`.

---

## Phase 3 — Data integrity 🔴🟠

**Status**: ⬜
Addresses F29, F30, F22, F6, F26.

1. **F29** — Add `pending_writes` (outbox) table. Worker drains when network present. Don't ship "save success" for an op that won't sync.
2. **F30** — Add MIGRATION scaffolding + CI check.
3. **F22** — Wrap event delete in `@Transaction` DAO.
4. **F6** — Replace bookkeeper read-modify-write with `INSERT … ON CONFLICT(key) DO UPDATE SET failureCount = failureCount + 1`. Document Store5 single-executor assumption.
5. **F26** — Add `retryOnExceptionIf { it is HttpRequestTimeoutException }` to Ktor `HttpRequestRetry`.

**Verification**: kill network mid-save → restart → outbox flushes. Schema migration unit test. Stress test 50 concurrent fetch failures → assert failureCount == 50.

---

## Phase 4 — Cache lifecycle 🟠

**Status**: ⬜
Addresses F5, F28, F7.

1. **F5** — Wire `EventValidator.isStale(key)` into Fetcher + call `recordFetch(key)` on Fetcher success.
2. **F28** — Quantize `EventKey` to month buckets.
3. **F7** — Move refresh-on-empty into `HolidayStore` Fetcher; delete duplicated `initializeHolidays()` chain.

**Verification**: integration test — refetch after TTL with injected `Clock`. Pan calendar 30 days back/forth → single network call per month.

---

## Phase 5 — Architectural cleanup 🟡

**Status**: ⬜
Addresses F20, F21, F25, F36, F37.

1. **F20** — Call `dateStateHolder.resetToToday()` in `CalendarViewModel.onCleared()` (or scope `DateStateHolder` to nav-graph).
2. **F21** — Document why Calendar/User bypass Store5 (DB is SoT, no remote sync).
3. **F25** — `expect/actual` `@Composable fun <T> StateFlow<T>.collectAsStateLifecycle(): State<T>`.
4. **F36** — Subsumed by F7.
5. **F37** — Externalize API base URL into `BuildKonfig`.

---

## Phase 6 — Compose perf, foundational 🟠

**Status**: ⬜
Addresses F33, F35, F18, F27.

1. **F33** — Annotate domain models with `@Immutable`. Re-run Compose Compiler metrics.
2. **F35** — Wrap grouped maps with `@Stable data class EventsByDate(val map: ImmutableMap<...>)`.
3. **F18 + F27** — Split `CalendarUiState` into per-concern `StateFlow`s. Consumer recomposes only on its slice.

**Verification**: Compose Compiler reports — `restartable skippable` count rises. Recomposition highlighter shows fewer "yellow" cells.

---

## Phase 7 — Compose perf, hot path 🟠

**Status**: ⬜
Addresses F9, F10, F11, F13, F16, F17.

1. **F9** — Rewrite `groupOverlappingEvents` as sweep-line O(N log N).
2. **F10** — Precompute `localStartTime`/`localEndTime` once when events enter UI layer.
3. **F11** — Add `contentType` to `LazyVerticalGrid.items`. Remove per-cell `rememberScrollState()`.
4. **F13** — Replace `onGloballyPositioned { state.value = … }` + `animateContentSize()` with `Modifier.layout` (no state-from-layout).
5. **F16** — Migrate `noRippleClickable` to `Modifier.Node` API. Shared `MutableInteractionSource` per cell via `remember(Unit)` outside `composed { }`.
6. **F17** — Hoist `MaterialShapes.Cookie9Sided.toShape()` to module-level `val COOKIE_SHAPE`.

**Verification**: frame-time benchmark vs Phase 0 baseline. Target 60fps on Pixel 6 with 500-event month.

---

## Phase 8 — Compose perf, polish 🟡

**Status**: ⬜
Addresses F12 (residue), F14, F15, F34.

1. **F12** — Validate consolidation from Phase 2; replace remaining linear `.firstOrNull` with index map.
2. **F14** — Memoize `Clock.System.now()` + `.uppercase()` in headers.
3. **F15** — Inject `today: LocalDate` from `DateStateHolder` into `DayCell`.
4. **F34** — Add `beyondViewportPageCount = 1` to `HorizontalPager`.

---

## Phase 9 — Security & multi-user 🟠

**Status**: ⬜
Addresses F31, F23 (final), F24.

1. **F31** — SQLCipher on Android+Desktop; iOS Keychain-wrap DB key.
2. **F23 final** — Scope all stores/bookkeepers/`CacheTimestampTracker` into Koin `scoped { }` under `UserScope`. Login = create scope; logout = close scope.
3. **F24** — Pull country code from user prefs / system locale.

---

# Critical-files cheat sheet

| Phase | Files (most touched) |
|-------|---------------------|
| 1 | `BaseRepository.kt`, `Koin.desktop.kt`, `CalendarViewModel.kt`, `DateStateHolder.kt` |
| 2 | `ScheduleStateHolder.kt`, `ScheduleScreen.kt`, `StoreValidator.kt`, `EventStore.kt`, `HolidayStore.kt` |
| 3 | `EventRepository.kt`, `StoreBookkeeper.kt`, `AppDatabase.kt`, `Koin.kt` (Ktor), `EventDao.kt` |
| 4 | `StoreValidator.kt`, `StoreKeys.kt`, `EventStore.kt`, `HolidayStore.kt`, `CalendarViewModel.kt` |
| 5 | `CalendarViewModel.kt`, `DateStateHolder.kt`, `Koin.kt`, new `LifecycleCollect.kt` (expect/actual) |
| 6 | `domain/model/*.kt`, `CalendarUiState.kt`, `CalendarViewModel.kt`, `SwipeableCalendarView.kt` |
| 7 | `CalendarEventsGrid.kt`, `MonthView.kt`, `DayCell.kt`, `BaseCalendarScreen.kt`, `DaysHeaderRow.kt`, `modifierExtension.kt` |
| 8 | `ScheduleScreen.kt`, `WeekdayHeader.kt`, `WeekHeader.kt`, `DayCell.kt`, `SwipeablePager.kt` |
| 9 | `AppDatabase.kt`, `Koin.kt`, `EventStore.kt`, `HolidayStore.kt`, `StoreBookkeeper.kt`, `CalendarViewModel.kt` |

---

# Verification matrix

| Phase | Test type | Tooling |
|-------|-----------|---------|
| 0 | Baseline capture | `kotlinx-coroutines-debug`, Compose Compiler reports, perf macrobench |
| 1 | Mock-clock midnight crossover | Inject `Clock` into `DateStateHolder` |
| 2 | Thread fuzz | 100 parallel ops on `Dispatchers.Default` |
| 3 | Crash-resume + outbox flush | Compose UI test with `ProcessDeath` simulation |
| 4 | TTL refetch | Mock `Clock` + Store stream collector |
| 5 | Lifecycle survival | Rotate device 100x; check `DebugProbes` for orphans |
| 6 | Compose metrics regression | CI gate on `restartable+skippable` count |
| 7 | Macrobench frame time | `androidx.benchmark.macro` on Pixel 6 |
| 8 | Pager smoothness | Macrobench swipe gesture |
| 9 | Encrypted DB + multi-user | Integration test: logout/login user B, assert user A cache invisible |

---

# Out of scope (acknowledged, deferred)

- Glass/shader GPU overdraw — needs GPU profiler, separate effort.
- iOS-specific perf (NSDateFormatter caching, Main queue dispatching).
- Desktop window-resize layout pass.
- Localization beyond country code.

---

# Change log

| Date | Note |
|------|------|
| 2026-05-21 | Initial audit + plan committed. Phase 0 started. |
| 2026-05-21 | Phase 0 complete. Baselines archived under `docs/compose-metrics/baseline/`. Coroutines-debug + atomicfu + RecomposeHighlighter wired. |
