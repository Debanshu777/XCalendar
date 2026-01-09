package com.debanshu.xcalendar.domain.usecase.event

import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.util.DomainError
import com.debanshu.xcalendar.domain.util.DomainResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateEventUseCaseTest {

    // Fake repository for testing
    private class FakeEventRepository : IEventRepository {
        val addedEvents = mutableListOf<Event>()
        val updatedEvents = mutableListOf<Event>()
        val deletedEvents = mutableListOf<Event>()
        
        override suspend fun syncEventsForCalendar(
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
        val result = useCase(testEvent)
        
        // Then
        assertIs<DomainResult.Success<Unit>>(result)
        assertEquals(1, fakeRepository.addedEvents.size)
        assertEquals(testEvent, fakeRepository.addedEvents.first())
    }

    @Test
    fun `CreateEventUseCase returns error for blank title`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val useCase = CreateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(title = "   ")
        
        // When
        val result = useCase(invalidEvent)
        
        // Then
        assertIs<DomainResult.Error>(result)
        assertIs<DomainError.ValidationError>(result.error)
        assertTrue(fakeRepository.addedEvents.isEmpty())
    }

    @Test
    fun `CreateEventUseCase returns error for empty title`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val useCase = CreateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(title = "")
        
        // When
        val result = useCase(invalidEvent)
        
        // Then
        assertIs<DomainResult.Error>(result)
        assertIs<DomainError.ValidationError>(result.error)
    }

    @Test
    fun `CreateEventUseCase returns error when end time before start time`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val useCase = CreateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(
            startTime = 1704070800000L,  // Later time
            endTime = 1704067200000L     // Earlier time
        )
        
        // When
        val result = useCase(invalidEvent)
        
        // Then
        assertIs<DomainResult.Error>(result)
        assertIs<DomainError.ValidationError>(result.error)
    }

    @Test
    fun `CreateEventUseCase returns error for blank calendar ID`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val useCase = CreateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(calendarId = "")
        
        // When
        val result = useCase(invalidEvent)
        
        // Then
        assertIs<DomainResult.Error>(result)
        assertIs<DomainError.ValidationError>(result.error)
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
        val result = useCase(allDayEvent)
        
        // Then
        assertIs<DomainResult.Success<Unit>>(result)
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
        val result = updateUseCase(updatedEvent)
        
        // Then
        assertIs<DomainResult.Success<Unit>>(result)
        assertEquals(1, fakeRepository.updatedEvents.size)
        assertEquals("Updated Title", fakeRepository.addedEvents.first().title)
    }

    @Test
    fun `UpdateEventUseCase returns error for blank event ID`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val updateUseCase = UpdateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(id = "")
        
        // When
        val result = updateUseCase(invalidEvent)
        
        // Then
        assertIs<DomainResult.Error>(result)
        assertIs<DomainError.ValidationError>(result.error)
    }

    @Test
    fun `UpdateEventUseCase returns error for invalid event data`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val updateUseCase = UpdateEventUseCase(fakeRepository)
        val invalidEvent = createTestEvent(title = "")
        
        // When
        val result = updateUseCase(invalidEvent)
        
        // Then
        assertIs<DomainResult.Error>(result)
        assertIs<DomainError.ValidationError>(result.error)
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
        val result = deleteUseCase(testEvent)
        
        // Then
        assertIs<DomainResult.Success<Unit>>(result)
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
