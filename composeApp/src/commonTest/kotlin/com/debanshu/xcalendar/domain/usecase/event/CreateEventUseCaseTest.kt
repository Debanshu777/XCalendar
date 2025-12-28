package com.debanshu.xcalendar.domain.usecase.event

import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.util.EventValidationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CreateEventUseCaseTest {

    // Fake repository for testing
    private class FakeEventRepository : IEventRepository {
        val addedEvents = mutableListOf<Event>()
        val updatedEvents = mutableListOf<Event>()
        val deletedEvents = mutableListOf<Event>()
        
        override suspend fun getEventsForCalendar(
            calendarIds: List<String>,
            startTime: Long,
            endTime: Long
        ) {
            // Not needed for these tests
        }

        override fun getEventsForCalendarsInRange(
            userId: String,
            start: Long,
            end: Long
        ): Flow<List<Event>> = flowOf(addedEvents)

        override suspend fun addEvent(event: Event) {
            addedEvents.add(event)
        }

        override suspend fun updateEvent(event: Event) {
            updatedEvents.add(event)
            val index = addedEvents.indexOfFirst { it.id == event.id }
            if (index >= 0) {
                addedEvents[index] = event
            }
        }

        override suspend fun deleteEvent(event: Event) {
            deletedEvents.add(event)
            addedEvents.removeAll { it.id == event.id }
        }
    }

    private fun createTestEvent(
        id: String = "test-id-123",
        title: String = "Test Event",
        calendarId: String = "calendar-1",
        startTime: Long = 1704067200000L, // 2024-01-01 00:00:00 UTC
        endTime: Long = 1704070800000L,   // 2024-01-01 01:00:00 UTC
        isAllDay: Boolean = false
    ) = Event(
        id = id,
        calendarId = calendarId,
        calendarName = "Test Calendar",
        title = title,
        description = "Test Description",
        location = null,
        startTime = startTime,
        endTime = endTime,
        isAllDay = isAllDay,
        isRecurring = false,
        recurringRule = null,
        reminderMinutes = listOf(15),
        color = 0xFF2196F3.toInt()
    )

    // ==================== CreateEventUseCase Tests ====================

    @Test
    fun `CreateEventUseCase adds valid event to repository`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val useCase = CreateEventUseCase(fakeRepository)
        val testEvent = createTestEvent()
        
        // When
        useCase(testEvent)
        
        // Then
        assertEquals(1, fakeRepository.addedEvents.size)
        assertEquals(testEvent, fakeRepository.addedEvents.first())
    }

    @Test
    fun `CreateEventUseCase throws exception for blank title`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val useCase = CreateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(title = "   ")
        
        // When/Then
        assertFailsWith<EventValidationException> {
            useCase(invalidEvent)
        }
        
        // Verify event was not added
        assertTrue(fakeRepository.addedEvents.isEmpty())
    }

    @Test
    fun `CreateEventUseCase throws exception for empty title`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val useCase = CreateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(title = "")
        
        // When/Then
        assertFailsWith<EventValidationException> {
            useCase(invalidEvent)
        }
    }

    @Test
    fun `CreateEventUseCase throws exception when end time before start time`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val useCase = CreateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(
            startTime = 1704070800000L,  // Later time
            endTime = 1704067200000L     // Earlier time
        )
        
        // When/Then
        assertFailsWith<EventValidationException> {
            useCase(invalidEvent)
        }
    }

    @Test
    fun `CreateEventUseCase throws exception for blank calendar ID`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val useCase = CreateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(calendarId = "")
        
        // When/Then
        assertFailsWith<EventValidationException> {
            useCase(invalidEvent)
        }
    }

    @Test
    fun `CreateEventUseCase allows all-day event with same start and end`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val useCase = CreateEventUseCase(fakeRepository)
        val allDayEvent = createTestEvent(
            startTime = 1704067200000L,
            endTime = 1704067200000L,
            isAllDay = true
        )
        
        // When
        useCase(allDayEvent)
        
        // Then - should not throw
        assertEquals(1, fakeRepository.addedEvents.size)
    }

    // ==================== UpdateEventUseCase Tests ====================

    @Test
    fun `UpdateEventUseCase updates valid event in repository`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val createUseCase = CreateEventUseCase(fakeRepository)
        val updateUseCase = UpdateEventUseCase(fakeRepository)
        
        val originalEvent = createTestEvent(id = "event-1", title = "Original Title")
        createUseCase(originalEvent)
        
        val updatedEvent = originalEvent.copy(title = "Updated Title")
        
        // When
        updateUseCase(updatedEvent)
        
        // Then
        assertEquals(1, fakeRepository.updatedEvents.size)
        assertEquals("Updated Title", fakeRepository.addedEvents.first().title)
    }

    @Test
    fun `UpdateEventUseCase throws exception for blank event ID`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val updateUseCase = UpdateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(id = "")
        
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            updateUseCase(invalidEvent)
        }
    }

    @Test
    fun `UpdateEventUseCase validates event data`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val updateUseCase = UpdateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(title = "")
        
        // When/Then
        assertFailsWith<EventValidationException> {
            updateUseCase(invalidEvent)
        }
    }

    // ==================== DeleteEventUseCase Tests ====================

    @Test
    fun `DeleteEventUseCase removes event from repository`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val createUseCase = CreateEventUseCase(fakeRepository)
        val deleteUseCase = DeleteEventUseCase(fakeRepository)
        
        val testEvent = createTestEvent()
        createUseCase(testEvent)
        assertEquals(1, fakeRepository.addedEvents.size)
        
        // When
        deleteUseCase(testEvent)
        
        // Then
        assertEquals(1, fakeRepository.deletedEvents.size)
        assertTrue(fakeRepository.addedEvents.isEmpty())
    }

    // ==================== GetEventsForDateRangeUseCase Tests ====================

    @Test
    fun `GetEventsForDateRangeUseCase returns events from repository`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val createUseCase = CreateEventUseCase(fakeRepository)
        val getEventsUseCase = GetEventsForDateRangeUseCase(fakeRepository)
        
        val testEvent = createTestEvent()
        createUseCase(testEvent)
        
        // When
        val eventsFlow = getEventsUseCase("user_id", 0L, Long.MAX_VALUE)
        
        // Then
        eventsFlow.collect { events ->
            assertEquals(1, events.size)
            assertEquals(testEvent, events.first())
        }
    }
}
