package com.debanshu.xcalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.usecase.event.CreateEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.DeleteEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.UpdateEventUseCase
import com.debanshu.xcalendar.domain.util.onError
import com.debanshu.xcalendar.domain.util.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class EventUiState(
    val selectedEvent: Event? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@KoinViewModel
class EventViewModel(
    private val createEventUseCase: CreateEventUseCase,
    private val updateEventUseCase: UpdateEventUseCase,
    private val deleteEventUseCase: DeleteEventUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EventUiState())
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    fun selectEvent(event: Event) {
        _uiState.update { it.copy(selectedEvent = event) }
    }

    fun clearSelectedEvent() {
        _uiState.update { it.copy(selectedEvent = null) }
    }

    fun addEvent(event: Event) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            createEventUseCase(event)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                }.onError { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun editEvent(event: Event) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            updateEventUseCase(event)
                .onSuccess {
                    _uiState.update {
                        it.copy(selectedEvent = null, isLoading = false, errorMessage = null)
                    }
                }.onError { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            deleteEventUseCase(event)
                .onSuccess {
                    _uiState.update {
                        it.copy(selectedEvent = null, isLoading = false, errorMessage = null)
                    }
                }.onError { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
