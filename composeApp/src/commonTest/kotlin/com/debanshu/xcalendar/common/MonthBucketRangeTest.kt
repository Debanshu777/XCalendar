package com.debanshu.xcalendar.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Phase 4 — F28 month-bucket quantization.
 *
 * The Store5 [com.debanshu.xcalendar.data.store.EventKey] is built from
 * `(startTime, endTime)`. Before this change those came from
 * `today ± N months`, so every day's reset of "today" produced a new key →
 * cache miss → refetch. After the change both ends snap to the first-of-month
 * of the bucket, so all days in a given month share one key.
 */
class MonthBucketRangeTest {

    @Test
    fun `two dates in the same month produce the same bucket`() {
        val d1 = LocalDate(2026, Month.MAY, 1)
        val d15 = LocalDate(2026, Month.MAY, 15)
        val d31 = LocalDate(2026, Month.MAY, 31)

        val r1 = DateRangeHelper.getMonthBucketRangeFor(d1)
        val r15 = DateRangeHelper.getMonthBucketRangeFor(d15)
        val r31 = DateRangeHelper.getMonthBucketRangeFor(d31)

        assertEquals(r1, r15, "Days in same month must share bucket")
        assertEquals(r1, r31, "Last day of month must share bucket with first day")
    }

    @Test
    fun `crossing into next month shifts the bucket exactly once`() {
        val lastMay = LocalDate(2026, Month.MAY, 31)
        val firstJune = LocalDate(2026, Month.JUNE, 1)

        val rMay = DateRangeHelper.getMonthBucketRangeFor(lastMay)
        val rJune = DateRangeHelper.getMonthBucketRangeFor(firstJune)

        assertNotEquals(rMay, rJune, "Month flip should produce a new bucket")
        assertTrue(rJune.first > rMay.first, "Start should move forward")
        assertTrue(rJune.second > rMay.second, "End should move forward")
    }

    @Test
    fun `bucket window honors requested months back and forward`() {
        // monthsBack=1, monthsForward=1 → window is [prev month, today month, next month]
        val today = LocalDate(2026, Month.MAY, 10)
        val (startMs, endMs) = DateRangeHelper.getMonthBucketRangeFor(
            today = today,
            monthsBack = 1,
            monthsForward = 1,
        )

        val start = DateRangeHelper.epochToLocalDateTime(startMs).date
        val end = DateRangeHelper.epochToLocalDateTime(endMs).date

        assertEquals(LocalDate(2026, Month.APRIL, 1), start, "Start should be Apr 1 (today_month - 1)")
        // monthsForward + 1 → first of (today_month + 2) = July 1
        assertEquals(LocalDate(2026, Month.JULY, 1), end, "End should be Jul 1 (today_month + monthsForward + 1)")
    }

    @Test
    fun `year boundary handled correctly`() {
        val lastDec = LocalDate(2026, Month.DECEMBER, 31)
        val firstJan = LocalDate(2027, Month.JANUARY, 1)

        val rDec = DateRangeHelper.getMonthBucketRangeFor(lastDec)
        val rJan = DateRangeHelper.getMonthBucketRangeFor(firstJan)

        assertNotEquals(rDec, rJan, "Year flip should yield new bucket")
    }
}
