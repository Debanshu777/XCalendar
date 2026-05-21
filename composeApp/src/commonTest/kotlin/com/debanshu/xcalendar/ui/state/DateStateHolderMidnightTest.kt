package com.debanshu.xcalendar.ui.state

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Verifies that [DateStateHolder] advances `currentDate` when wall-clock
 * crosses midnight (audit F2). Uses an injected [Clock] so the test can
 * march time forward deterministically without sleeping.
 */
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class DateStateHolderMidnightTest {

    private class FakeClock(initial: Instant) : Clock {
        var now: Instant = initial
        override fun now(): Instant = now
    }

    private val tz = TimeZone.UTC

    @Test
    fun `midnightTicker advances currentDate after wall clock crosses midnight`() = runTest {
        // 2026-05-21 22:00 UTC
        val start = LocalDateTime(2026, Month.MAY, 21, 22, 0).toInstant(tz)
        val clock = FakeClock(start)
        val holder = DateStateHolder(clock = clock, timeZoneProvider = { tz })

        assertEquals(LocalDate(2026, Month.MAY, 21), holder.today, "Initial today wrong")

        // Wire ticker → resetToToday like the production VM does.
        val job = launch {
            holder.midnightTicker().collect {
                holder.resetToToday()
            }
        }

        // Let the launched collector compute its first delay (2h to midnight)
        // *before* we move the wall clock — otherwise it reads the post-midnight
        // time on startup and computes the *next* day's midnight, skipping the
        // event under test.
        advanceTimeBy(1)
        clock.now = LocalDateTime(2026, Month.MAY, 22, 0, 0, 1).toInstant(tz)
        advanceTimeBy(2 * 60 * 60 * 1000L + 1_000L)

        val newToday = holder.currentDateState.first().currentDate
        assertEquals(LocalDate(2026, Month.MAY, 22), newToday, "Today did not advance after midnight")

        job.cancel()
    }

    @Test
    fun `resetToToday preserves selected date if user moved off today`() {
        val start = LocalDateTime(2026, Month.MAY, 21, 10, 0).toInstant(tz)
        val clock = FakeClock(start)
        val holder = DateStateHolder(clock = clock, timeZoneProvider = { tz })

        val movedTo = LocalDate(2026, Month.JUNE, 15)
        holder.updateSelectedDateState(movedTo)
        assertEquals(movedTo, holder.currentDateState.value.selectedDate)

        // Clock advances a day; today changes but user's selection holds.
        clock.now = LocalDateTime(2026, Month.MAY, 22, 10, 0).toInstant(tz)
        holder.resetToToday()

        val state = holder.currentDateState.value
        assertEquals(LocalDate(2026, Month.MAY, 22), state.currentDate)
        assertEquals(movedTo, state.selectedDate, "User-selected date should not be clobbered")
    }

    @Test
    fun `resetToToday follows today forward when user was sitting on old today`() {
        val start = LocalDateTime(2026, Month.MAY, 21, 23, 30).toInstant(tz)
        val clock = FakeClock(start)
        val holder = DateStateHolder(clock = clock, timeZoneProvider = { tz })

        // User selection == old today.
        assertEquals(LocalDate(2026, Month.MAY, 21), holder.currentDateState.value.selectedDate)

        clock.now = LocalDateTime(2026, Month.MAY, 22, 0, 1).toInstant(tz)
        holder.resetToToday()

        val state = holder.currentDateState.value
        assertEquals(LocalDate(2026, Month.MAY, 22), state.currentDate)
        assertEquals(state.currentDate, state.selectedDate, "Selection should follow today forward")
    }

    @Suppress("UNUSED_PARAMETER")
    private fun touch(scope: TestScope) = Unit
}
