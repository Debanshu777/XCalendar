package com.debanshu.xcalendar.common

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Utility object for common date calculations used across ViewModels.
 * Centralizes date range logic to avoid duplication.
 */
object DateUtils {
    
    /**
     * Default number of months to load in each direction (past and future).
     */
    const val DEFAULT_MONTH_RANGE = 10
    
    /**
     * Get the current date in the system's default timezone.
     */
    @OptIn(ExperimentalTime::class)
    fun getCurrentDate(): LocalDate =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    
    /**
     * Get the current year.
     */
    @OptIn(ExperimentalTime::class)
    fun getCurrentYear(): Int = getCurrentDate().year
    
    /**
     * Calculate the start time for event queries.
     * Returns epoch milliseconds for [monthsBack] months before the current date.
     */
    @OptIn(ExperimentalTime::class)
    fun getStartTime(monthsBack: Int = DEFAULT_MONTH_RANGE): Long =
        getCurrentDate()
            .minus(DatePeriod(months = monthsBack))
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    
    /**
     * Calculate the end time for event queries.
     * Returns epoch milliseconds for [monthsForward] months after the current date.
     */
    @OptIn(ExperimentalTime::class)
    fun getEndTime(monthsForward: Int = DEFAULT_MONTH_RANGE): Long =
        getCurrentDate()
            .plus(DatePeriod(months = monthsForward))
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    
    /**
     * Data class containing the date range for queries.
     */
    data class DateRange(
        val currentDate: LocalDate,
        val startTime: Long,
        val endTime: Long
    )
    
    /**
     * Get the full date range for queries.
     */
    @OptIn(ExperimentalTime::class)
    fun getDateRange(monthsRange: Int = DEFAULT_MONTH_RANGE): DateRange {
        val currentDate = getCurrentDate()
        val startTime = currentDate
            .minus(DatePeriod(months = monthsRange))
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        val endTime = currentDate
            .plus(DatePeriod(months = monthsRange))
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        
        return DateRange(currentDate, startTime, endTime)
    }
}

