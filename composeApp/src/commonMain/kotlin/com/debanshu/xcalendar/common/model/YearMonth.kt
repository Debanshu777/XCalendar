package com.debanshu.xcalendar.common.model

import androidx.compose.runtime.Immutable
import com.debanshu.xcalendar.common.isLeap
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.number

/**
 * Represents a year-month combination without a day component
 */
@Immutable
data class YearMonth(
    val year: Int,
    val month: Month,
) {
    constructor(year: Int, monthNumber: Int) : this(
        year,
        Month(monthNumber),
    )

    fun plusMonths(months: Int): YearMonth {
        var newYear = year
        var newMonthNum = month.number + months

        while (newMonthNum > 12) {
            newMonthNum -= 12
            newYear++
        }

        while (newMonthNum < 1) {
            newMonthNum += 12
            newYear--
        }

        return YearMonth(newYear, Month(newMonthNum))
    }

    fun getLastDateOrdinal(): Int =
        when (month) {
            Month.JANUARY -> 31
            Month.FEBRUARY -> if (year.isLeap()) 29 else 28
            Month.MARCH -> 31
            Month.APRIL -> 30
            Month.MAY -> 31
            Month.JUNE -> 30
            Month.JULY -> 31
            Month.AUGUST -> 31
            Month.SEPTEMBER -> 30
            Month.OCTOBER -> 31
            Month.NOVEMBER -> 30
            Month.DECEMBER -> 31
        }

    override fun toString(): String = "$year-${month.number.toString().padStart(2, '0')}"

    companion object {
        fun from(date: LocalDate): YearMonth = YearMonth(date.year, date.month)
    }
}
