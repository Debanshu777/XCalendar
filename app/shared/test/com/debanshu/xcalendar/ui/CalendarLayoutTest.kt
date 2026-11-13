package com.debanshu.xcalendar.ui

import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.test.TestDataFactory
import com.debanshu.xcalendar.ui.utils.indexEventsByDate
import com.debanshu.xcalendar.ui.utils.layoutDayEvents
import com.debanshu.xcalendar.ui.utils.monthGridDates
import com.debanshu.xcalendar.ui.state.ScheduleStateHolder
import com.debanshu.xcalendar.ui.state.DateStateHolder
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
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
    fun equalStartEventsKeepTheirOrderWhenServerOrderChanges() {
        val events = listOf(event("b", 60, 120), event("a", 60, 120))
        val first = indexEventsByDate(events, day, day, TimeZone.UTC)
        assertEquals(listOf("a", "b"), first[day]!!.map { it.id })
        assertEquals(first, indexEventsByDate(events.reversed(), day, day, TimeZone.UTC))
    }

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
    fun dateIndexClipsUsingLocalMidnightsAcrossDaylightSavingChanges() {
        val zone = TimeZone.of("America/New_York")
        for ((date, nextDate) in listOf(
            LocalDate(2026, 3, 8) to LocalDate(2026, 3, 9),
            LocalDate(2026, 11, 1) to LocalDate(2026, 11, 2),
        )) {
            val start = date.atStartOfDayIn(zone).toEpochMilliseconds()
            val end = nextDate.atStartOfDayIn(zone).toEpochMilliseconds()
            val events = listOf(
                TestDataFactory.createEvent(id = "before", startTime = start - 60_000, endTime = start),
                TestDataFactory.createEvent(id = "last-minute", startTime = end - 60_000, endTime = end),
                TestDataFactory.createEvent(id = "after", startTime = end, endTime = end + 60_000),
                TestDataFactory.createEvent(id = "spanning", startTime = start - 60_000, endTime = end + 60_000),
            )
            val index = indexEventsByDate(events, date, date, zone)
            assertEquals(setOf(date), index.keys)
            assertEquals(listOf("spanning", "last-minute"), index.getValue(date).map { it.id })
        }
    }

    @Test
    fun dateIndexPreservesPointEventsAndSkipsEmptyWindows() {
        val events = listOf(event("point", 60, 60), event("reversed", 90, 30), event("tomorrow", 1440, 1440))
        assertEquals(listOf("point", "reversed"), indexEventsByDate(events, day, day, TimeZone.UTC).getValue(day).map { it.id })
        assertTrue(indexEventsByDate(events, LocalDate(2026, 11, 9), day, TimeZone.UTC).isEmpty())
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
    fun allDayEventsDoNotConsumeTimedColumnsOrJoinSeparateOverlapGroups() {
        val allDay = event("all-day", 0, 1440).copy(isAllDay = true)
        val multiDay = event("multi-day", -1440, 2880).copy(isAllDay = true)
        val placements = layoutDayEvents(listOf(
            allDay, multiDay, event("early", 60, 120), event("adjacent", 120, 180),
            event("overlap-a", 200, 300), event("overlap-b", 250, 350),
        ), day, 0..23, TimeZone.UTC)

        assertEquals(listOf("early", "adjacent", "overlap-a", "overlap-b"), placements.map { it.event.id })
        assertEquals(listOf(1, 1, 2, 2), placements.map { it.columnCount })
        assertTrue(layoutDayEvents(listOf(allDay, multiDay), day, 0..23, TimeZone.UTC).isEmpty())
    }

    @Test
    fun allDayHeaderIndexIncludesCoveredDaysAndExcludesTheMidnightEnd() {
        val allDay = event("two-days", 0, 2880).copy(isAllDay = true)
        val index = indexEventsByDate(listOf(allDay), LocalDate(2026, 11, 7), LocalDate(2026, 11, 11), TimeZone.UTC)

        assertEquals(setOf(day, LocalDate(2026, 11, 9)), index.keys)
        assertTrue(index.values.all { it.single() == allDay })
    }

    @Test
    fun timeGridClipsOvernightAndPartiallyVisibleEvents() {
        val placement = layoutDayEvents(listOf(event("overnight", -60, 600)), day, 8..9, TimeZone.UTC).single()
        assertEquals(480, placement.startMinute)
        assertEquals(600, placement.endMinute)
    }

    @Test
    fun subMinuteEventsRemainVisibleAndPartialEndMinutesAreIncluded() {
        val short = TestDataFactory.createEvent(
            id = "sub-minute", startTime = midnight + 600_125L, endTime = midnight + 630_250L,
        )
        val placements = layoutDayEvents(listOf(short, event("partial-end", 20, 30).copy(
            endTime = midnight + 30 * 60_000L + 1L,
        )), day, 0..23, TimeZone.UTC)

        assertEquals(listOf(10, 20), placements.map { it.startMinute })
        assertEquals(listOf(11, 31), placements.map { it.endMinute })
        assertEquals(short, placements.first().event)
    }

    @Test
    fun repeatedClockHourUsesElapsedDurationWithoutChangingEventInstants() {
        val foldDay = LocalDate(2026, 11, 1)
        val original = TestDataFactory.createEvent(
            // 01:45 EDT to 01:15 EST is a positive 30-minute interval.
            startTime = LocalDateTime.parse("2026-11-01T05:45").toInstant(TimeZone.UTC).toEpochMilliseconds(),
            endTime = LocalDateTime.parse("2026-11-01T06:15").toInstant(TimeZone.UTC).toEpochMilliseconds(),
        )
        val placement = layoutDayEvents(listOf(original), foldDay, 0..23, TimeZone.of("America/New_York")).single()

        assertEquals(105, placement.startMinute)
        assertEquals(135, placement.endMinute)
        assertEquals(original, placement.event)

        val sameClockTime = original.copy(
            endTime = LocalDateTime.parse("2026-11-01T06:45").toInstant(TimeZone.UTC).toEpochMilliseconds(),
        )
        val repeated = layoutDayEvents(listOf(sameClockTime), foldDay, 0..23, TimeZone.of("America/New_York")).single()
        assertEquals(105, repeated.startMinute)
        assertEquals(165, repeated.endMinute)

        // Intervals with increasing wall time retain that placement on the
        // fixed grid even when a clock change alters their elapsed duration.
        val spanningFold = original.copy(
            startTime = LocalDateTime.parse("2026-11-01T04:30").toInstant(TimeZone.UTC).toEpochMilliseconds(),
            endTime = LocalDateTime.parse("2026-11-01T07:30").toInstant(TimeZone.UTC).toEpochMilliseconds(),
        )
        val spanning = layoutDayEvents(listOf(spanningFold), foldDay, 0..23, TimeZone.of("America/New_York")).single()
        assertEquals(30, spanning.startMinute)
        assertEquals(150, spanning.endMinute)
    }

    @Test
    fun emptyReversedAndNonIntersectingIntervalsDoNotCreateGridCards() {
        val events = listOf(
            event("empty", 10, 10), event("reversed", 20, 10),
            event("ends-at-midnight", -60, 0), event("starts-tomorrow", 1440, 1500),
        )
        assertTrue(layoutDayEvents(events, day, 0..23, TimeZone.UTC).isEmpty())
        val endsAtMidnight = layoutDayEvents(listOf(event("ends-next-midnight", 1380, 1440)), day, 0..23, TimeZone.UTC).single()
        assertEquals(1440, endsAtMidnight.endMinute)
    }

    @Test
    fun scheduleIncludesEveryDayOfAnOvernightEvent() {
        val localMidnight = day.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val overnight = TestDataFactory.createEvent(startTime = localMidnight - 3_600_000, endTime = localMidnight + 3_600_000)
        val schedule = ScheduleStateHolder(YearMonth(2026, 11), { listOf(overnight) }, { emptyList() })
        val dates = schedule.items.filterIsInstance<ScheduleItem.DayEvents>().map { it.date }
        assertEquals(listOf(LocalDate(2026, 11, 7), day), dates)
    }

    @Test
    fun todayRolloverPreservesSelectionAndViewport() {
        val dates = DateStateHolder()
        dates.updateSelectedDateState(day)
        val viewedMonth = YearMonth(2026, 12)
        dates.updateSelectedInViewMonthState(viewedMonth)

        val tomorrow = LocalDate(2027, 1, 1)
        dates.updateToday(tomorrow)

        assertEquals(tomorrow, dates.today)
        assertEquals(day, dates.currentDateState.value.selectedDate)
        assertEquals(viewedMonth, dates.currentDateState.value.selectedInViewMonth)
    }

    @Test
    fun scheduleCanJumpToAnEventDayOrItsWeek() {
        val zone = TimeZone.currentSystemDefault()
        val start = day.atStartOfDayIn(zone).toEpochMilliseconds()
        val event = TestDataFactory.createEvent(startTime = start, endTime = start + 3_600_000)
        val schedule = ScheduleStateHolder(YearMonth(2026, 11), { listOf(event) }, { emptyList() })
        val eventRow = schedule.items[schedule.scrollIndexFor(day)] as ScheduleItem.DayEvents
        assertEquals(day, eventRow.date)
        assertEquals(YearMonth(2026, 11), eventRow.month)

        val outsideRange = LocalDate(2027, 5, 8)
        val weekRow = schedule.items[schedule.scrollIndexFor(outsideRange)] as ScheduleItem.WeekHeader
        assertTrue(outsideRange in weekRow.startDate..weekRow.endDate)
        assertEquals(YearMonth(2027, 5), weekRow.month)
    }
}
