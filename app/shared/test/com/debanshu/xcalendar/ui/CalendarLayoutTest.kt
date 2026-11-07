package com.debanshu.xcalendar.ui

import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.test.TestDataFactory
import com.debanshu.xcalendar.ui.utils.indexEventsByDate
import com.debanshu.xcalendar.ui.utils.layoutDayEvents
import com.debanshu.xcalendar.ui.utils.monthGridDates
import com.debanshu.xcalendar.ui.state.ScheduleStateHolder
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarLayoutTest {
    private val day = LocalDate(2026, 11, 8)
    private val midnight = day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    private fun event(id: String, startMinute: Int, endMinute: Int) = TestDataFactory.createEvent(
        id = id, startTime = midnight + startMinute * 60_000L, endTime = midnight + endMinute * 60_000L,
    )

    @Test
    fun sundayStartDoesNotInsertAnEmptyWeek() {
        val dates = monthGridDates(YearMonth(2026, 11))
        assertEquals(42, dates.size)
        assertEquals(LocalDate(2026, 11, 1), dates.first())
        assertEquals(LocalDate(2026, 12, 12), dates.last())
        assertEquals(42, dates.toSet().size)
    }

    @Test
    fun leapMonthAndYearRolloverKeepConsecutiveDates() {
        assertTrue(LocalDate(2024, 2, 29) in monthGridDates(YearMonth(2024, 2)))
        val dates = monthGridDates(YearMonth(2027, 1))
        assertEquals(LocalDate(2026, 12, 27), dates.first())
        assertTrue(dates.zipWithNext().all { (a, b) -> b.toEpochDays() - a.toEpochDays() == 1L })
    }

    @Test
    fun overnightEventsAppearOnBothDaysButMidnightEndsAreExclusive() {
        val overnight = event("overnight", -60, 60)
        val endsAtMidnight = event("midnight", -60, 0)
        val index = indexEventsByDate(listOf(overnight, endsAtMidnight), LocalDate(2026, 11, 7), day, TimeZone.UTC)
        assertEquals(listOf("overnight"), index[day]!!.map { it.id })
        assertEquals(2, index[LocalDate(2026, 11, 7)]!!.size)
    }

    @Test
    fun expansionIsBoundedByTheRequestedWindow() {
        val longEvent = TestDataFactory.createEvent(startTime = 0, endTime = Long.MAX_VALUE / 1000)
        val index = indexEventsByDate(listOf(longEvent), day, day, TimeZone.UTC)
        assertEquals(setOf(day), index.keys)
    }

    @Test
    fun adjacentEventsUseFullWidthAndOverlapsHaveDistinctColumns() {
        val placements = layoutDayEvents(listOf(event("a", 60, 120), event("b", 120, 180),
            event("c", 200, 300), event("d", 250, 350)), day, 0..23, TimeZone.UTC)
        assertEquals(listOf(1, 1, 2, 2), placements.map { it.columnCount })
        assertEquals(listOf(0, 1), placements.takeLast(2).map { it.column })
    }

    @Test
    fun columnsAreReusedWithinAConnectedOverlapGroup() {
        val placements = layoutDayEvents(listOf(event("long", 60, 240), event("short", 60, 120),
            event("next", 120, 180)), day, 0..23, TimeZone.UTC)
        assertEquals(listOf(2, 2, 2), placements.map { it.columnCount })
        assertEquals(placements[1].column, placements[2].column)
    }

    @Test
    fun timeGridClipsOvernightAndPartiallyVisibleEvents() {
        val placement = layoutDayEvents(listOf(event("overnight", -60, 600)), day, 8..9, TimeZone.UTC).single()
        assertEquals(480, placement.startMinute)
        assertEquals(600, placement.endMinute)
    }

    @Test
    fun scheduleIncludesEveryDayOfAnOvernightEvent() {
        val localMidnight = day.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val overnight = TestDataFactory.createEvent(startTime = localMidnight - 3_600_000, endTime = localMidnight + 3_600_000)
        val schedule = ScheduleStateHolder(YearMonth(2026, 11), { listOf(overnight) }, { emptyList() })
        val dates = schedule.items.filterIsInstance<ScheduleItem.DayEvents>().map { it.date }
        assertEquals(listOf(LocalDate(2026, 11, 7), day), dates)
    }
}
