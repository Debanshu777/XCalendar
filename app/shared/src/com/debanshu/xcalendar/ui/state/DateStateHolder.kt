package com.debanshu.xcalendar.ui.state

import com.debanshu.xcalendar.common.model.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Holds and manages the current date state for the calendar UI.
 * 
 * This is a UI-layer state holder that manages:
 * - Current date (today)
 * - Selected date
 * - Currently viewed month
 * 
 * Uses StateFlow.update() instead of tryEmit() to ensure atomic updates
 * and prevent silent failures when the buffer is full.
 */
@Single
class DateStateHolder {
    @OptIn(ExperimentalTime::class)
    private val initialDate: LocalDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    
    private val _currentDateState = MutableStateFlow(
        DateState(
            currentDate = initialDate,
            selectedDate = initialDate,
            selectedInViewMonth = YearMonth(initialDate.year, initialDate.month.number),
        )
    )
    
    val currentDateState: StateFlow<DateState> = _currentDateState
    
    /**
     * The current date (today) for convenience access.
     */
    val today: LocalDate get() = _currentDateState.value.currentDate
    
    /**
     * Updates the currently visible month in the calendar view.
     */
    fun updateSelectedInViewMonthState(selectedInViewMonth: YearMonth) {
        _currentDateState.update { current ->
            current.copy(selectedInViewMonth = selectedInViewMonth)
        }
    }

    /**
     * Updates the selected date and synchronizes the view month.
     */
    fun updateSelectedDateState(selectedDate: LocalDate) {
        _currentDateState.update { current ->
            current.copy(
                selectedDate = selectedDate,
                selectedInViewMonth = YearMonth(selectedDate.year, selectedDate.month),
            )
        }
    }
    
    /**
     * Resets to today's date.
     */
    @OptIn(ExperimentalTime::class)
    fun resetToToday() {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        _currentDateState.update { current ->
            current.copy(
                currentDate = today,
                selectedDate = today,
                selectedInViewMonth = YearMonth(today.year, today.month.number),
            )
        }
    }
}

