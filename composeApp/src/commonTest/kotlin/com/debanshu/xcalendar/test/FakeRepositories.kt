package com.debanshu.xcalendar.test

import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.model.User
import com.debanshu.xcalendar.domain.repository.ICalendarRepository
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.repository.IHolidayRepository
import com.debanshu.xcalendar.domain.repository.IUserRepository
import com.debanshu.xcalendar.domain.repository.RepositoryException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake repositories for testing.
 * 
 * These provide in-memory implementations of repository interfaces
 * that can be easily configured for different test scenarios.
 * 
 * Benefits:
 * - Fast tests (no I/O)
 * - Predictable behavior
 * - Easy to configure test scenarios
 * - Reusable across test classes
 */

/**
 * Fake implementation of IEventRepository for testing.
 */
class FakeEventRepository(
    initialEvents: List<Event> = emptyList()
) : IEventRepository {
    
    private val _events = MutableStateFlow(initialEvents.toMutableList())
    
    // Configuration for test scenarios
    var shouldFailOnSync = false
    var shouldFailOnAdd = false
    var shouldFailOnUpdate = false
    var shouldFailOnDelete = false
    var syncDelayMs: Long = 0
    
    // Tracking for verification
    val syncCalls = mutableListOf<SyncCall>()
    val addedEvents = mutableListOf<Event>()
    val updatedEvents = mutableListOf<Event>()
    val deletedEvents = mutableListOf<Event>()
    
    data class SyncCall(
        val calendarIds: List<String>,
        val startTime: Long,
        val endTime: Long
    )
    
    override suspend fun syncEventsForCalendar(
        calendarIds: List<String>,
        startTime: Long,
        endTime: Long
    ) {
        syncCalls.add(SyncCall(calendarIds, startTime, endTime))
        
        if (shouldFailOnSync) {
            throw RepositoryException("Simulated sync failure")
        }
        
        if (syncDelayMs > 0) {
            kotlinx.coroutines.delay(syncDelayMs)
        }
    }
    
    override fun getEventsForCalendarsInRange(
        userId: String,
        start: Long,
        end: Long
    ): Flow<List<Event>> {
        return _events.map { events ->
            events.filter { event ->
                event.startTime >= start && event.endTime <= end
            }
        }
    }
    
    override suspend fun addEvent(event: Event) {
        if (shouldFailOnAdd) {
            throw RepositoryException("Simulated add failure")
        }
        
        addedEvents.add(event)
        _events.value = (_events.value + event).toMutableList()
    }
    
    override suspend fun updateEvent(event: Event) {
        if (shouldFailOnUpdate) {
            throw RepositoryException("Simulated update failure")
        }
        
        updatedEvents.add(event)
        _events.value = _events.value.map { 
            if (it.id == event.id) event else it 
        }.toMutableList()
    }
    
    override suspend fun deleteEvent(event: Event) {
        if (shouldFailOnDelete) {
            throw RepositoryException("Simulated delete failure")
        }
        
        deletedEvents.add(event)
        _events.value = _events.value.filter { it.id != event.id }.toMutableList()
    }
    
    // Test helpers
    
    fun reset() {
        _events.value = mutableListOf()
        syncCalls.clear()
        addedEvents.clear()
        updatedEvents.clear()
        deletedEvents.clear()
        shouldFailOnSync = false
        shouldFailOnAdd = false
        shouldFailOnUpdate = false
        shouldFailOnDelete = false
        syncDelayMs = 0
    }
    
    fun setEvents(events: List<Event>) {
        _events.value = events.toMutableList()
    }
}

/**
 * Fake implementation of ICalendarRepository for testing.
 */
class FakeCalendarRepository(
    initialCalendars: List<Calendar> = emptyList()
) : ICalendarRepository {
    
    private val _calendars = MutableStateFlow(initialCalendars.toMutableList())
    
    var shouldFailOnRefresh = false
    var refreshDelayMs: Long = 0
    
    val refreshCalls = mutableListOf<String>()
    val upsertedCalendars = mutableListOf<Calendar>()
    val deletedCalendars = mutableListOf<Calendar>()
    
    override suspend fun refreshCalendarsForUser(userId: String) {
        refreshCalls.add(userId)
        
        if (shouldFailOnRefresh) {
            throw RepositoryException("Simulated refresh failure")
        }
        
        if (refreshDelayMs > 0) {
            kotlinx.coroutines.delay(refreshDelayMs)
        }
    }
    
    override fun getCalendarsForUser(userId: String): Flow<List<Calendar>> {
        return _calendars.map { calendars ->
            calendars.filter { it.userId == userId }
        }
    }
    
    override suspend fun upsertCalendar(calendars: List<Calendar>) {
        upsertedCalendars.addAll(calendars)
        
        val currentCalendars = _calendars.value.toMutableList()
        calendars.forEach { calendar ->
            val index = currentCalendars.indexOfFirst { it.id == calendar.id }
            if (index >= 0) {
                currentCalendars[index] = calendar
            } else {
                currentCalendars.add(calendar)
            }
        }
        _calendars.value = currentCalendars
    }
    
    override suspend fun deleteCalendar(calendar: Calendar) {
        deletedCalendars.add(calendar)
        _calendars.value = _calendars.value.filter { it.id != calendar.id }.toMutableList()
    }
    
    fun reset() {
        _calendars.value = mutableListOf()
        refreshCalls.clear()
        upsertedCalendars.clear()
        deletedCalendars.clear()
        shouldFailOnRefresh = false
        refreshDelayMs = 0
    }
    
    fun setCalendars(calendars: List<Calendar>) {
        _calendars.value = calendars.toMutableList()
    }
}

/**
 * Fake implementation of IHolidayRepository for testing.
 */
class FakeHolidayRepository(
    initialHolidays: List<Holiday> = emptyList()
) : IHolidayRepository {
    
    private val _holidays = MutableStateFlow(initialHolidays.toMutableList())
    
    var shouldFailOnUpdate = false
    
    val updateCalls = mutableListOf<Pair<String, Int>>()
    
    override suspend fun updateHolidays(countryCode: String, year: Int) {
        updateCalls.add(Pair(countryCode, year))
        
        if (shouldFailOnUpdate) {
            throw RepositoryException("Simulated update failure")
        }
    }
    
    override fun getHolidaysForYear(
        countryCode: String,
        year: Int
    ): Flow<List<Holiday>> {
        return _holidays.map { holidays ->
            holidays.filter { 
                it.countryCode.equals(countryCode, ignoreCase = true)
            }
        }
    }
    
    fun reset() {
        _holidays.value = mutableListOf()
        updateCalls.clear()
        shouldFailOnUpdate = false
    }
    
    fun setHolidays(holidays: List<Holiday>) {
        _holidays.value = holidays.toMutableList()
    }
}

/**
 * Fake implementation of IUserRepository for testing.
 */
class FakeUserRepository(
    initialUsers: List<User> = emptyList()
) : IUserRepository {
    
    private val _users = MutableStateFlow(initialUsers.toMutableList())
    
    var shouldFailOnGetFromApi = false
    
    val addedUsers = mutableListOf<User>()
    val deletedUsers = mutableListOf<User>()
    
    override suspend fun getUserFromApi() {
        if (shouldFailOnGetFromApi) {
            throw RepositoryException("Simulated API failure")
        }
        
        // Simulate adding a default user from API
        val dummyUser = User(
            id = "api_user",
            name = "API User",
            email = "api@example.com",
            imageUrl = null
        )
        addUser(dummyUser)
    }
    
    override fun getAllUsers(): Flow<List<User>> = _users
    
    override suspend fun addUser(user: User) {
        addedUsers.add(user)
        _users.value = (_users.value + user).toMutableList()
    }
    
    override suspend fun deleteUser(user: User) {
        deletedUsers.add(user)
        _users.value = _users.value.filter { it.id != user.id }.toMutableList()
    }
    
    fun reset() {
        _users.value = mutableListOf()
        addedUsers.clear()
        deletedUsers.clear()
        shouldFailOnGetFromApi = false
    }
    
    fun setUsers(users: List<User>) {
        _users.value = users.toMutableList()
    }
}

