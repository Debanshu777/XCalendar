package com.debanshu.xcalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.usecase.event.CreateEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.DeleteEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.UpdateEventUseCase
import com.debanshu.xcalendar.domain.util.DomainError
import com.debanshu.xcalendar.domain.util.DomainResult
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

    fun addEvent(event: Event, onSuccess: () -> Unit = {}) {
        saveEvent(onSuccess = onSuccess) { createEventUseCase(event) }
    }

    fun editEvent(event: Event, onSuccess: () -> Unit = {}) {
        saveEvent(clearSelectionFor = event.id, onSuccess = onSuccess) { updateEventUseCase(event) }
    }

    fun deleteEvent(event: Event) {
        saveEvent(
            clearSelectionFor = event.id,
            failureMessage = "Couldn't delete this event. Please try again.",
        ) { deleteEventUseCase(event) }
    }

    private fun saveEvent(
        clearSelectionFor: String? = null,
        onSuccess: () -> Unit = {},
        failureMessage: String = "Couldn't save your changes. Please try again.",
        operation: suspend () -> DomainResult<Unit>,
    ) {
        // Claim the operation before launching: a second tap can arrive before
        // the coroutine runs, even when the button observes isLoading.
        while (true) {
            val state = _uiState.value
            if (state.isLoading) return
            if (_uiState.compareAndSet(state, state.copy(isLoading = true, errorMessage = null))) break
        }

        viewModelScope.launch {
            val result = try {
                operation()
            } finally {
                // Cancellation is propagated by the use cases and must also
                // release the submission guard without dismissing the editor.
                _uiState.update { it.copy(isLoading = false) }
            }
            when (result) {
                is DomainResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            selectedEvent = state.selectedEvent.takeUnless {
                                clearSelectionFor != null && it?.id == clearSelectionFor
                            },
                            errorMessage = null,
                        )
                    }
                    onSuccess()
                }
                is DomainResult.Error -> {
                    // Validation explains how to fix the form; persistence
                    // failures can contain implementation details and IDs.
                    val message = when (val error = result.error) {
                        is DomainError.ValidationError -> error.message
                        else -> failureMessage
                    }
                    _uiState.update { it.copy(errorMessage = message) }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
