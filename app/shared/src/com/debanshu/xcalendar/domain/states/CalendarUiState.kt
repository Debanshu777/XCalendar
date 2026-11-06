package com.debanshu.xcalendar.domain.states

import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.model.User
import com.debanshu.xcalendar.domain.util.DomainError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * UI state for the calendar screens.
 * 
 * Error handling is consolidated to use [DomainError] for structured error types.
 * Use [displayError] to get a user-friendly error message for display.
 */
data class CalendarUiState(
    val accounts: ImmutableList<User> = persistentListOf(),
    val calendars: ImmutableList<Calendar> = persistentListOf(),
    val events: ImmutableList<Event> = persistentListOf(),
    val holidays: ImmutableList<Holiday> = persistentListOf(),
    val selectedEvent: Event? = null,
    val isLoading: Boolean = false,
    val error: DomainError? = null,
) {
    /**
     * Whether there is an error to display.
     */
    val hasError: Boolean get() = error != null
    
    /**
     * User-friendly error message for display in UI (snackbar, dialog, etc.).
     */
    val displayError: String? get() = error?.message
    
    /**
     * Whether the state has no data (empty after loading).
     */
    val isEmpty: Boolean get() = !isLoading && accounts.isEmpty() && calendars.isEmpty() && events.isEmpty()
}
