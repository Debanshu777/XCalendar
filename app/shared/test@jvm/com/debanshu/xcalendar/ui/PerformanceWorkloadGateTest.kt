package com.debanshu.xcalendar.ui

import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.test.TestDataFactory
import com.debanshu.xcalendar.ui.screen.monthScreen.components.MonthCellPreviewCapacity
import com.debanshu.xcalendar.ui.screen.monthScreen.components.monthCellPreview
import com.debanshu.xcalendar.ui.utils.indexEventsByDate
import com.debanshu.xcalendar.ui.utils.monthGridDates
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Deterministic workload checks for the dense-year UI fixture; deliberately has no wall-clock limit. */
class PerformanceWorkloadGateTest {
    @Test
    fun denseYearLoadRefreshAndMonthPreviewsStayWithinWorkloadBounds() {
        val events = denseYearEvents()
        assertEquals(3_650, events.size, "The performance gate must retain ten starts on every day of 2026")

        // The UI benchmark fixture delivers the year in 100-event increments, then
        // refreshes every tenth title while preserving event identities.
        // Mirror the fixture's 100-event loading batches before applying its refresh policy.
        val loaded = events.chunked(100).flatten()
        assertEquals(3_650, loaded.size)

        val refreshed = loaded.mapIndexed { index, event ->
            if (index % 10 == 0) event.copy(title = "${event.title} [updated]") else event
        }
        assertEquals(loaded.map(Event::id), refreshed.map(Event::id), "Refresh must preserve identities")
        assertEquals(365, loaded.zip(refreshed).count { (before, after) -> before.title != after.title })

        val previewCapacity = MonthCellPreviewCapacity(withoutOverflow = 3, withOverflow = 3)
        val coveredYearDates = mutableSetOf<LocalDate>()
        for (month in 1..12) {
            val dates = monthGridDates(YearMonth(2026, month))
            assertEquals(42, dates.size)
            val eventsByDate = indexEventsByDate(refreshed, dates.first(), dates.last(), TimeZone.UTC)
            assertTrue(eventsByDate.keys.all { it in dates }, "Index must stay inside the displayed month grid")

            for (date in dates) {
                val dayEvents = eventsByDate[date].orEmpty()
                if (date.year == 2026) {
                    assertTrue(dayEvents.size >= 10, "Every 2026 date in the month grid must contain its ten fixture starts: $date")
                    coveredYearDates += date
                }
                val preview = monthCellPreview(
                    holidayCount = 0,
                    eventCount = dayEvents.size,
                    capacity = previewCapacity,
                )
                val visible = preview.holidays + preview.events
                assertTrue(visible <= 3, "A month cell must show at most three combined previews: $date")
                assertEquals(dayEvents.size, visible + preview.omitted, "Every indexed event must be visible or counted as omitted")
            }
        }
        assertEquals(365, coveredYearDates.size, "All actual 2026 dates must be covered by the twelve month grids")
    }

    private fun denseYearEvents(): List<Event> {
        val timeZone = TimeZone.UTC
        val events = ArrayList<Event>(3_650)
        var date = LocalDate(2026, 1, 1)
        val yearEnd = LocalDate(2027, 1, 1)
        var day = 0
        while (date < yearEnd) {
            val midnight = date.atStartOfDayIn(timeZone).toEpochMilliseconds()
            for (slot in 0 until 10) {
                val (startMinute, durationMinutes, allDay) = when (slot) {
                    in 0..5 -> Triple(510 + slot * 45, 90, false)
                    6 -> Triple(1_350, 180, false)
                    7 -> Triple(1_395, 180, false)
                    8 -> Triple(0, 1_440, true)
                    else -> Triple(0, 2_880, true)
                }
                val start = midnight + startMinute * 60_000L
                events += TestDataFactory.createEvent(
                    id = "year-2026-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}-$slot",
                    title = "Day $day event $slot",
                    startTime = start,
                    endTime = start + durationMinutes * 60_000L,
                    isAllDay = allDay,
                )
            }
            date = date.plus(DatePeriod(days = 1))
            day++
        }
        return events
    }
}
