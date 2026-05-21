package com.debanshu.xcalendar.domain.states

import com.debanshu.xcalendar.domain.util.DomainError

/**
 * Loading and error slice for calendar shell UI — kept separate from list
 * [StateFlow]s so composables that only need accounts/events do not
 * recompose when [isLoading] or [error] flips (audit F27).
 */
data class CalendarSessionState(
    val isLoading: Boolean = true,
    val error: DomainError? = null,
) {
    val hasError: Boolean get() = error != null

    /** User-facing message for snackbars / dialogs. */
    val displayError: String? get() = error?.message
}
