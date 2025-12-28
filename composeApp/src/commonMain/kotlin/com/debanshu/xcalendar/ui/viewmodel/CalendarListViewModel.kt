package com.debanshu.xcalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.usecase.calendar.GetUserCalendarsUseCase
import com.debanshu.xcalendar.domain.usecase.calendar.ToggleCalendarVisibilityUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class CalendarListUiState(
    val calendars: ImmutableList<Calendar> = persistentListOf(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@KoinViewModel
class CalendarListViewModel(
    private val getUserCalendarsUseCase: GetUserCalendarsUseCase,
    private val toggleCalendarVisibilityUseCase: ToggleCalendarVisibilityUseCase
) : ViewModel() {

    // TODO: Make userId configurable for future multi-user support
    private val userId = "user_id"

    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val calendarsFlow = getUserCalendarsUseCase(userId)
        .catch { e ->
            _errorMessage.value = "Failed to load calendars: ${e.message}"
            emit(emptyList())
        }

    val uiState: StateFlow<CalendarListUiState> = combine(
        calendarsFlow,
        _isLoading,
        _errorMessage
    ) { calendars, isLoading, errorMessage ->
        CalendarListUiState(
            calendars = calendars.toImmutableList(),
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarListUiState(isLoading = true)
    )

    val visibleCalendars: StateFlow<ImmutableList<Calendar>> = calendarsFlow
        .map { calendars -> calendars.filter { it.isVisible }.toImmutableList() }
        .catch { emit(persistentListOf()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    fun toggleCalendarVisibility(calendar: Calendar) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                toggleCalendarVisibilityUseCase(calendar)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to toggle visibility: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

