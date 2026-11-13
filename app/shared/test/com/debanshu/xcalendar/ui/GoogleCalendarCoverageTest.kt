package com.debanshu.xcalendar.ui

import com.debanshu.xcalendar.data.google.GoogleProfile
import com.debanshu.xcalendar.data.google.GoogleSnapshot
import com.debanshu.xcalendar.ui.google.coversMonthGrid
import com.debanshu.xcalendar.ui.google.monthSyncWindow
import com.debanshu.xcalendar.ui.google.isFresh
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.*

class GoogleCalendarCoverageTest {
    private val zone = TimeZone.of("America/New_York")
    private val month = LocalDate(2026, 11, 1)
    private fun snapshot(start: String, end: String) = GoogleSnapshot(
        GoogleProfile("account"),
        rangeStart = LocalDate.parse(start).atStartOfDayIn(zone).toEpochMilliseconds(),
        rangeEnd = LocalDate.parse(end).atStartOfDayIn(zone).toEpochMilliseconds(),
    )

    @Test fun cachedYearOnEitherSideNeedsNoFetch() {
        val (start, end) = monthSyncWindow(month, zone)
        val cache = listOf(GoogleSnapshot(GoogleProfile("account"), rangeStart = start, rangeEnd = end))
        assertTrue(cache.coversMonthGrid(month, zone))
        assertTrue(cache.coversMonthGrid(LocalDate(2026, 10, 1), zone))
        assertTrue(cache.coversMonthGrid(LocalDate(2026, 12, 1), zone))
        assertTrue(cache.coversMonthGrid(LocalDate(2026, 8, 1), zone))
        assertTrue(cache.coversMonthGrid(LocalDate(2025, 11, 1), zone))
        assertTrue(cache.coversMonthGrid(LocalDate(2027, 11, 1), zone))
        assertFalse(cache.coversMonthGrid(LocalDate(2025, 10, 1), zone))
        assertFalse(cache.coversMonthGrid(LocalDate(2027, 12, 1), zone))
    }

    @Test fun freshnessExpiresAndRejectsMissingOrFutureTimestamps() {
        val now = 2_000_000L
        val cached = snapshot("2026-10-01", "2027-01-01")
        assertTrue(listOf(cached.copy(syncedAt = now - 899_999)).isFresh(now))
        assertFalse(listOf(cached.copy(syncedAt = now - 900_000)).isFresh(now))
        assertFalse(listOf(cached.copy(syncedAt = now + 1)).isFresh(now))
        assertFalse(listOf(cached).isFresh(now))
        assertFalse(emptyList<GoogleSnapshot>().isFresh(now))
        assertFalse(listOf(cached.copy(syncedAt = now), cached).isFresh(now))
    }

    @Test fun coverageIncludesAllSixWeeksAndExclusiveEnd() {
        assertFalse(listOf(snapshot("2026-11-01", "2026-12-01")).coversMonthGrid(month, zone))
        assertTrue(listOf(snapshot("2026-11-01", "2026-12-13")).coversMonthGrid(month, zone))
        assertFalse(listOf(snapshot("2026-11-01", "2026-12-12")).coversMonthGrid(month, zone))
    }

    @Test fun everyAccountMustCoverTheWindowIncludingEmptyCalendars() {
        val complete = snapshot("2026-10-01", "2027-01-01")
        val missing = GoogleSnapshot(GoogleProfile("other"))
        assertTrue(listOf(complete).coversMonthGrid(month, zone))
        assertFalse(listOf(complete, missing).coversMonthGrid(month, zone))
        assertFalse(emptyList<GoogleSnapshot>().coversMonthGrid(month, zone))
    }
}
