package com.debanshu.xcalendar.ui.state

import com.debanshu.xcalendar.common.model.YearMonth

/**
 * Manages the range of months for the schedule view with pagination support.
 * 
 * @property startMonth The initial center month
 * @property initialRange How many months to load initially in each direction
 */
internal class ScheduleState(
    private val startMonth: YearMonth,
    private val initialRange: Int = 3 // Smaller default for faster initial load
) {
    private var minOffset = -initialRange
    private var maxOffset = initialRange

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
    fun expandBackward(amount: Int = 10): List<YearMonth> {
        val previous = minOffset
        minOffset -= amount
        return (minOffset until previous).map(startMonth::plusMonths)
    }

    /**
     * Expands the range forward by [amount] months.
     */
    fun expandForward(amount: Int = 10): List<YearMonth> {
        val previous = maxOffset
        maxOffset += amount
        return ((previous + 1)..maxOffset).map(startMonth::plusMonths)
    }
}

