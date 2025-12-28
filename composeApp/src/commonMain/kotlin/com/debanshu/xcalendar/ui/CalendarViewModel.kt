package com.debanshu.xcalendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.common.DateUtils
import com.debanshu.xcalendar.domain.repository.ICalendarRepository
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.repository.IUserRepository
import com.debanshu.xcalendar.domain.states.CalendarUiState
import com.debanshu.xcalendar.domain.usecase.calendar.GetUserCalendarsUseCase
import com.debanshu.xcalendar.domain.usecase.event.GetEventsForDateRangeUseCase
import com.debanshu.xcalendar.domain.usecase.holiday.GetHolidaysForYearUseCase
import com.debanshu.xcalendar.domain.usecase.holiday.RefreshHolidaysUseCase
import com.debanshu.xcalendar.domain.usecase.user.GetCurrentUserUseCase
import com.debanshu.xcalendar.domain.util.DomainError
import kotlinx.collections.immutable.toImmutableList
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
import org.koin.android.annotation.KoinViewModel
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@KoinViewModel
class CalendarViewModel(
    private val userRepository: IUserRepository,
    private val calendarRepository: ICalendarRepository,
    private val eventRepository: IEventRepository,
    getUserCalendarsUseCase: GetUserCalendarsUseCase,
    getEventsForDateRangeUseCase: GetEventsForDateRangeUseCase,
    private val getHolidaysForYearUseCase: GetHolidaysForYearUseCase,
    private val refreshHolidaysUseCase: RefreshHolidaysUseCase,
    getCurrentUserUseCase: GetCurrentUserUseCase,
) : ViewModel() {
    private val userId = getCurrentUserUseCase()
    private val dateRange = DateUtils.getDateRange()
    private val currentDate = dateRange.currentDate
    private val startTime = dateRange.startTime
    private val endTime = dateRange.endTime
    private val _uiState = MutableStateFlow(CalendarUiState(isLoading = true))

    @OptIn(ExperimentalAtomicApi::class)
    private val isInitialized = AtomicBoolean(false)
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
        getHolidaysForYearUseCase("IN", currentDate.year)
            .catch { exception ->
                handleError("Failed to load holidays", exception)
                emit(emptyList())
            }.shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )

    private val calendars =
        getUserCalendarsUseCase(userId)
            .catch { exception ->
                handleError("Failed to load calendars", exception)
                emit(emptyList())
            }.shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )

    private val events =
        getEventsForDateRangeUseCase(userId, startTime, endTime)
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
                accounts = usersList.toImmutableList(),
                holidays = holidaysList.toImmutableList(),
                calendars = calendarsList.toImmutableList(),
                events = eventsList.toImmutableList(),
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
        if (isInitialized.compareAndSet(expectedValue = false, newValue = true)) {
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
            getHolidaysForYearUseCase("IN", currentDate.year).collectLatest {
                if (it.isEmpty()) {
                    refreshHolidaysUseCase("IN", currentDate.year)
                }
            }
        }.onFailure { exception ->
            handleError("Failed to initialize holidays", exception)
        }
    }

    private suspend fun initializeCalendars() {
        runCatching {
            calendarRepository.refreshCalendarsForUser(userId)
        }.onFailure { exception ->
            handleError("Failed to initialize calendars", exception)
        }
    }

    private suspend fun initializeEvents() {
        runCatching {
            eventRepository.syncEventsForCalendar(emptyList(), startTime, endTime)
        }.onFailure { exception ->
            handleError("Failed to initialize events", exception)
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
        AppLogger.e(exception) { "CalendarViewModel: $message" }
        val errorMessage = "$message: ${exception.message ?: "Unknown error"}"
        updateState { currentState ->
            currentState.copy(
                isLoading = false,
                error = DomainError.Unknown(errorMessage),
            )
        }
    }

    fun clearError() {
        updateState { it.copy(error = null) }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun onCleared() {
        super.onCleared()
        isInitialized.store(false)
    }
}
