package com.debanshu.xcalendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.common.DateRangeHelper
import com.debanshu.xcalendar.domain.repository.ICalendarRepository
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.repository.IUserRepository
import com.debanshu.xcalendar.domain.states.CalendarUiState
import com.debanshu.xcalendar.domain.usecase.calendar.GetUserCalendarsUseCase
import com.debanshu.xcalendar.domain.usecase.event.GetEventsForDateRangeUseCase
import com.debanshu.xcalendar.domain.usecase.holiday.GetHolidaysForYearUseCase
import com.debanshu.xcalendar.domain.usecase.user.GetCurrentUserUseCase
import com.debanshu.xcalendar.domain.util.DomainError
import com.debanshu.xcalendar.ui.state.DateStateHolder
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class CalendarViewModel(
    private val userRepository: IUserRepository,
    private val calendarRepository: ICalendarRepository,
    private val eventRepository: IEventRepository,
    private val dateStateHolder: DateStateHolder,
    getUserCalendarsUseCase: GetUserCalendarsUseCase,
    getEventsForDateRangeUseCase: GetEventsForDateRangeUseCase,
    private val getHolidaysForYearUseCase: GetHolidaysForYearUseCase,
    getCurrentUserUseCase: GetCurrentUserUseCase,
) : ViewModel() {
    private val userId = getCurrentUserUseCase()
    private val dateRange = DateRangeHelper.getDateRange()
    private val startTime = dateRange.startTime
    private val endTime = dateRange.endTime
    private val error = MutableStateFlow<DomainError?>(null)

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

    // Combine holidays from current year, previous year, and next year to avoid blinking on year transitions
    @OptIn(ExperimentalCoroutinesApi::class)
    private val holidays =
        dateStateHolder.currentDateState
            .map { it.selectedInViewMonth.year }
            .distinctUntilChanged()
            .flatMapLatest { year ->
                // Combine holidays from adjacent years to ensure smooth transitions
                combine(
                    getHolidaysForYearUseCase("IN", year - 1).onStart { emit(emptyList()) },
                    getHolidaysForYearUseCase("IN", year).onStart { emit(emptyList()) },
                    getHolidaysForYearUseCase("IN", year + 1).onStart { emit(emptyList()) },
                ) { prevYear, currentYear, nextYear ->
                    (prevYear + currentYear + nextYear).distinctBy { it.id }
                }
            }
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
            .onStart { emit(emptyList()) }
            .catch { exception ->
                handleError("Failed to load events", exception)
                emit(emptyList())
            }.shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )

    val uiState =
        combine(
            error,
            users.distinctUntilChanged(),
            holidays.distinctUntilChanged(),
            calendars.distinctUntilChanged(),
            events.distinctUntilChanged(),
        ) { currentError, usersList, holidaysList, calendarsList, eventsList ->
            CalendarUiState(
                accounts = usersList.toImmutableList(),
                holidays = holidaysList.toImmutableList(),
                calendars = calendarsList.toImmutableList(),
                events = eventsList.toImmutableList(),
                error = currentError,
            )
        }.distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = CalendarUiState(),
            )

    init {
        initializeData()
    }

    private fun initializeData() {
        // Holiday observation already refreshes through Store. A second perpetual
        // collector duplicated requests and prevented initialization from finishing.
        viewModelScope.launch {
            try {
                initializeUsers()
                initializeCalendars()
                initializeEvents()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                handleError("Initialization failed", exception)
            }
        }
    }

    private suspend fun initializeUsers() {
        runCatching {
            userRepository.getUserFromApi()
        }.onFailure { exception ->
            if (exception is CancellationException) throw exception
            handleError("Failed to initialize users", exception)
        }
    }

    private suspend fun initializeCalendars() {
        runCatching {
            calendarRepository.refreshCalendarsForUser(userId)
        }.onFailure { exception ->
            if (exception is CancellationException) throw exception
            handleError("Failed to initialize calendars", exception)
        }
    }

    private suspend fun initializeEvents() {
        runCatching {
            eventRepository.syncEventsForCalendar(emptyList(), startTime, endTime)
        }.onFailure { exception ->
            if (exception is CancellationException) throw exception
            handleError("Failed to initialize events", exception)
        }
    }

    private fun handleError(
        message: String,
        exception: Throwable,
    ) {
        AppLogger.e(exception) { "CalendarViewModel: $message" }
        val errorMessage = "$message: ${exception.message ?: "Unknown error"}"
        error.value = DomainError.Unknown(errorMessage)
    }

    fun clearError() {
        error.value = null
    }

}
