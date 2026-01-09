package com.debanshu.xcalendar.test

import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.model.User

/**
 * Factory for creating test data objects.
 *
 * Provides convenient builders for domain models with sensible defaults.
 * Use these in tests to avoid boilerplate and ensure consistent test data.
 */
object TestDataFactory {
    // ==================== User ====================

    fun createUser(
        id: String = "test-user-id",
        name: String = "Test User",
        email: String = "test@example.com",
        imageUrl: String? = null,
    ) = User(
        id = id,
        name = name,
        email = email,
        photoUrl = imageUrl ?: "",
    )

    fun createUsers(count: Int): List<User> =
        (1..count).map { index ->
            createUser(
                id = "user-$index",
                name = "User $index",
                email = "user$index@example.com",
            )
        }

    // ==================== Calendar ====================

    fun createCalendar(
        id: String = "test-calendar-id",
        name: String = "Test Calendar",
        color: Int = 0xFF2196F3.toInt(),
        userId: String = "test-user-id",
        isVisible: Boolean = true,
        isPrimary: Boolean = false,
    ) = Calendar(
        id = id,
        name = name,
        color = color,
        userId = userId,
        isVisible = isVisible,
        isPrimary = isPrimary,
    )

    fun createCalendars(
        count: Int,
        userId: String = "test-user-id",
    ): List<Calendar> =
        (1..count).map { index ->
            createCalendar(
                id = "calendar-$index",
                name = "Calendar $index",
                userId = userId,
                isPrimary = index == 1,
            )
        }

    // ==================== Event ====================

    // Base timestamp: 2024-01-01 00:00:00 UTC
    private const val BASE_TIMESTAMP = 1704067200000L
    private const val ONE_HOUR_MS = 3600000L
    private const val ONE_DAY_MS = 86400000L

    fun createEvent(
        id: String = "test-event-id",
        calendarId: String = "test-calendar-id",
        calendarName: String = "Test Calendar",
        title: String = "Test Event",
        description: String? = "Test description",
        location: String? = null,
        startTime: Long = BASE_TIMESTAMP,
        endTime: Long = BASE_TIMESTAMP + ONE_HOUR_MS,
        isAllDay: Boolean = false,
        isRecurring: Boolean = false,
        recurringRule: String? = null,
        reminderMinutes: List<Int> = listOf(15),
        color: Int = 0xFF2196F3.toInt(),
    ) = Event(
        id = id,
        calendarId = calendarId,
        calendarName = calendarName,
        title = title,
        description = description,
        location = location,
        startTime = startTime,
        endTime = endTime,
        isAllDay = isAllDay,
        isRecurring = isRecurring,
        recurringRule = recurringRule,
        reminderMinutes = reminderMinutes,
        color = color,
    )

    fun createEvents(
        count: Int,
        calendarId: String = "test-calendar-id",
        startFromTimestamp: Long = BASE_TIMESTAMP,
    ): List<Event> =
        (0 until count).map { index ->
            val startTime = startFromTimestamp + (index * ONE_DAY_MS)
            createEvent(
                id = "event-$index",
                calendarId = calendarId,
                title = "Event $index",
                startTime = startTime,
                endTime = startTime + ONE_HOUR_MS,
            )
        }

    fun createAllDayEvent(
        id: String = "all-day-event",
        title: String = "All Day Event",
        date: Long = BASE_TIMESTAMP,
    ) = createEvent(
        id = id,
        title = title,
        startTime = date,
        endTime = date,
        isAllDay = true,
    )

    fun createRecurringEvent(
        id: String = "recurring-event",
        title: String = "Recurring Event",
        startTime: Long = BASE_TIMESTAMP,
        recurringRule: String = "FREQ=WEEKLY;BYDAY=MO",
    ) = createEvent(
        id = id,
        title = title,
        startTime = startTime,
        endTime = startTime + ONE_HOUR_MS,
        isRecurring = true,
        recurringRule = recurringRule,
    )

    // ==================== Holiday ====================

    fun createHoliday(
        id: String = "test-holiday-id",
        name: String = "Test Holiday",
        description: String? = "Test holiday description",
        date: Long = BASE_TIMESTAMP,
        countryCode: String = "IN",
        type: String = "National holiday",
    ) = Holiday(
        id = id,
        name = name,
        date = date,
        countryCode = countryCode,
    )

    fun createHolidays(
        count: Int,
        countryCode: String = "IN",
        startFromTimestamp: Long = BASE_TIMESTAMP,
    ): List<Holiday> =
        (0 until count).map { index ->
            createHoliday(
                id = "holiday-$index",
                name = "Holiday $index",
                date = startFromTimestamp + (index * ONE_DAY_MS * 30), // ~monthly
                countryCode = countryCode,
            )
        }

    // ==================== Time Helpers ====================

    /**
     * Get timestamp for a specific day offset from base.
     */
    fun timestampForDay(daysFromBase: Int): Long = BASE_TIMESTAMP + (daysFromBase * ONE_DAY_MS)

    /**
     * Get timestamp range for a month (approximately).
     */
    fun timestampRangeForMonth(monthOffset: Int = 0): Pair<Long, Long> {
        val start = BASE_TIMESTAMP + (monthOffset * 30 * ONE_DAY_MS)
        val end = start + (30 * ONE_DAY_MS)
        return Pair(start, end)
    }
}
