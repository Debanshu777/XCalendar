package com.debanshu.xcalendar.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.common.lengthOfMonth
import com.debanshu.xcalendar.data.remoteDataSource.CalendarApiService
import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.User
import com.debanshu.xcalendar.domain.repository.CalendarRepository
import com.debanshu.xcalendar.domain.repository.EventRepository
import com.debanshu.xcalendar.domain.repository.HolidayRepository
import com.debanshu.xcalendar.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class CalendarViewModel(
    private val userRepository: UserRepository,
    private val calendarRepository: CalendarRepository,
    private val eventRepository: EventRepository,
    //private val holidayRepository: HolidayRepository,
    private val apiService: CalendarApiService
) : ViewModel() {

    // State management
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    // Track visible calendars
    private val visibleCalendarIds = mutableStateOf<Set<String>>(emptySet())

    init {
        // Initialize with default data
        viewModelScope.launch {
            loadUsers()
        }
    }

    private suspend fun loadUsers() {
        // Load users from repository or create dummy users if none exist
        userRepository.getAllUsers().collectLatest { users ->
            if (users.isEmpty()) {
                // Create dummy user
                val dummyUser = User(
                    id = "dwdwdw",//UUID.randomUUID().toString(),
                    name = "Demo User",
                    email = "user@example.com"
                )
                userRepository.addUser(dummyUser)

                _uiState.update { it.copy(accounts = listOf(dummyUser)) }

                // Create dummy calendars for user
                loadCalendarsForUser(dummyUser.id)
            } else {
                _uiState.update { it.copy(accounts = users) }

                // Load calendars for all users
                users.forEach { user ->
                    loadCalendarsForUser(user.id)
                }
            }
        }
    }

    private suspend fun loadCalendarsForUser(userId: String) {
        // Try to load from database first
        calendarRepository.getCalendarsForUser(userId).collectLatest { dbCalendars ->
            if (dbCalendars.isEmpty()) {
                // Fetch from API and store in database
                val apiCalendars = apiService.fetchCalendarsForUser(userId)
                apiCalendars.forEach { calendar ->
                    calendarRepository.upsertCalendar(calendar)
                }

                // Update visible calendars
                updateVisibleCalendars(apiCalendars)

                // Update UI state
                _uiState.update { it.copy(calendars = _uiState.value.calendars + apiCalendars) }

                // Load events for these calendars
                loadEventsForCalendars(apiCalendars.map { it.id })
            } else {
                // Update visible calendars
                updateVisibleCalendars(dbCalendars)

                // Update UI state
                _uiState.update { it.copy(calendars = _uiState.value.calendars + dbCalendars) }

                // Load events for these calendars
                loadEventsForCalendars(dbCalendars.map { it.id })
            }
        }
    }

    private fun updateVisibleCalendars(calendars: List<Calendar>) {
        val visibleIds = calendars.filter { it.isVisible }.map { it.id }.toSet()
        visibleCalendarIds.value = visibleCalendarIds.value + visibleIds
    }

    private suspend fun loadEventsForCalendars(calendarIds: List<String>) {
        // Calculate date range (3 months before and after current date)
        val now = Clock.System.now()
        val currentDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startDate = currentDate.minus(DatePeriod(months = 3))
        val endDate = currentDate.plus(DatePeriod(months = 3))

        val startTime = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val endTime = endDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

        // Try to load from database first
        eventRepository.getEventsForCalendarsInRange(calendarIds, startTime, endTime).collectLatest { dbEvents ->
            if (dbEvents.isEmpty()) {
                // Fetch from API and store in database
                val allEvents = mutableListOf<Event>()

                calendarIds.forEach { calendarId ->
                    val apiEvents = apiService.fetchEventsForCalendar(calendarId, startTime, endTime)
                    apiEvents.forEach { event ->
                        eventRepository.addEvent(event)
                    }
                    allEvents.addAll(apiEvents)
                }

                // Update UI state
                _uiState.update {
                    it.copy(
                        events = allEvents,
                        upcomingEvents = CalendarUiState.getUpcomingEvents(allEvents, currentDate)
                    )
                }
            } else {
                // Update UI state
                _uiState.update {
                    it.copy(
                        events = dbEvents,
                        upcomingEvents = CalendarUiState.getUpcomingEvents(dbEvents, currentDate)
                    )
                }
            }
        }

        // Load holidays
        loadHolidays("IN", currentDate.year)
    }

    private suspend fun loadHolidays(countryCode: String, year: Int) {
        // Fetch holidays
        val holidays = apiService.fetchHolidays(countryCode, year)

        // Update UI state
        _uiState.update { it.copy(holidays = holidays) }
    }

    // UI actions
    fun selectDay(date: LocalDate) {
        val currentMonth = _uiState.value.selectedMonth
        val dateMonth = YearMonth(date.year, date.month)

        // If selecting a day from a different month, navigate to that month
        if (currentMonth != dateMonth) {
            selectMonth(date.month, date.year, false)
        }

        // Update the selected day
        _uiState.update {
            it.copy(
                selectedDay = date,
                weekStartDate = CalendarUiState.getWeekStartDate(date),
                threeDayStartDate = date
            )
        }
    }

    fun selectMonth(month: Month, year: Int, preserveSelectedDay: Boolean = true) {
        val yearMonth = YearMonth(year, month)
        val currentSelectedDay = _uiState.value.selectedDay

        // Calculate the same day in the new month if possible, otherwise use first day
        val newSelectedDay = if (preserveSelectedDay && _uiState.value.selectedDay != LocalDate(0, Month.JANUARY, 1)) {
            try {
                // Try to use the same day number in the new month
                val targetDayOfMonth = minOf(
                    currentSelectedDay.dayOfMonth,
                    month.lengthOfMonth(year.isLeap())
                )
                LocalDate(year, month, targetDayOfMonth)
            } catch (e: Exception) {
                // Fallback to first day of month if any error occurs
                LocalDate(year, month, 1)
            }
        } else {
            // Default to first day if not preserving or no valid day selected
            LocalDate(year, month, 1)
        }

        val weekStartDate = CalendarUiState.getWeekStartDate(newSelectedDay)

        _uiState.update {
            it.copy(
                selectedMonth = yearMonth,
                selectedDay = newSelectedDay,
                weekStartDate = weekStartDate,
                threeDayStartDate = newSelectedDay
            )
        }

        // Preload events for this month range
        viewModelScope.launch {
            preloadEventsForMonth(yearMonth)
        }
    }

    fun selectToday() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        // Navigate to today's month while indicating not to preserve the selected day
        selectMonth(today.month, today.year, false)

        // Set today as the selected day
        _uiState.update {
            it.copy(
                selectedDay = today,
                weekStartDate = CalendarUiState.getWeekStartDate(today),
                threeDayStartDate = today
            )
        }
    }

    fun selectView(view: CalendarView) {
        _uiState.update { it.copy(currentView = view) }
    }

    fun setTopAppBarMonthDropdown(viewType: TopBarCalendarView) {
        _uiState.update { it.copy(showMonthDropdown = viewType) }
    }

    /**
     * Preload events for the given month and adjacent months
     */
    private fun preloadEventsForMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            // Calculate date range (one month before and after selected month)
            val startMonth = yearMonth.plusMonths(-1)
            val endMonth = yearMonth.plusMonths(1)

            val startDate = startMonth.atStartOfMonth()
            val endDate = endMonth.atEndOfMonth()

            val startTime = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            val endTime = endDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

            // Get visible calendar IDs
            val visibleCalendarIds = _uiState.value.calendars
                .filter { it.isVisible }
                .map { it.id }

            if (visibleCalendarIds.isEmpty()) return@launch

            // Load events for this date range - implementation depends on your repository
            // This is just an example that matches your existing code structure
            loadEventsForCalendars(visibleCalendarIds)
        }
    }

    fun toggleCalendarVisibility(calendar: Calendar) {
        val updatedCalendar = calendar.copy(isVisible = !calendar.isVisible)

        viewModelScope.launch {
            calendarRepository.upsertCalendar(updatedCalendar)

            // Update visible calendars tracking
            if (updatedCalendar.isVisible) {
                visibleCalendarIds.value = visibleCalendarIds.value + updatedCalendar.id
            } else {
                visibleCalendarIds.value = visibleCalendarIds.value - updatedCalendar.id
            }

            // Update UI state with the updated calendar
            _uiState.update {
                val updatedCalendars = it.calendars.map { cal ->
                    if (cal.id == calendar.id) updatedCalendar else cal
                }

                it.copy(calendars = updatedCalendars)
            }
        }
    }

    // Event management
    fun showAddEventDialog() {
        _uiState.update { it.copy(showAddEventDialog = true) }
    }

    fun hideAddEventDialog() {
        _uiState.update { it.copy(showAddEventDialog = false) }
    }

    fun selectEvent(event: Event) {
        _uiState.update { it.copy(selectedEvent = event) }
    }

    fun clearSelectedEvent() {
        _uiState.update { it.copy(selectedEvent = null) }
    }

    fun addEvent(event: Event) {
        viewModelScope.launch {
            eventRepository.addEvent(event)

            // Update UI state with the new event
            _uiState.update {
                val updatedEvents = it.events + event
                it.copy(
                    events = updatedEvents,
                    upcomingEvents = CalendarUiState.getUpcomingEvents(updatedEvents, it.selectedDay)
                )
            }
        }
    }

    fun editEvent(event: Event) {
        viewModelScope.launch {
            eventRepository.updateEvent(event)

            // Update UI state with the updated event
            _uiState.update {
                val updatedEvents = it.events.map { e ->
                    if (e.id == event.id) event else e
                }
                it.copy(
                    events = updatedEvents,
                    upcomingEvents = CalendarUiState.getUpcomingEvents(updatedEvents, it.selectedDay),
                    selectedEvent = null
                )
            }
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            eventRepository.deleteEvent(event)

            // Update UI state by removing the event
            _uiState.update {
                val updatedEvents = it.events.filter { e -> e.id != event.id }
                it.copy(
                    events = updatedEvents,
                    upcomingEvents = CalendarUiState.getUpcomingEvents(updatedEvents, it.selectedDay),
                    selectedEvent = null
                )
            }
        }
    }
}