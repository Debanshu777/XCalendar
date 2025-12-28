package com.debanshu.xcalendar.domain.usecase.event

import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.repository.IEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetEventsForDateRangeUseCaseTest {

    private fun createTestEvent(
        id: String,
        title: String,
        startTime: Long,
        endTime: Long
    ) = Event(
        id = id,
        calendarId = "calendar-1",
        calendarName = "Test Calendar",
        title = title,
        description = null,
        location = null,
        startTime = startTime,
        endTime = endTime,
        isAllDay = false,
        isRecurring = false,
        recurringRule = null,
        reminderMinutes = emptyList(),
        color = 0xFF2196F3.toInt()
    )

    private class FakeEventRepository(
        private val events: List<Event> = emptyList()
    ) : IEventRepository {
        
        override suspend fun syncEventsForCalendar(
            calendarIds: List<String>,
            startTime: Long,
            endTime: Long
        ) {
            // Not needed for this test
        }

        override fun getEventsForCalendarsInRange(
            userId: String,
            start: Long,
            end: Long
        ): Flow<List<Event>> {
            // Filter events that fall within the date range
            val filtered = events.filter { event ->
                event.startTime >= start && event.endTime <= end
            }
            return flowOf(filtered)
        }

        override suspend fun addEvent(event: Event) {}
        override suspend fun updateEvent(event: Event) {}
        override suspend fun deleteEvent(event: Event) {}
    }

    @Test
    fun `invoke returns events within date range`() = runTest {
        // Given
        val startRange = 1704067200000L // 2024-01-01 00:00:00 UTC
        val endRange = 1704153600000L   // 2024-01-02 00:00:00 UTC
        
        val eventsInRange = listOf(
            createTestEvent("1", "Event 1", 1704070800000L, 1704074400000L),
            createTestEvent("2", "Event 2", 1704081600000L, 1704085200000L)
        )
        
        val eventsOutOfRange = listOf(
            createTestEvent("3", "Event Outside", 1704240000000L, 1704243600000L)
        )
        
        val fakeRepository = FakeEventRepository(eventsInRange + eventsOutOfRange)
        
        // When
        val result = fakeRepository.getEventsForCalendarsInRange(
            userId = "user_id",
            start = startRange,
            end = endRange
        ).first()
        
        // Then
        assertEquals(2, result.size)
        assertTrue(result.all { it.startTime >= startRange && it.endTime <= endRange })
    }

    @Test
    fun `invoke returns empty list when no events in range`() = runTest {
        // Given
        val startRange = 1704067200000L
        val endRange = 1704153600000L
        
        val eventsOutOfRange = listOf(
            createTestEvent("1", "Event Outside", 1704240000000L, 1704243600000L)
        )
        
        val fakeRepository = FakeEventRepository(eventsOutOfRange)
        
        // When
        val result = fakeRepository.getEventsForCalendarsInRange(
            userId = "user_id",
            start = startRange,
            end = endRange
        ).first()
        
        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `invoke returns empty list when repository has no events`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository(emptyList())
        
        // When
        val result = fakeRepository.getEventsForCalendarsInRange(
            userId = "user_id",
            start = 0L,
            end = Long.MAX_VALUE
        ).first()
        
        // Then
        assertTrue(result.isEmpty())
    }
}

