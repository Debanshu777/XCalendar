package com.debanshu.xcalendar.ui

import com.debanshu.xcalendar.test.TestDataFactory
import com.debanshu.xcalendar.ui.utils.DateTimeFormatter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class DateTimeFormatterTest {
    private fun instant(value: String) = LocalDateTime.parse(value).toInstant(TimeZone.UTC).toEpochMilliseconds()

    @Test
    fun sameDayEventsKeepCompactTimesAndRecurrence() {
        val event = TestDataFactory.createEvent(
            startTime = instant("2026-11-08T12:00"),
            endTime = instant("2026-11-08T13:30"),
            isRecurring = true,
            recurringRule = "FREQ=WEEKLY;BYDAY=SU",
        )
        assertEquals(
            "Sunday, 8 Nov 12-1:30PM\nRepeat every week",
            DateTimeFormatter.formatEventSubheading(event, TimeZone.UTC),
        )
    }

    @Test
    fun overnightTimedEventsShowBothDatesIncludingAMidnightEnd() {
        val event = TestDataFactory.createEvent(
            startTime = instant("2026-11-08T23:00"),
            endTime = instant("2026-11-09T00:00"),
        )
        assertEquals(
            "Sunday, 8 Nov 11:00 PM – Monday, 9 Nov 12:00 AM",
            DateTimeFormatter.formatEventSubheading(event, TimeZone.UTC),
        )
    }

    @Test
    fun allDayRangesExcludeTheMidnightEndDate() {
        val event = TestDataFactory.createEvent(
            startTime = instant("2026-11-08T00:00"),
            endTime = instant("2026-11-11T00:00"),
            isAllDay = true,
        )
        assertEquals(
            "Sunday, 8 Nov – Tuesday, 10 Nov",
            DateTimeFormatter.formatEventSubheading(event, TimeZone.UTC),
        )
    }

    @Test
    fun singleAllDayDatesRemainOneDateAcrossDaylightSavingChanges() {
        val timeZone = TimeZone.of("America/New_York")
        for ((start, end, label) in listOf(
            Triple(LocalDate(2026, 3, 8), LocalDate(2026, 3, 9), "Sunday, 8 Mar"),
            Triple(LocalDate(2026, 11, 1), LocalDate(2026, 11, 2), "Sunday, 1 Nov"),
        )) {
            val event = TestDataFactory.createEvent(
                startTime = start.atStartOfDayIn(timeZone).toEpochMilliseconds(),
                endTime = end.atStartOfDayIn(timeZone).toEpochMilliseconds(),
                isAllDay = true,
            )
            assertEquals(label, DateTimeFormatter.formatEventSubheading(event, timeZone))
        }
    }

    @Test
    fun allDayRangeCrossingNewYearIncludesBothYears() {
        val event = TestDataFactory.createEvent(
            startTime = instant("2026-12-31T00:00"),
            endTime = instant("2027-01-02T00:00"),
            isAllDay = true,
        )
        assertEquals(
            "Thursday, 31 Dec 2026 – Friday, 1 Jan 2027",
            DateTimeFormatter.formatEventSubheading(event, TimeZone.UTC),
        )
    }
}
