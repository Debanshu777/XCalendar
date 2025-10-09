package com.debanshu.xcalendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.repository.CalendarRepository
import com.debanshu.xcalendar.domain.repository.EventRepository
import com.debanshu.xcalendar.domain.repository.HolidayRepository
import com.debanshu.xcalendar.domain.repository.UserRepository
import com.debanshu.xcalendar.domain.states.CalendarUiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.android.annotation.KoinViewModel
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@KoinViewModel
class CalendarViewModel(
    private val userRepository: UserRepository,
    private val calendarRepository: CalendarRepository,
    private val eventRepository: EventRepository,
    private val holidayRepository: HolidayRepository,
) : ViewModel() {
    @OptIn(ExperimentalTime::class)
    private val currentDate =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

    @OptIn(ExperimentalTime::class)
    private val startTime =
        currentDate
            .minus(DatePeriod(months = 10))
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

    @OptIn(ExperimentalTime::class)
    private val endTime =
        currentDate
            .plus(DatePeriod(months = 10))
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

    // Internal state management
    private val _uiState = MutableStateFlow(CalendarUiState(isLoading = true))

    @OptIn(ExperimentalAtomicApi::class)
    private val _isInitialized = AtomicBoolean(false)

    // Cached data flows with proper error handling
    private val users =
        userRepository
            .getAllUsers()
            .catch { exception ->
                handleError("Failed to load users", exception)
                emit(emptyList())
            }.shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )

    private val holidays =
        holidayRepository
            .getHolidaysForYear("IN", currentDate.year)
            .catch { exception ->
                handleError("Failed to load holidays", exception)
                emit(emptyList())
            }.shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )

    private val calendars =
        calendarRepository
            .getCalendarsForUser("user_id")
            .catch { exception ->
                handleError("Failed to load calendars", exception)
                emit(emptyList())
            }.shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )

    private val events =
        eventRepository
            .getEventsForCalendarsInRange("user_id", startTime, endTime)
            .catch { exception ->
                handleError("Failed to load events", exception)
                emit(emptyList())
            }.shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )

    // Optimized UI state with proper distinctUntilChanged and debouncing
    @OptIn(FlowPreview::class)
    val uiState =
        combine(
            _uiState,
            users.distinctUntilChanged(),
            holidays.distinctUntilChanged(),
            calendars.distinctUntilChanged(),
            events.distinctUntilChanged(),
        ) { currentState, usersList, holidaysList, calendarsList, eventsList ->
            currentState.copy(
                accounts = usersList,
                holidays = holidaysList,
                calendars = calendarsList,
                events = eventsList,
                isLoading = false,
            )
        }.distinctUntilChanged()
            .debounce(30) // Reduced debounce for better responsiveness
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = CalendarUiState(isLoading = true),
            )

    init {
        initializeData()
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun initializeData() {
        if (_isInitialized.compareAndSet(false, true)) {
            viewModelScope.launch {
                try {
                    val initJobs =
                        listOf(
                            async {
                                initializeUsers()
                                initializeCalendars()
                                initializeEvents()
                            },
                            async { initializeHolidays() },
                        )
                    initJobs.awaitAll()
                } catch (exception: Exception) {
                    handleError("Initialization failed", exception)
                } finally {
                    updateLoadingState(false)
                }
            }
        }
    }

    private suspend fun initializeUsers() {
        runCatching {
            userRepository.getUserFromApi()
        }.onFailure { exception ->
            handleError("Failed to initialize users", exception)
        }
    }

    private suspend fun initializeHolidays() {
        runCatching {
            holidayRepository.getHolidaysForYear("IN", currentDate.year).collectLatest {
                if (it.isEmpty()) {
                    holidayRepository.updateHolidays("IN", currentDate.year)
                }
            }
        }.onFailure { exception ->
            handleError("Failed to initialize holidays", exception)
        }
    }

    private suspend fun initializeCalendars() {
        runCatching {
            calendarRepository.getCalendersForUser("user_id")
        }.onFailure { exception ->
            handleError("Failed to initialize calendars", exception)
        }
    }

    private suspend fun initializeEvents() {
        runCatching {
            eventRepository.getEventsForCalendar(emptyList(), startTime, endTime)
        }.onFailure { exception ->
            handleError("Failed to initialize events", exception)
        }
    }

    fun toggleCalendarVisibility(calendar: Calendar) {
        val updatedCalendar = calendar.copy(isVisible = !calendar.isVisible)

        viewModelScope.launch {
            runCatching {
                // Uncomment when repository method is ready
                // calendarRepository.upsertCalendar(listOf(updatedCalendar))

                updateState { currentState ->
                    val updatedCalendars =
                        currentState.calendars.map { cal ->
                            if (cal.id == calendar.id) updatedCalendar else cal
                        }
                    currentState.copy(calendars = updatedCalendars)
                }
            }.onFailure { exception ->
                handleError("Failed to toggle calendar visibility", exception)
            }
        }
    }

    fun selectEvent(event: Event) {
        updateState { it.copy(selectedEvent = event) }
    }

    fun clearSelectedEvent() {
        updateState { it.copy(selectedEvent = null) }
    }

    fun addEvent(event: Event) {
        performEventOperation(
            operation = { eventRepository.addEvent(event) },
            onSuccess = { currentState ->
                currentState.copy(events = currentState.events + event)
            },
            errorMessage = "Failed to add event",
        )
    }

    fun editEvent(event: Event) {
        performEventOperation(
            operation = { eventRepository.updateEvent(event) },
            onSuccess = { currentState ->
                val updatedEvents =
                    currentState.events.map { e ->
                        if (e.id == event.id) event else e
                    }
                currentState.copy(
                    events = updatedEvents,
                    selectedEvent = null,
                )
            },
            errorMessage = "Failed to edit event",
        )
    }

    fun deleteEvent(event: Event) {
        performEventOperation(
            operation = { eventRepository.deleteEvent(event) },
            onSuccess = { currentState ->
                val updatedEvents = currentState.events.filter { e -> e.id != event.id }
                currentState.copy(
                    events = updatedEvents,
                    selectedEvent = null,
                )
            },
            errorMessage = "Failed to delete event",
        )
    }

    private fun performEventOperation(
        operation: suspend () -> Unit,
        onSuccess: (CalendarUiState) -> CalendarUiState,
        errorMessage: String,
    ) {
        viewModelScope.launch {
            runCatching {
                operation()
                updateState(onSuccess)
            }.onFailure { exception ->
                handleError(errorMessage, exception)
            }
        }
    }

    private fun updateState(update: (CalendarUiState) -> CalendarUiState) {
        _uiState.update(update)
    }

    private fun updateLoadingState(isLoading: Boolean) {
        updateState { it.copy(isLoading = isLoading) }
    }

    private fun handleError(
        message: String,
        exception: Throwable,
    ) {
        println("CalendarViewModel Error: $message - ${exception.message}")
        updateState { currentState ->
            currentState.copy(
                isLoading = false,
                errorMessage = message,
            )
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun onCleared() {
        super.onCleared()
        _isInitialized.store(false)
    }
}
