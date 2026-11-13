package com.debanshu.xcalendar.common

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Date ranges used by calendar queries and holiday loading. */
object DateRangeHelper {
    private const val DEFAULT_MONTH_RANGE = 10

    private fun getCurrentDate(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private fun getCurrentTimeZone(): TimeZone = TimeZone.currentSystemDefault()

    /** Inclusive epoch-millisecond bounds for the requested year. */
    fun getYearRange(year: Int): Pair<Long, Long> {
        val timeZone = getCurrentTimeZone()

        val startDateTime = LocalDateTime(
            year = year,
            month = Month.JANUARY,
            dayOfMonth = 1,
            hour = 0,
            minute = 0,
            second = 0,
            nanosecond = 0
        )

        val endDateTime = LocalDateTime(
            year = year,
            month = Month.DECEMBER,
            dayOfMonth = 31,
            hour = 23,
            minute = 59,
            second = 59,
            nanosecond = 999_999_999
        )

        val startMillis = startDateTime.toInstant(timeZone).toEpochMilliseconds()
        val endMillis = endDateTime.toInstant(timeZone).toEpochMilliseconds()

        return Pair(startMillis, endMillis)
    }

    data class DateRange(
        val currentDate: LocalDate,
        val startTime: Long,
        val endTime: Long
    )

    /**
     * Get the full date range for queries centered on current date.
     *
     * @param monthsRange Number of months before and after current date
     */
    fun getDateRange(monthsRange: Int = DEFAULT_MONTH_RANGE): DateRange {
        val currentDate = getCurrentDate()
        val timeZone = getCurrentTimeZone()

        val startTime = currentDate
            .minus(DatePeriod(months = monthsRange))
            .atStartOfDayIn(timeZone)
            .toEpochMilliseconds()

        val endTime = currentDate
            .plus(DatePeriod(months = monthsRange))
            .atStartOfDayIn(timeZone)
            .toEpochMilliseconds()

        return DateRange(currentDate, startTime, endTime)
    }

}
