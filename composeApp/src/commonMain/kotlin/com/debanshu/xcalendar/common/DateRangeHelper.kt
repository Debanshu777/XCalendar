@file:OptIn(kotlin.time.ExperimentalTime::class)

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

/**
 * Centralized utility for date range calculations used across the app.
 * 
 * This consolidates all date range logic to:
 * - Avoid code duplication
 * - Ensure consistent time zone handling
 * - Provide a single source of truth for date calculations
 * 
 * Used by:
 * - ViewModels for event/holiday queries
 * - Store factories for caching keys
 * - Repositories for date-based filtering
 */
object DateRangeHelper {
    
    /**
     * Default number of months to load in each direction (past and future).
     */
    const val DEFAULT_MONTH_RANGE = 10
    
    /**
     * Get the current date in the system's default timezone.
     */
    fun getCurrentDate(): LocalDate =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    
    /**
     * Get the current year.
     */
    fun getCurrentYear(): Int = getCurrentDate().year
    
    /**
     * Get the current timezone.
     */
    fun getCurrentTimeZone(): TimeZone = TimeZone.currentSystemDefault()
    
    /**
     * Get current time in epoch milliseconds.
     */
    fun getCurrentTimeMillis(): Long = 
        Clock.System.now().toEpochMilliseconds()
    
    /**
     * Calculate the start time for queries.
     * Returns epoch milliseconds for [monthsBack] months before the current date.
     */
    fun getStartTime(monthsBack: Int = DEFAULT_MONTH_RANGE): Long =
        getCurrentDate()
            .minus(DatePeriod(months = monthsBack))
            .atStartOfDayIn(getCurrentTimeZone())
            .toEpochMilliseconds()
    
    /**
     * Calculate the end time for queries.
     * Returns epoch milliseconds for [monthsForward] months after the current date.
     */
    fun getEndTime(monthsForward: Int = DEFAULT_MONTH_RANGE): Long =
        getCurrentDate()
            .plus(DatePeriod(months = monthsForward))
            .atStartOfDayIn(getCurrentTimeZone())
            .toEpochMilliseconds()
    
    /**
     * Get the start and end epoch milliseconds for a specific year.
     * 
     * @param year The year to get the range for
     * @return Pair of (startMillis, endMillis) representing Jan 1 00:00:00 to Dec 31 23:59:59
     */
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
    
    /**
     * Get the start and end epoch milliseconds for a specific month.
     * 
     * @param year The year
     * @param month The month (1-12)
     * @return Pair of (startMillis, endMillis) for the month
     */
    fun getMonthRange(year: Int, month: Int): Pair<Long, Long> {
        val timeZone = getCurrentTimeZone()
        val kotlinMonth = Month(month)
        
        val startDateTime = LocalDateTime(
            year = year,
            month = kotlinMonth,
            dayOfMonth = 1,
            hour = 0,
            minute = 0,
            second = 0,
            nanosecond = 0
        )
        
        // Calculate last day of month
        val isLeapYear = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
        val lastDay = kotlinMonth.lengthOfMonth(isLeapYear)
        
        val endDateTime = LocalDateTime(
            year = year,
            month = kotlinMonth,
            dayOfMonth = lastDay,
            hour = 23,
            minute = 59,
            second = 59,
            nanosecond = 999_999_999
        )
        
        val startMillis = startDateTime.toInstant(timeZone).toEpochMilliseconds()
        val endMillis = endDateTime.toInstant(timeZone).toEpochMilliseconds()
        
        return Pair(startMillis, endMillis)
    }
    
    /**
     * Get the start and end epoch milliseconds for a specific day.
     * 
     * @param date The date
     * @return Pair of (startMillis, endMillis) for the day
     */
    fun getDayRange(date: LocalDate): Pair<Long, Long> {
        val timeZone = getCurrentTimeZone()
        
        val startMillis = date.atStartOfDayIn(timeZone).toEpochMilliseconds()
        
        val endDateTime = LocalDateTime(
            year = date.year,
            month = date.month,
            dayOfMonth = date.dayOfMonth,
            hour = 23,
            minute = 59,
            second = 59,
            nanosecond = 999_999_999
        )
        val endMillis = endDateTime.toInstant(timeZone).toEpochMilliseconds()
        
        return Pair(startMillis, endMillis)
    }
    
    /**
     * Data class containing the date range for queries.
     */
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
    
    /**
     * Convert epoch milliseconds to LocalDateTime in the current timezone.
     */
    fun epochToLocalDateTime(epochMillis: Long): LocalDateTime =
        kotlinx.datetime.Instant
            .fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(getCurrentTimeZone())
    
    /**
     * Convert LocalDateTime to epoch milliseconds.
     */
    fun localDateTimeToEpoch(dateTime: LocalDateTime): Long =
        dateTime.toInstant(getCurrentTimeZone()).toEpochMilliseconds()
    
    /**
     * Convert LocalDate to epoch milliseconds (start of day).
     */
    fun localDateToEpoch(date: LocalDate): Long =
        date.atStartOfDayIn(getCurrentTimeZone()).toEpochMilliseconds()
}

