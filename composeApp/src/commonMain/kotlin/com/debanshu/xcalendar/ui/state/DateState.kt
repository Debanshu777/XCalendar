package com.debanshu.xcalendar.ui.state

import com.debanshu.xcalendar.common.model.YearMonth
import kotlinx.datetime.LocalDate

/**
 * Represents the current date state for the calendar UI.
 *
 * @property currentDate The actual current date (today)
 * @property selectedDate The currently selected date by the user
 * @property selectedInViewMonth The month currently being viewed
 */
data class DateState(
    val currentDate: LocalDate,
    val selectedDate: LocalDate,
    val selectedInViewMonth: YearMonth,
)
