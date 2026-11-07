package com.debanshu.xcalendar.ui.viewmodel

import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.usecase.event.CreateEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.DeleteEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.UpdateEventUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EventViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // Fake repository for testing
    private class FakeEventRepository(
        var shouldFail: Boolean = false
    ) : IEventRepository {
        val addedEvents = mutableListOf<Event>()
        val updatedEvents = mutableListOf<Event>()
        val deletedEvents = mutableListOf<Event>()
        var writeGate: CompletableDeferred<Unit>? = null
        var cancelWrite = false
        var addAttempts = 0
        var updateAttempts = 0

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
            addAttempts++
            writeGate?.await()
            if (cancelWrite) throw CancellationException("Write cancelled")
            if (shouldFail) throw RuntimeException("SourceOfTruth write failed for ${event.id}")
            addedEvents.add(event)
        }

        override suspend fun updateEvent(event: Event) {
            updateAttempts++
            writeGate?.await()
            if (cancelWrite) throw CancellationException("Write cancelled")
            if (shouldFail) throw RuntimeException("SourceOfTruth write failed for ${event.id}")
            updatedEvents.add(event)
        }

        override suspend fun deleteEvent(event: Event) {
            if (shouldFail) throw RuntimeException("SourceOfTruth delete failed for ${event.id}")
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
        var dismissed = false
        viewModel.addEvent(invalidEvent) { dismissed = true }
        advanceUntilIdle()

        // Then
        assertEquals("Event title cannot be empty", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(dismissed)
    }

    @Test
    fun `addEvent suppresses duplicate submissions and dismisses only after persistence`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fakeRepository = FakeEventRepository().apply { writeGate = gate }
        val viewModel = createViewModel(fakeRepository)
        val event = createTestEvent()
        var dismissals = 0

        viewModel.addEvent(event) {
            assertEquals(listOf(event), fakeRepository.addedEvents)
            assertFalse(viewModel.uiState.value.isLoading)
            dismissals++
        }
        assertTrue(viewModel.uiState.value.isLoading)
        viewModel.addEvent(event) { dismissals++ }
        runCurrent()
        assertEquals(1, fakeRepository.addAttempts)
        assertEquals(0, dismissals)
        assertTrue(fakeRepository.addedEvents.isEmpty())

        viewModel.addEvent(event) { dismissals++ }
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, fakeRepository.addAttempts)
        assertEquals(1, dismissals)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `failed add keeps editor open and allows retry with the same event`() = runTest {
        val fakeRepository = FakeEventRepository(shouldFail = true)
        val viewModel = createViewModel(fakeRepository)
        val event = createTestEvent()
        var dismissals = 0

        viewModel.addEvent(event) { dismissals++ }
        advanceUntilIdle()

        assertEquals(0, dismissals)
        assertEquals("Couldn't save your changes. Please try again.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(fakeRepository.addedEvents.isEmpty())

        fakeRepository.shouldFail = false
        viewModel.addEvent(event) { dismissals++ }
        advanceUntilIdle()

        assertEquals(1, dismissals)
        assertEquals(listOf(event), fakeRepository.addedEvents)
        assertNull(viewModel.uiState.value.errorMessage)
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
    fun `failed edit preserves selection and successful retry updates the original id`() = runTest {
        val fakeRepository = FakeEventRepository(shouldFail = true)
        val viewModel = createViewModel(fakeRepository)
        val original = createTestEvent().copy(reminderMinutes = listOf(10, 30))
        val edited = original.copy(title = "Edited title")
        var dismissals = 0
        viewModel.selectEvent(original)

        viewModel.editEvent(edited) { dismissals++ }
        viewModel.editEvent(edited) { dismissals++ }
        advanceUntilIdle()

        assertEquals(1, fakeRepository.updateAttempts)
        assertEquals(original, viewModel.uiState.value.selectedEvent)
        assertEquals("Couldn't save your changes. Please try again.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(0, dismissals)

        fakeRepository.shouldFail = false
        viewModel.editEvent(edited) { dismissals++ }
        advanceUntilIdle()

        assertEquals(listOf(edited), fakeRepository.updatedEvents)
        assertEquals(1, dismissals)
        assertNull(viewModel.uiState.value.selectedEvent)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `cancelled edit releases submission guard without dismissing or losing selection`() = runTest {
        val fakeRepository = FakeEventRepository().apply { cancelWrite = true }
        val viewModel = createViewModel(fakeRepository)
        val event = createTestEvent()
        var dismissals = 0
        viewModel.selectEvent(event)

        viewModel.editEvent(event) { dismissals++ }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(event, viewModel.uiState.value.selectedEvent)
        assertEquals(0, dismissals)
        assertNull(viewModel.uiState.value.errorMessage)

        fakeRepository.cancelWrite = false
        viewModel.editEvent(event) { dismissals++ }
        advanceUntilIdle()
        assertEquals(listOf(event), fakeRepository.updatedEvents)
        assertEquals(1, dismissals)
    }

    @Test
    fun `finishing an edit does not clear a different event selected while saving`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fakeRepository = FakeEventRepository().apply { writeGate = gate }
        val viewModel = createViewModel(fakeRepository)
        val original = createTestEvent()
        val other = createTestEvent(id = "another-event")
        viewModel.selectEvent(original)

        viewModel.editEvent(original.copy(title = "Edited title"))
        runCurrent()
        viewModel.selectEvent(other)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(other, viewModel.uiState.value.selectedEvent)
        assertEquals(1, fakeRepository.updatedEvents.size)
        assertFalse(viewModel.uiState.value.isLoading)
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
    fun `failed delete preserves selection and hides persistence internals`() = runTest {
        val fakeRepository = FakeEventRepository(shouldFail = true)
        val viewModel = createViewModel(fakeRepository)
        val event = createTestEvent()
        viewModel.selectEvent(event)

        viewModel.deleteEvent(event)
        advanceUntilIdle()

        assertEquals("Couldn't delete this event. Please try again.", viewModel.uiState.value.errorMessage)
        assertEquals(event, viewModel.uiState.value.selectedEvent)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(fakeRepository.deletedEvents.isEmpty())
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
