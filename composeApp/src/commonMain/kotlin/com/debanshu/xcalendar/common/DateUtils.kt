package com.debanshu.xcalendar.common

import kotlinx.datetime.LocalDate

/**
 * Utility object for common date calculations used across ViewModels.
 * 
 * @deprecated Use [DateRangeHelper] instead for all date range calculations.
 * This object now delegates to DateRangeHelper for backward compatibility.
 */
object DateUtils {
    
    /**
     * Default number of months to load in each direction (past and future).
     */
    const val DEFAULT_MONTH_RANGE = DateRangeHelper.DEFAULT_MONTH_RANGE
    
    /**
     * Get the current date in the system's default timezone.
     * @deprecated Use [DateRangeHelper.getCurrentDate] instead.
     */
    fun getCurrentDate(): LocalDate = DateRangeHelper.getCurrentDate()
    
    /**
     * Get the current year.
     * @deprecated Use [DateRangeHelper.getCurrentYear] instead.
     */
    fun getCurrentYear(): Int = DateRangeHelper.getCurrentYear()
    
    /**
     * Calculate the start time for event queries.
     * Returns epoch milliseconds for [monthsBack] months before the current date.
     * @deprecated Use [DateRangeHelper.getStartTime] instead.
     */
    fun getStartTime(monthsBack: Int = DEFAULT_MONTH_RANGE): Long =
        DateRangeHelper.getStartTime(monthsBack)
    
    /**
     * Calculate the end time for event queries.
     * Returns epoch milliseconds for [monthsForward] months after the current date.
     * @deprecated Use [DateRangeHelper.getEndTime] instead.
     */
    fun getEndTime(monthsForward: Int = DEFAULT_MONTH_RANGE): Long =
        DateRangeHelper.getEndTime(monthsForward)
    
    /**
     * Data class containing the date range for queries.
     * @deprecated Use [DateRangeHelper.DateRange] instead.
     */
    data class DateRange(
        val currentDate: LocalDate,
        val startTime: Long,
        val endTime: Long
    )
    
    /**
     * Get the full date range for queries.
     * @deprecated Use [DateRangeHelper.getDateRange] instead.
     */
    fun getDateRange(monthsRange: Int = DEFAULT_MONTH_RANGE): DateRange {
        val helperRange = DateRangeHelper.getDateRange(monthsRange)
        return DateRange(
            currentDate = helperRange.currentDate,
            startTime = helperRange.startTime,
            endTime = helperRange.endTime
        )
    }
}

