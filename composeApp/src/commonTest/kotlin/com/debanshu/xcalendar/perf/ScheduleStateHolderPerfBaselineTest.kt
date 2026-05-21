package com.debanshu.xcalendar.perf

import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.test.TestDataFactory
import com.debanshu.xcalendar.ui.state.ScheduleStateHolder
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * Phase 0 perf baseline.
 *
 * Measures cold-path cost of [ScheduleStateHolder] initialization with a
 * realistic event payload (500 events × 12 months window). Numbers are
 * captured on the dev machine and used as a regression net for Phase 7
 * (Compose hot-path) and Phase 8 (polish).
 *
 * NOTE: This is a microbench, not a UI benchmark. It does not exercise
 * Compose recomposition. Compose-side benchmarks live in macrobench
 * (TODO Phase 7) and Compose Compiler metrics under docs/compose-metrics/.
 *
 * Baseline thresholds are intentionally loose — they catch >5x regressions,
 * not micro-regressions.
 */
@OptIn(ExperimentalTime::class)
class ScheduleStateHolderPerfBaselineTest {

    @Test
    fun `init 12-month window with 500 events completes within budget`() {
        val initialMonth = YearMonth(2026, 1)
        val events = TestDataFactory.createEvents(count = 500)
        val holidays = TestDataFactory.createHolidays(count = 30)

        val start = TimeSource.Monotonic.markNow()
        val holder = ScheduleStateHolder(
            initialMonth = initialMonth,
            getEvents = { events },
            getHolidays = { holidays },
        )
        val elapsed = start.elapsedNow()

        // Sanity: items were generated.
        assertTrue(holder.items.isNotEmpty(), "Items should be populated after init")
        // Generous budget (1s) — meant to catch >5x regressions, not micro changes.
        assertTrue(
            elapsed.inWholeMilliseconds < 1_000,
            "Init exceeded 1000ms budget: ${elapsed.inWholeMilliseconds}ms",
        )
        println("BASELINE: ScheduleStateHolder init = ${elapsed.inWholeMilliseconds}ms, items=${holder.items.size}")
    }

    @Test
    fun `loadMoreForward 6 months stays within budget`() {
        val initialMonth = YearMonth(2026, 1)
        val events = TestDataFactory.createEvents(count = 500)
        val holidays = TestDataFactory.createHolidays(count = 30)
        val holder = ScheduleStateHolder(
            initialMonth = initialMonth,
            getEvents = { events },
            getHolidays = { holidays },
        )

        val start = TimeSource.Monotonic.markNow()
        repeat(6) { holder.loadMoreForward() }
        val elapsed = start.elapsedNow()

        assertTrue(
            elapsed.inWholeMilliseconds < 500,
            "loadMoreForward x6 exceeded 500ms budget: ${elapsed.inWholeMilliseconds}ms",
        )
        println("BASELINE: 6x loadMoreForward = ${elapsed.inWholeMilliseconds}ms, items=${holder.items.size}")
    }

    @Test
    fun `refreshItems with 1000 events stays within budget`() {
        val initialMonth = YearMonth(2026, 1)
        var events = TestDataFactory.createEvents(count = 1000)
        val holidays = TestDataFactory.createHolidays(count = 30)
        val holder = ScheduleStateHolder(
            initialMonth = initialMonth,
            getEvents = { events },
            getHolidays = { holidays },
        )

        // Mutate event set and refresh.
        events = TestDataFactory.createEvents(count = 1000, startFromTimestamp = 1_800_000_000_000L)

        val start = TimeSource.Monotonic.markNow()
        holder.refreshItems()
        val elapsed = start.elapsedNow()

        assertTrue(
            elapsed.inWholeMilliseconds < 1_500,
            "refreshItems exceeded 1500ms budget: ${elapsed.inWholeMilliseconds}ms",
        )
        println("BASELINE: refreshItems 1000 events = ${elapsed.inWholeMilliseconds}ms, items=${holder.items.size}")
    }
}
