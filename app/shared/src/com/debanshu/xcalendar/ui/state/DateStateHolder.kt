package com.debanshu.xcalendar.ui.state

import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.ui.utils.today
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Duration.Companion.minutes

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
    private val initialDate = today()
    
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
        val today = today()
        _currentDateState.update { current ->
            current.copy(
                currentDate = today,
                selectedDate = today,
                selectedInViewMonth = YearMonth(today.year, today.month.number),
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun trackToday() {
        while (true) {
            val zone = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val date = now.toLocalDateTime(zone).date
            updateToday(date)
            val nextMidnight = date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone)
            // Recheck the system clock and zone if either changes while the app stays open.
            delay(minOf(nextMidnight - now, 1.minutes))
        }
    }

    internal fun updateToday(date: LocalDate) {
        _currentDateState.update { it.copy(currentDate = date) }
    }
}

