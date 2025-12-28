package com.debanshu.xcalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.common.DateUtils
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.usecase.event.CreateEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.DeleteEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.GetEventsForDateRangeUseCase
import com.debanshu.xcalendar.domain.usecase.event.UpdateEventUseCase
import com.debanshu.xcalendar.domain.util.DomainResult
import com.debanshu.xcalendar.domain.util.onError
import com.debanshu.xcalendar.domain.util.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class EventUiState(
    val events: ImmutableList<Event> = persistentListOf(),
    val selectedEvent: Event? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@KoinViewModel
class EventViewModel(
    private val getEventsForDateRangeUseCase: GetEventsForDateRangeUseCase,
    private val createEventUseCase: CreateEventUseCase,
    private val updateEventUseCase: UpdateEventUseCase,
    private val deleteEventUseCase: DeleteEventUseCase
) : ViewModel() {

    // TODO: Make userId configurable for future multi-user support
    private val userId = "user_id"

    // Use shared date utilities
    private val dateRange = DateUtils.getDateRange()

    private val _selectedEvent = MutableStateFlow<Event?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val eventsFlow = getEventsForDateRangeUseCase(userId, dateRange.startTime, dateRange.endTime)
        .catch { e ->
            _errorMessage.value = "Failed to load events: ${e.message}"
            emit(emptyList())
        }

    val uiState: StateFlow<EventUiState> = combine(
        eventsFlow,
        _selectedEvent,
        _isLoading,
        _errorMessage
    ) { events, selectedEvent, isLoading, errorMessage ->
        EventUiState(
            events = events.toImmutableList(),
            selectedEvent = selectedEvent,
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EventUiState(isLoading = true)
    )

    fun selectEvent(event: Event) {
        _selectedEvent.value = event
    }

    fun clearSelectedEvent() {
        _selectedEvent.value = null
    }

    fun addEvent(event: Event) {
        viewModelScope.launch {
            _isLoading.value = true
            createEventUseCase(event)
                .onSuccess { _errorMessage.value = null }
                .onError { error -> _errorMessage.value = error.message }
            _isLoading.value = false
        }
    }

    fun editEvent(event: Event) {
        viewModelScope.launch {
            _isLoading.value = true
            updateEventUseCase(event)
                .onSuccess { 
                    _selectedEvent.value = null
                    _errorMessage.value = null 
                }
                .onError { error -> _errorMessage.value = error.message }
            _isLoading.value = false
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            _isLoading.value = true
            deleteEventUseCase(event)
                .onSuccess {
                    _selectedEvent.value = null
                    _errorMessage.value = null
                }
                .onError { error -> _errorMessage.value = error.message }
            _isLoading.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

