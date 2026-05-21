package com.debanshu.xcalendar.ui.model

import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for EventsByDate and HolidaysByDate grouping logic.
 * Verifies that the grouping transformation used in CalendarViewModel
 * correctly groups events and holidays by their local date.
 */
class DateGroupedCalendarMapsTest {
    private val testTimeZone = TimeZone.UTC

    @Test
    fun `EventsByDate groups events correctly by local date`() {
        // Create test events spanning multiple dates
        val events =
            listOf(
                Event(
                    id = "event1",
                    title = "Morning Event",
                    startTime = 1640995200000L, // 2022-01-01 00:00 UTC
                    endTime = 1640998800000L, // 2022-01-01 01:00 UTC
                    color = 0xFF0000FF.toInt(),
                    isAllDay = false,
                    calendarId = "cal1",
                    calendarName = "cal1",
                ),
                Event(
                    id = "event2",
                    title = "Afternoon Event",
                    startTime = 1641024000000L, // 2022-01-01 08:00 UTC
                    endTime = 1641027600000L, // 2022-01-01 09:00 UTC
                    color = 0xFF00FF00.toInt(),
                    isAllDay = false,
                    calendarId = "cal1",
                    calendarName = "cal1",
                ),
                Event(
                    id = "event3",
                    title = "Next Day Event",
                    startTime = 1641081600000L, // 2022-01-02 00:00 UTC
                    endTime = 1641085200000L, // 2022-01-02 01:00 UTC
                    color = 0xFFFF0000.toInt(),
                    isAllDay = false,
                    calendarId = "cal1",
                    calendarName = "cal1",
                ),
            )

        // Apply the same transformation as CalendarViewModel
        val eventsByDate =
            EventsByDate(
                events
                    .groupBy { event ->
                        event.startTime.toLocalDateTime(testTimeZone).date
                    }.mapValues { (_, eventList) ->
                        eventList.toImmutableList()
                    }.toImmutableMap(),
            )

        // Verify grouping
        val date1 = LocalDate(2022, 1, 1)
        val date2 = LocalDate(2022, 1, 2)

        val eventsOnDate1 = eventsByDate[date1]
        val eventsOnDate2 = eventsByDate[date2]

        assertEquals(2, eventsOnDate1.size, "Should have 2 events on 2022-01-01")
        assertEquals(1, eventsOnDate2.size, "Should have 1 event on 2022-01-02")
        assertTrue(eventsOnDate1.any { it.id == "event1" }, "Should contain morning event")
        assertTrue(eventsOnDate1.any { it.id == "event2" }, "Should contain afternoon event")
        assertTrue(eventsOnDate2.any { it.id == "event3" }, "Should contain next day event")
    }

    @Test
    fun `HolidaysByDate groups holidays correctly by local date`() {
        // Create test holidays
        val holidays =
            listOf(
                Holiday(
                    id = "holiday1",
                    name = "New Year",
                    date = 1640995200000L, // 2022-01-01 00:00 UTC
                    countryCode = "in",
                ),
                Holiday(
                    id = "holiday2",
                    name = "Independence Day",
                    date = 1656633600000L, // 2022-07-01 00:00 UTC
                    countryCode = "in",
                ),
            )

        // Apply the same transformation as CalendarViewModel
        val holidaysByDate =
            HolidaysByDate(
                holidays
                    .groupBy { holiday ->
                        holiday.date.toLocalDateTime(testTimeZone).date
                    }.mapValues { (_, holidayList) ->
                        holidayList.toImmutableList()
                    }.toImmutableMap(),
            )

        // Verify grouping
        val newYearDate = LocalDate(2022, 1, 1)
        val independenceDate = LocalDate(2022, 7, 1)

        val newYearHolidays = holidaysByDate[newYearDate]
        val independenceHolidays = holidaysByDate[independenceDate]

        assertEquals(1, newYearHolidays.size, "Should have 1 holiday on New Year")
        assertEquals(1, independenceHolidays.size, "Should have 1 holiday on Independence Day")
        assertEquals("New Year", newYearHolidays.first().name)
        assertEquals("Independence Day", independenceHolidays.first().name)
    }

    @Test
    fun `EventsByDate returns empty list for dates with no events`() {
        val eventsByDate =
            EventsByDate(
                mapOf<LocalDate, ImmutableList<Event>>().toImmutableMap(),
            )

        val emptyDate = LocalDate(2022, 12, 25)
        val result = eventsByDate[emptyDate]

        assertTrue(result.isEmpty(), "Should return empty list for dates with no events")
    }

    @Test
    fun `HolidaysByDate returns empty list for dates with no holidays`() {
        val holidaysByDate =
            HolidaysByDate(
                mapOf<LocalDate, ImmutableList<Holiday>>().toImmutableMap(),
            )

        val emptyDate = LocalDate(2022, 12, 25)
        val result = holidaysByDate[emptyDate]

        assertTrue(result.isEmpty(), "Should return empty list for dates with no holidays")
    }

    @Test
    fun `Multiple events on same date are preserved in order`() {
        val events =
            listOf(
                Event(
                    id = "event1",
                    title = "First Event",
                    startTime = 1640995200000L, // 2022-01-01 00:00 UTC
                    endTime = 1640998800000L,
                    color = 0xFF0000FF.toInt(),
                    isAllDay = false,
                    calendarId = "cal1",
                    calendarName = "cal1",
                ),
                Event(
                    id = "event2",
                    title = "Second Event",
                    startTime = 1641024000000L, // 2022-01-01 08:00 UTC
                    endTime = 1641027600000L,
                    color = 0xFF00FF00.toInt(),
                    isAllDay = false,
                    calendarId = "cal1",
                    calendarName = "cal1",
                ),
            )

        val eventsByDate =
            EventsByDate(
                events
                    .groupBy { event ->
                        event.startTime.toLocalDateTime(testTimeZone).date
                    }.mapValues { (_, eventList) ->
                        eventList.toImmutableList()
                    }.toImmutableMap(),
            )

        val date = LocalDate(2022, 1, 1)
        val eventsOnDate = eventsByDate[date]

        assertEquals(2, eventsOnDate.size, "Should preserve both events on the same date")
        assertEquals("event1", eventsOnDate[0].id)
        assertEquals("event2", eventsOnDate[1].id)
    }
}
