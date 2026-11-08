package com.debanshu.xcalendar.ui

import com.debanshu.xcalendar.test.TestDataFactory
import com.debanshu.xcalendar.ui.components.dialog.formatReminderText
import com.debanshu.xcalendar.ui.components.dialog.parseEventInterval
import com.debanshu.xcalendar.ui.components.dialog.resolveEventInterval
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EventEditorTest {
    private fun utcInstant(value: String) = LocalDateTime.parse(value).toInstant(TimeZone.UTC).toEpochMilliseconds()

    @Test
    fun incompleteOrInvalidFieldsCannotBecomeEvents() {
        for ((date, time) in listOf(
            "" to "12:00",
            "2026-02-30" to "12:00",
            "2026-11-08" to "",
            "2026-11-08" to "24:00",
        )) {
            assertNull(parseEventInterval(date, "2026-11-09", time, "13:00", false, TimeZone.UTC))
        }
        assertNull(parseEventInterval("2026-11-08", "2026-11-08", "12:00", "12:00", false, TimeZone.UTC))
        assertNull(parseEventInterval("2026-11-08", "2026-11-08", "13:00", "12:00", false, TimeZone.UTC))
        assertNull(parseEventInterval("2026-11-08", "2026-11-07", "", "", true, TimeZone.UTC))
    }

    @Test
    fun overnightTimedEventsRetainTheSelectedEndDate() {
        val interval = assertNotNull(parseEventInterval(
            "2026-11-08", "2026-11-09", "23:30", "01:00", false, TimeZone.UTC,
        ))
        assertEquals(LocalDateTime(2026, 11, 8, 23, 30).toInstant(TimeZone.UTC).toEpochMilliseconds(), interval.first)
        assertEquals(LocalDateTime(2026, 11, 9, 1, 0).toInstant(TimeZone.UTC).toEpochMilliseconds(), interval.second)
        assertEquals(90 * 60_000L, interval.second - interval.first)
    }

    @Test
    fun allDayEndDatesAreInclusiveInputsAndExclusiveStoredInstants() {
        val interval = assertNotNull(parseEventInterval(
            "2026-12-31", "2027-01-01", "unused", "unused", true, TimeZone.UTC,
        ))
        assertEquals(LocalDate(2026, 12, 31).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), interval.first)
        assertEquals(LocalDate(2027, 1, 2).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), interval.second)
    }

    @Test
    fun allDayEventsFollowLocalMidnightsAcrossBothDaylightSavingTransitions() {
        val timeZone = TimeZone.of("America/New_York")
        for ((date, hours) in listOf("2026-03-08" to 23, "2026-11-01" to 25)) {
            val interval = assertNotNull(parseEventInterval(date, date, "", "", true, timeZone))
            assertEquals(hours * 3_600_000L, interval.second - interval.first)
        }
    }

    @Test
    fun nonexistentDaylightSavingTimesAreRejectedRatherThanSilentlyShifted() {
        val timeZone = TimeZone.of("America/New_York")
        assertNull(parseEventInterval("2026-03-08", "2026-03-08", "02:30", "04:00", false, timeZone))
        assertNull(parseEventInterval("2026-03-08", "2026-03-08", "01:30", "02:30", false, timeZone))
        val valid = assertNotNull(parseEventInterval("2026-03-08", "2026-03-08", "01:30", "03:30", false, timeZone))
        assertEquals(3_600_000L, valid.second - valid.first)
    }

    @Test
    fun unchangedSubMinuteEventCanSaveWithoutLosingItsOriginalInstants() {
        val original = TestDataFactory.createEvent(
            startTime = utcInstant("2026-11-08T12:00:10.125"),
            endTime = utcInstant("2026-11-08T12:00:40.250"),
        )
        assertEquals(
            original.startTime to original.endTime,
            resolveEventInterval("2026-11-08", "2026-11-08", "12:00", "12:00", false, TimeZone.UTC, original),
        )
    }

    @Test
    fun unchangedEventSpanningRepeatedClockTimePreservesBothOffsets() {
        val original = TestDataFactory.createEvent(
            startTime = utcInstant("2026-11-01T05:45"),
            endTime = utcInstant("2026-11-01T06:15"),
        )
        assertEquals(
            original.startTime to original.endTime,
            resolveEventInterval("2026-11-01", "2026-11-01", "01:45", "01:15", false,
                TimeZone.of("America/New_York"), original),
        )
    }

    @Test
    fun changingTimeFieldsUsesTheEditedValuesInsteadOfTheOriginalInstants() {
        val original = TestDataFactory.createEvent(
            startTime = utcInstant("2026-11-08T12:00:10.125"),
            endTime = utcInstant("2026-11-08T12:00:40.250"),
        )
        assertEquals(
            utcInstant("2026-11-08T12:00") to utcInstant("2026-11-08T12:05"),
            resolveEventInterval("2026-11-08", "2026-11-08", "12:00", "12:05", false, TimeZone.UTC, original),
        )
    }

    @Test
    fun zeroMinuteReminderMeansAtStartTime() {
        assertEquals("At start time", formatReminderText(0))
    }
}
