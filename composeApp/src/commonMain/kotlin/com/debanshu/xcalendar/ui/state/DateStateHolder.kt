package com.debanshu.xcalendar.ui.state

import com.debanshu.xcalendar.common.model.YearMonth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Holds and manages the current date state for the calendar UI.
 *
 * This is a UI-layer state holder that manages:
 * - Current date (today) — refreshed on midnight crossover
 * - Selected date
 * - Currently viewed month
 *
 * Uses StateFlow.update() instead of tryEmit() to ensure atomic updates
 * and prevent silent failures when the buffer is full.
 *
 * [Clock] is exposed as a constructor parameter so tests can inject a
 * controllable clock to simulate midnight crossover (audit F2).
 */
@OptIn(ExperimentalTime::class)
@Single
class DateStateHolder(
    private val clock: Clock = Clock.System,
    private val timeZoneProvider: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val initialDate: LocalDate = clock.now()
        .toLocalDateTime(timeZoneProvider())
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
     * Resets to today's date. Called by the midnight tick driver and any
     * caller that wants to snap back to "now" (e.g. a "Today" button).
     * Selected date is preserved unless it was the old today.
     */
    fun resetToToday() {
        val today = clock.now().toLocalDateTime(timeZoneProvider()).date
        _currentDateState.update { current ->
            if (current.currentDate == today) {
                current
            } else {
                // If the user hadn't moved off today, follow today forward.
                val newSelected = if (current.selectedDate == current.currentDate) today else current.selectedDate
                current.copy(
                    currentDate = today,
                    selectedDate = newSelected,
                    selectedInViewMonth = YearMonth(today.year, today.month.number),
                )
            }
        }
    }

    /**
     * Emits the new "today" each time the wall clock crosses midnight.
     *
     * Collect this from a long-lived scope (e.g. `viewModelScope` of the
     * root ViewModel) and call [resetToToday] on each emission.
     *
     * Behaviour:
     * - First emits when local midnight is reached.
     * - Then emits every 24h thereafter (with a small re-sync each tick
     *   so DST changes don't drift the schedule).
     */
    fun midnightTicker(): Flow<LocalDate> = flow {
        while (true) {
            val tz = timeZoneProvider()
            val now = clock.now()
            val today = now.toLocalDateTime(tz).date
            val nextMidnightInstant = today.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
                .atStartOfDayIn(tz)
            val delayMillis = (nextMidnightInstant - now).inWholeMilliseconds
                .coerceAtLeast(0L)
            delay(delayMillis)
            emit(clock.now().toLocalDateTime(tz).date)
        }
    }
}
