package com.debanshu.xcalendar.ui

import com.debanshu.xcalendar.data.google.*
import com.debanshu.xcalendar.ui.google.calendarContent
import com.debanshu.xcalendar.ui.google.withHiddenCalendars
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.*

class GoogleCalendarContentTest {
    @Test
    fun metadataOnlyRefreshDoesNotRebuildContentButEditsAndSignOutDo() = runTest {
        val calendar = GoogleCalendar("calendar", accessRole = "owner")
        val event = GoogleEvent("event", summary = "Before", start = GoogleDateTime(date = "2026-11-01"), end = GoogleDateTime(date = "2026-11-02"))
        val original = GoogleSnapshot(GoogleProfile("account"), listOf(GoogleCalendarData(calendar, listOf(event))))
        val refreshed = original.copy(syncedAt = 1000, rangeStart = 100, rangeEnd = 200)
        val edited = refreshed.copy(calendars = listOf(GoogleCalendarData(calendar, listOf(event.copy(summary = "After")))))
        val states = flowOf(emptyList(), listOf(original), listOf(refreshed), listOf(edited), emptyList())
            .calendarContent { TimeZone.UTC }.toList()
        assertEquals(4, states.size)
        assertNull(states.first())
        assertEquals("Before", states[1]!!.events.single().title)
        assertEquals("After", states[2]!!.events.single().title)
        assertEquals(calendar.asCalendar("account"), states[1]!!.writableCalendars.single())
        assertNull(states.last())
    }

    @Test
    fun projectionRetainsAccountAndReadOnlyCalendarAndOmitsCancelledEvents() = runTest {
        val calendar = GoogleCalendar("calendar", accessRole = "reader")
        val snapshot = GoogleSnapshot(GoogleProfile("account", name = "User"), listOf(
            GoogleCalendarData(calendar, listOf(GoogleEvent("cancelled", status = "cancelled"))),
        ))
        val content = flowOf(listOf(snapshot)).calendarContent { TimeZone.UTC }.toList().single()!!
        assertEquals("User", content.accounts.single().name)
        assertEquals(calendar.asCalendar("account"), content.calendars.single())
        assertTrue(content.writableCalendars.isEmpty())
        assertTrue(content.events.isEmpty())
    }

    @Test
    fun aggregatesSameRemoteIdsAcrossAccountsAndVisibilityIsAccountScoped() = runTest {
        val calendarA = GoogleCalendar("shared-calendar", summary = "A calendar", accessRole = "owner")
        val calendarB = GoogleCalendar("shared-calendar", summary = "B calendar", accessRole = "owner")
        val eventA = GoogleEvent("same-event", summary = "A event", start = GoogleDateTime(date = "2026-11-01"), end = GoogleDateTime(date = "2026-11-02"))
        val eventB = GoogleEvent("same-event", summary = "B event", start = GoogleDateTime(date = "2026-11-01"), end = GoogleDateTime(date = "2026-11-02"))
        val accountA = GoogleSnapshot(GoogleProfile("account-a", name = "A"), listOf(GoogleCalendarData(calendarA, listOf(eventA))))
        val accountB = GoogleSnapshot(GoogleProfile("account-b", name = "B"), listOf(GoogleCalendarData(calendarB, listOf(eventB))))
        val states = flowOf(emptyList(), listOf(accountA, accountB)).calendarContent { TimeZone.UTC }.toList()

        assertNull(states.first())
        val content = assertNotNull(states.last())
        assertEquals(listOf("A", "B"), content.accounts.map { it.name })
        assertEquals(2, content.calendars.size)
        assertEquals(2, content.writableCalendars.size)
        assertNotEquals(content.calendars[0].id, content.calendars[1].id)
        assertEquals(2, content.events.size)
        assertEquals(setOf("A event", "B event"), content.events.map { it.title }.toSet())
        assertNotEquals(content.events[0].id, content.events[1].id)

        val hiddenId = content.calendars.first { it.userId == "google:account-a" }.id
        val hidden = content.withHiddenCalendars(setOf(hiddenId))
        assertEquals(2, hidden.accounts.size)
        assertEquals(2, hidden.writableCalendars.size)
        assertFalse(hidden.calendars.single { it.id == hiddenId }.isVisible)
        assertTrue(hidden.calendars.single { it.id != hiddenId }.isVisible)
        assertEquals(listOf("B event"), hidden.events.map { it.title })

        val restored = content.withHiddenCalendars(emptySet())
        assertTrue(restored.calendars.all { it.isVisible })
        assertEquals(2, restored.events.size)
    }
}
