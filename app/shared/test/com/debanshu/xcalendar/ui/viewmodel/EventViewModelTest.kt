package com.debanshu.xcalendar.ui.viewmodel

import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.usecase.event.CreateEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.DeleteEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.UpdateEventUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class EventViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // Fake repository for testing
    private class FakeEventRepository(
        private val shouldFail: Boolean = false
    ) : IEventRepository {
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
            if (shouldFail) throw RuntimeException("Test error")
            addedEvents.add(event)
        }

        override suspend fun updateEvent(event: Event) {
            if (shouldFail) throw RuntimeException("Test error")
            updatedEvents.add(event)
        }

        override suspend fun deleteEvent(event: Event) {
            if (shouldFail) throw RuntimeException("Test error")
            deletedEvents.add(event)
        }
    }

    private fun createTestEvent(
        id: String = "test-id-123",
        title: String = "Test Event"
    ) = Event(
        id = id,
        calendarId = "calendar-1",
        calendarName = "Test Calendar",
        title = title,
        description = null,
        location = null,
        startTime = 1704067200000L,
        endTime = 1704070800000L,
        isAllDay = false,
        isRecurring = false,
        recurringRule = null,
        reminderMinutes = emptyList(),
        color = 0xFF2196F3.toInt()
    )

    private fun createViewModel(fakeRepository: FakeEventRepository = FakeEventRepository()): EventViewModel {
        return EventViewModel(
            createEventUseCase = CreateEventUseCase(fakeRepository),
            updateEventUseCase = UpdateEventUseCase(fakeRepository),
            deleteEventUseCase = DeleteEventUseCase(fakeRepository)
        )
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectEvent updates selectedEvent in state`() = runTest {
        // Given
        val viewModel = createViewModel()
        val testEvent = createTestEvent()

        // When
        viewModel.selectEvent(testEvent)
        advanceUntilIdle()

        // Then
        assertEquals(testEvent, viewModel.uiState.value.selectedEvent)
    }

    @Test
    fun `clearSelectedEvent clears selectedEvent`() = runTest {
        // Given
        val viewModel = createViewModel()
        val testEvent = createTestEvent()
        viewModel.selectEvent(testEvent)
        advanceUntilIdle()

        // When
        viewModel.clearSelectedEvent()
        advanceUntilIdle()

        // Then
        assertNull(viewModel.uiState.value.selectedEvent)
    }

    @Test
    fun `addEvent success clears error message`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val viewModel = createViewModel(fakeRepository)
        val testEvent = createTestEvent()

        // When
        viewModel.addEvent(testEvent)
        advanceUntilIdle()

        // Then
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, fakeRepository.addedEvents.size)
    }

    @Test
    fun `addEvent with invalid title sets error message`() = runTest {
        // Given
        val viewModel = createViewModel()
        val invalidEvent = createTestEvent(title = "") // Empty title should fail validation

        // When
        viewModel.addEvent(invalidEvent)
        advanceUntilIdle()

        // Then
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `editEvent success clears selectedEvent and error`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val viewModel = createViewModel(fakeRepository)
        val testEvent = createTestEvent()
        viewModel.selectEvent(testEvent)
        advanceUntilIdle()

        // When
        viewModel.editEvent(testEvent)
        advanceUntilIdle()

        // Then
        assertNull(viewModel.uiState.value.selectedEvent)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, fakeRepository.updatedEvents.size)
    }

    @Test
    fun `deleteEvent success clears selectedEvent and error`() = runTest {
        // Given
        val fakeRepository = FakeEventRepository()
        val viewModel = createViewModel(fakeRepository)
        val testEvent = createTestEvent()
        viewModel.selectEvent(testEvent)
        advanceUntilIdle()

        // When
        viewModel.deleteEvent(testEvent)
        advanceUntilIdle()

        // Then
        assertNull(viewModel.uiState.value.selectedEvent)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, fakeRepository.deletedEvents.size)
    }

    @Test
    fun `clearError clears error message`() = runTest {
        // Given
        val viewModel = createViewModel()
        val invalidEvent = createTestEvent(title = "") // Will cause validation error
        viewModel.addEvent(invalidEvent)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        // When
        viewModel.clearError()
        advanceUntilIdle()

        // Then
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
