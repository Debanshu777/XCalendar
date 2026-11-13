package com.debanshu.xcalendar.ui.google

import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.data.google.GoogleSnapshot
import com.debanshu.xcalendar.ui.utils.monthGridDates
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.minus

// Retain a year on either side, including each boundary month's overflow days.
internal fun monthSyncWindow(month: LocalDate, zone: TimeZone): Pair<Long, Long> {
    val previous = month.minus(DatePeriod(years = 1))
    val next = month.plus(DatePeriod(years = 1))
    val start = monthGridDates(YearMonth(previous.year, previous.month)).first()
    val end = monthGridDates(YearMonth(next.year, next.month)).last().plus(DatePeriod(days = 1))
    return start.atStartOfDayIn(zone).toEpochMilliseconds() to end.atStartOfDayIn(zone).toEpochMilliseconds()
}

/** Coverage includes overflow days in the six-week grid, for every account.
 * This controls navigation fetches only; explicit/periodic refresh still runs.
 */
internal fun List<GoogleSnapshot>.coversMonthGrid(month: LocalDate, zone: TimeZone): Boolean {
    if (isEmpty()) return false
    val (start, end) = monthGridBounds(month, zone)
    return all { it.rangeStart <= start && it.rangeEnd >= end }
}

internal fun monthGridBounds(month: LocalDate, zone: TimeZone): Pair<Long, Long> {
    val dates = monthGridDates(YearMonth(month.year, month.month))
    val start = dates.first().atStartOfDayIn(zone).toEpochMilliseconds()
    val end = dates.last().plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
    return start to end
}

// Automatic refreshes reuse recent persisted data; manual refresh bypasses this.
internal fun List<GoogleSnapshot>.isFresh(now: Long): Boolean =
    isNotEmpty() && all { it.syncedAt > 0 && now - it.syncedAt in 0 until 15 * 60 * 1000L }
