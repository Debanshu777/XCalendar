package com.debanshu.xcalendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.common.DateRangeHelper
import com.debanshu.xcalendar.domain.repository.ICalendarRepository
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.repository.IUserRepository
import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.model.User
import com.debanshu.xcalendar.domain.states.CalendarSessionState
import com.debanshu.xcalendar.domain.usecase.calendar.GetUserCalendarsUseCase
import com.debanshu.xcalendar.domain.usecase.event.GetEventsForDateRangeUseCase
import com.debanshu.xcalendar.domain.usecase.holiday.GetHolidaysForYearUseCase
import com.debanshu.xcalendar.di.HolidayCountryResolver
import com.debanshu.xcalendar.domain.usecase.user.GetCurrentUserUseCase
import com.debanshu.xcalendar.domain.util.DomainError
import com.debanshu.xcalendar.ui.state.DateStateHolder
import com.debanshu.xcalendar.ui.model.EventsByDate
import com.debanshu.xcalendar.ui.model.HolidaysByDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import com.debanshu.xcalendar.common.toLocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class CalendarViewModel(
    private val userRepository: IUserRepository,
    private val calendarRepository: ICalendarRepository,
    private val eventRepository: IEventRepository,
    private val dateStateHolder: DateStateHolder,
    getUserCalendarsUseCase: GetUserCalendarsUseCase,
    private val getEventsForDateRangeUseCase: GetEventsForDateRangeUseCase,
    private val getHolidaysForYearUseCase: GetHolidaysForYearUseCase,
    getCurrentUserUseCase: GetCurrentUserUseCase,
    private val holidayCountryResolver: HolidayCountryResolver,
) : ViewModel() {
    private val userId = getCurrentUserUseCase()
    private val _sessionState = MutableStateFlow(CalendarSessionState(isLoading = true))

    @OptIn(ExperimentalAtomicApi::class)
    private val isInitialized = AtomicBoolean(false)

    private val usersStream =
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
    private val holidaysStream =
        dateStateHolder.currentDateState
            .map { it.selectedInViewMonth.year }
            .distinctUntilChanged()
            .flatMapLatest { year ->
                val country = holidayCountryResolver.current()
                combine(
                    getHolidaysForYearUseCase(country, year - 1),
                    getHolidaysForYearUseCase(country, year),
                    getHolidaysForYearUseCase(country, year + 1),
                ) { prevYear, currentYear, nextYear ->
                    (prevYear + currentYear + nextYear).distinctBy { it.date }
                }
                .flowOn(Dispatchers.Default)
            }
            .catch { exception ->
                handleError("Failed to load holidays", exception)
                emit(emptyList())
            }.shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )

    private val calendarsStream =
        getUserCalendarsUseCase(userId)
            .catch { exception ->
                handleError("Failed to load calendars", exception)
                emit(emptyList())
            }.shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )

    /**
     * Events flow reacts to the current date — re-queried when today rolls
     * over midnight (audit F2). The query range is bucketed to whole months
     * via [DateRangeHelper.getMonthBucketRange] so day-to-day panning
     * resolves to the same [EventKey] and hits the Store cache instead of
     * re-fetching (audit F28).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventsStream =
        dateStateHolder.currentDateState
            .map { it.currentDate }
            .distinctUntilChanged()
            .flatMapLatest {
                val (startTime, endTime) = DateRangeHelper.getMonthBucketRange()
                getEventsForDateRangeUseCase(userId, startTime, endTime)
            }
            .catch { exception ->
                handleError("Failed to load events", exception)
                emit(emptyList())
            }.shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )

    /** Per-slice [StateFlow]s — composables collect only what they need (audit F18 / F27). */
    val accounts: StateFlow<ImmutableList<User>> =
        usersStream
            .map { it.toImmutableList() }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = persistentListOf(),
            )

    val holidays: StateFlow<ImmutableList<Holiday>> =
        holidaysStream
            .map { it.toImmutableList() }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = persistentListOf(),
            )

    val holidaysByDate: StateFlow<HolidaysByDate> =
        holidaysStream
            .map { holidays ->
                HolidaysByDate(
                    holidays.groupBy { holiday ->
                        holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    }.mapValues { (_, holidayList) ->
                        holidayList.toImmutableList()
                    }.toImmutableMap()
                )
            }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = HolidaysByDate(persistentMapOf()),
            )

    val calendars: StateFlow<ImmutableList<Calendar>> =
        calendarsStream
            .map { it.toImmutableList() }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = persistentListOf(),
            )

    val events: StateFlow<ImmutableList<Event>> =
        eventsStream
            .map { it.toImmutableList() }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = persistentListOf(),
            )

    val eventsByDate: StateFlow<EventsByDate> =
        eventsStream
            .map { events ->
                EventsByDate(
                    events.groupBy { event ->
                        event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    }.mapValues { (_, eventList) ->
                        eventList.toImmutableList()
                    }.toImmutableMap()
                )
            }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = EventsByDate(persistentMapOf()),
            )

    val isLoading: StateFlow<Boolean> =
        _sessionState
            .map { it.isLoading }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true,
            )

    val calendarError: StateFlow<DomainError?> =
        _sessionState
            .map { it.error }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

    init {
        initializeData()
        startMidnightTicker()
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

    /**
     * Drives [DateStateHolder.resetToToday] on each midnight crossover so
     * the events flow above refetches with the new date range. Lives for
     * the ViewModel lifetime; cancelled by viewModelScope on `onCleared`.
     */
    private fun startMidnightTicker() {
        viewModelScope.launch {
            dateStateHolder.midnightTicker().collect {
                dateStateHolder.resetToToday()
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

    private suspend fun initializeCalendars() {
        runCatching {
            calendarRepository.refreshCalendarsForUser(userId)
        }.onFailure { exception ->
            handleError("Failed to initialize calendars", exception)
        }
    }

    private suspend fun initializeEvents() {
        runCatching {
            val (startTime, endTime) = DateRangeHelper.getMonthBucketRange()
            eventRepository.syncEventsForCalendar(emptyList(), startTime, endTime)
        }.onFailure { exception ->
            handleError("Failed to initialize events", exception)
        }
    }

    private fun updateSession(update: (CalendarSessionState) -> CalendarSessionState) {
        _sessionState.update(update)
    }

    private fun updateLoadingState(isLoading: Boolean) {
        updateSession { it.copy(isLoading = isLoading) }
    }

    private fun handleError(
        message: String,
        exception: Throwable,
    ) {
        AppLogger.e(exception) { "CalendarViewModel: $message" }
        val errorMessage = "$message: ${exception.message ?: "Unknown error"}"
        updateSession { currentState ->
            currentState.copy(
                isLoading = false,
                error = DomainError.Unknown(errorMessage),
            )
        }
    }

    fun clearError() {
        updateSession { it.copy(error = null) }
    }

    /**
     * `DateStateHolder` is a Koin `@Single` and outlives the ViewModel
     * (audit F20). On Android process restore the same instance is handed
     * back to the next VM with potentially stale `selectedInViewMonth`. We
     * snap it back to today on teardown so the next VM starts from a clean
     * date state. Store5 state is isolated per [com.debanshu.xcalendar.di.UserSession]
     * scope; this hook remains for the shared [DateStateHolder] singleton.
     */
    @OptIn(ExperimentalAtomicApi::class)
    override fun onCleared() {
        super.onCleared()
        isInitialized.store(false)
        dateStateHolder.resetToToday()
    }
}
