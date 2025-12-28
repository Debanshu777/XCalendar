package com.debanshu.xcalendar.domain.states.dateState

import com.debanshu.xcalendar.common.model.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.ExperimentalTime

/**
 * Holds and manages the current date state for the calendar UI.
 * 
 * Uses StateFlow.update() instead of tryEmit() to ensure atomic updates
 * and prevent silent failures when the buffer is full.
 * 
 * For a high-scale app, reliable state updates are critical for:
 * - Consistent UI behavior
 * - Avoiding race conditions
 * - Debugging state-related issues
 */
@Single
class DateStateHolder {
    @OptIn(ExperimentalTime::class)
    val date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    
    private val _currentDateState = MutableStateFlow(
        DateState(
            currentDate = date,
            selectedDate = date,
            selectedInViewMonth = YearMonth(date.year, date.month.number),
        )
    )
    
    val currentDateState: StateFlow<DateState> = _currentDateState
    
    /**
     * Updates the currently visible month in the calendar view.
     * 
     * Uses [MutableStateFlow.update] for atomic, thread-safe updates
     * instead of tryEmit which can silently fail.
     */
    fun updateSelectedInViewMonthState(selectedInViewMonth: YearMonth) {
        _currentDateState.update { current ->
            current.copy(selectedInViewMonth = selectedInViewMonth)
        }
    }

    /**
     * Updates the selected date and synchronizes the view month.
     * 
     * Uses [MutableStateFlow.update] for atomic, thread-safe updates
     * instead of tryEmit which can silently fail.
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
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        _currentDateState.update { current ->
            current.copy(
                selectedDate = today,
                selectedInViewMonth = YearMonth(today.year, today.month.number),
            )
        }
    }
}