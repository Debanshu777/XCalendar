package com.debanshu.xcalendar.ui.state

import com.debanshu.xcalendar.common.model.YearMonth

/**
 * Manages the range of months for the schedule view with pagination support.
 * 
 * @property startMonth The initial center month
 * @property initialRange How many months to load initially in each direction
 */
data class ScheduleState(
    private val startMonth: YearMonth,
    private val initialRange: Int = 3 // Smaller default for faster initial load
) {
    private var minOffset = -initialRange
    private var maxOffset = initialRange
    private var lastMinOffset = minOffset
    private var lastMaxOffset = maxOffset

    /**
     * Returns all months in the current range.
     */
    fun getMonths(): List<YearMonth> {
        return (minOffset..maxOffset).map { offset ->
            startMonth.plusMonths(offset)
        }
    }

    /**
     * Expands the range backward by [amount] months.
     */
    fun expandBackward(amount: Int = 10) {
        lastMinOffset = minOffset
        minOffset -= amount
    }

    /**
     * Expands the range forward by [amount] months.
     */
    fun expandForward(amount: Int = 10) {
        lastMaxOffset = maxOffset
        maxOffset += amount
    }

    /**
     * Returns the months that were added in the last backward expansion.
     */
    fun getLastAddedMonthsBackward(): List<YearMonth> {
        return (minOffset until lastMinOffset).map { offset ->
            startMonth.plusMonths(offset)
        }
    }

    /**
     * Returns the months that were added in the last forward expansion.
     */
    fun getLastAddedMonthsForward(): List<YearMonth> {
        return ((lastMaxOffset + 1)..maxOffset).map { offset ->
            startMonth.plusMonths(offset)
        }
    }
}

