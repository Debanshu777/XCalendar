package com.debanshu.xcalendar.domain.usecase.calendar

import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.repository.ICalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarUseCaseTest {

    private fun createTestCalendar(
        id: String,
        name: String,
        isVisible: Boolean = true,
        isPrimary: Boolean = false
    ) = Calendar(
        id = id,
        name = name,
        color = 0xFF2196F3.toInt(),
        userId = "user_id",
        isVisible = isVisible,
        isPrimary = isPrimary
    )

    private class FakeCalendarRepository(
        private val calendars: MutableList<Calendar> = mutableListOf()
    ) : ICalendarRepository {
        
        override suspend fun refreshCalendarsForUser(userId: String) {
            // Simulates API fetch - not needed for this test
        }

        override fun getCalendarsForUser(userId: String): Flow<List<Calendar>> {
            return flowOf(calendars.filter { it.userId == userId })
        }

        override suspend fun upsertCalendar(calendars: List<Calendar>) {
            calendars.forEach { calendar ->
                val index = this.calendars.indexOfFirst { it.id == calendar.id }
                if (index >= 0) {
                    this.calendars[index] = calendar
                } else {
                    this.calendars.add(calendar)
                }
            }
        }

        override suspend fun deleteCalendar(calendar: Calendar) {
            this.calendars.removeAll { it.id == calendar.id }
        }
    }

    @Test
    fun `getCalendarsForUser returns user calendars`() = runTest {
        // Given
        val userCalendars = listOf(
            createTestCalendar("1", "Work Calendar"),
            createTestCalendar("2", "Personal Calendar")
        )
        val fakeRepository = FakeCalendarRepository(userCalendars.toMutableList())
        
        // When
        val result = fakeRepository.getCalendarsForUser("user_id").first()
        
        // Then
        assertEquals(2, result.size)
        assertEquals("Work Calendar", result[0].name)
        assertEquals("Personal Calendar", result[1].name)
    }

    @Test
    fun `getCalendarsForUser returns empty for unknown user`() = runTest {
        // Given
        val calendars = listOf(
            createTestCalendar("1", "Work Calendar")
        )
        val fakeRepository = FakeCalendarRepository(calendars.toMutableList())
        
        // When
        val result = fakeRepository.getCalendarsForUser("unknown_user").first()
        
        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `upsertCalendar adds new calendar`() = runTest {
        // Given
        val fakeRepository = FakeCalendarRepository()
        val newCalendar = createTestCalendar("1", "New Calendar")
        
        // When
        fakeRepository.upsertCalendar(listOf(newCalendar))
        val result = fakeRepository.getCalendarsForUser("user_id").first()
        
        // Then
        assertEquals(1, result.size)
        assertEquals("New Calendar", result[0].name)
    }

    @Test
    fun `upsertCalendar updates existing calendar`() = runTest {
        // Given
        val originalCalendar = createTestCalendar("1", "Original Name", isVisible = true)
        val fakeRepository = FakeCalendarRepository(mutableListOf(originalCalendar))
        
        val updatedCalendar = originalCalendar.copy(name = "Updated Name", isVisible = false)
        
        // When
        fakeRepository.upsertCalendar(listOf(updatedCalendar))
        val result = fakeRepository.getCalendarsForUser("user_id").first()
        
        // Then
        assertEquals(1, result.size)
        assertEquals("Updated Name", result[0].name)
        assertFalse(result[0].isVisible)
    }

    @Test
    fun `toggleCalendarVisibility inverts visibility`() = runTest {
        // Given
        val calendar = createTestCalendar("1", "Test Calendar", isVisible = true)
        val fakeRepository = FakeCalendarRepository(mutableListOf(calendar))
        
        // When - toggle visibility
        val toggledCalendar = calendar.copy(isVisible = !calendar.isVisible)
        fakeRepository.upsertCalendar(listOf(toggledCalendar))
        
        val result = fakeRepository.getCalendarsForUser("user_id").first()
        
        // Then
        assertEquals(1, result.size)
        assertFalse(result[0].isVisible)
    }

    @Test
    fun `deleteCalendar removes calendar`() = runTest {
        // Given
        val calendar = createTestCalendar("1", "Calendar to Delete")
        val fakeRepository = FakeCalendarRepository(mutableListOf(calendar))
        
        // When
        fakeRepository.deleteCalendar(calendar)
        val result = fakeRepository.getCalendarsForUser("user_id").first()
        
        // Then
        assertTrue(result.isEmpty())
    }
}

