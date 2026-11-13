package com.debanshu.xcalendar.data.google

import com.debanshu.xcalendar.test.TestDataFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.Instant

class GoogleCalendarTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun client(handler: MockRequestHandler) = HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) { json(json) }
    }
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun followsEmptyPagesAndEncodesCalendarIds() = runTest {
        val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val client = client { request ->
            requests += request
            respond(if (request.url.parameters["pageToken"] == null) """{"items":[],"nextPageToken":"second"}"""
                else """{"items":[{"id":"event"}]}""", headers = headers)
        }
        try {
            val result = GoogleCalendarApi(client).events({ "test-token" }, "shared/name+tag@example.com", 0, 1000)
            assertEquals(listOf("event"), result.map { it.id })
            assertEquals(2, requests.size)
            assertEquals("second", requests.last().url.parameters["pageToken"])
            assertEquals("true", requests.last().url.parameters["singleEvents"])
            assertEquals("1970-01-01T00:00:00Z", requests.last().url.parameters["timeMin"])
            assertContains(requests.first().url.encodedPath, "shared%2Fname")
            assertEquals("Bearer test-token", requests.first().headers[HttpHeaders.Authorization])
        } finally { client.close() }
    }

    @Test
    fun failedSecondPageDoesNotReturnAnIncompleteList() = runTest {
        val client = client { request ->
            if (request.url.parameters["pageToken"] == null) respond("""{"items":[{"id":"first"}],"nextPageToken":"next"}""", headers = headers)
            else respond("{}", HttpStatusCode.ServiceUnavailable, headers)
        }
        try { assertFailsWith<GoogleCalendarException> { GoogleCalendarApi(client).events({ "token" }, "calendar", 0, 1000) } }
        finally { client.close() }
    }

    @Test
    fun updateUsesEtagAndDoesNotReplaceUneditedFields() = runTest {
        val client = client { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("\"v1\"", request.headers[HttpHeaders.IfMatch])
            val body = (request.body as TextContent).text
            assertFalse(body.contains("attendees"))
            assertFalse(body.contains("recurrence"))
            assertFalse(body.contains("\"id\""))
            assertFalse(body.contains("null"))
            respond("{}", HttpStatusCode.PreconditionFailed, headers)
        }
        try {
            val failure = assertFailsWith<GoogleCalendarException> {
                GoogleCalendarApi(client).update("token", "calendar", GoogleEvent("id", etag = "\"v1\""), GoogleEventWrite.from(TestDataFactory.createEvent()))
            }
            assertContains(failure.message.orEmpty(), "changed")
        } finally { client.close() }
    }

    @Test
    fun createRetryAcceptsTheSameEventWithNormalizedTimes() = runTest {
        val id = "0123456789abcdef0123456789abcdef"
        val client = client { request ->
            if (request.method == HttpMethod.Post) {
                assertContains((request.body as TextContent).text, id)
                respond("{}", HttpStatusCode.Conflict, headers)
            } else {
                assertTrue(request.url.encodedPath.endsWith(id))
                respond(
                    """{"id":"$id","summary":"Test Event","start":{"dateTime":"2024-01-01T05:30:00+05:30"},"end":{"dateTime":"2024-01-01T06:30:00+05:30"},"reminders":{"useDefault":false,"overrides":[{"method":"popup","minutes":15}]}}""",
                    headers = headers,
                )
            }
        }
        try { assertEquals(id, GoogleCalendarApi(client).create("token", "calendar", GoogleEventWrite.from(TestDataFactory.createEvent(description = null), id = id)).id) }
        finally { client.close() }
    }

    @Test
    fun createRetryRejectsAnExistingEventWhenTheDraftChanged() = runTest {
        val id = "0123456789abcdef0123456789abcdef"
        val client = client { request ->
            if (request.method == HttpMethod.Post) respond("{}", HttpStatusCode.Conflict, headers)
            else respond(
                """{"id":"$id","summary":"Original title","description":"Test description","start":{"dateTime":"2024-01-01T00:00:00Z"},"end":{"dateTime":"2024-01-01T01:00:00Z"},"reminders":{"useDefault":false,"overrides":[{"method":"popup","minutes":15}]}}""",
                headers = headers,
            )
        }
        try {
            val failure = assertFailsWith<GoogleCalendarException> {
                GoogleCalendarApi(client).create("token", "calendar", GoogleEventWrite.from(TestDataFactory.createEvent(title = "Changed title"), id = id))
            }
            assertContains(failure.message.orEmpty(), "different details")
            assertContains(failure.message.orEmpty(), "Sync and reopen")
        } finally { client.close() }
    }

    @Test
    fun allDayDatesStayFloatingAndUseAnExclusiveEndAcrossDst() {
        val calendar = GoogleCalendar("id")
        val remote = GoogleEvent("event", start = GoogleDateTime(date = "2026-03-08"), end = GoogleDateTime(date = "2026-03-09"))
        val zone = TimeZone.of("America/New_York")
        val event = assertNotNull(remote.asEvent(calendar, "account", zone))
        assertEquals(23 * 60 * 60 * 1000L, event.endTime - event.startTime)
        val payload = GoogleEventWrite.from(event, zone = zone)
        assertEquals("2026-03-08", payload.start.date)
        assertEquals("2026-03-09", payload.end.date)
        assertNull(payload.start.dateTime)
    }

    @Test
    fun timedEventsKeepOffsetsAndCancelledEventsAreRemoved() {
        val remote = GoogleEvent("event", start = GoogleDateTime(dateTime = "2026-11-01T01:30:00-04:00"), end = GoogleDateTime(dateTime = "2026-11-01T01:30:00-05:00"))
        val event = assertNotNull(remote.asEvent(GoogleCalendar("id"), "account"))
        assertEquals(3_600_000, event.endTime - event.startTime)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z").toEpochMilliseconds(), event.startTime)
        assertNull(remote.copy(status = "cancelled").asEvent(GoogleCalendar("id"), "account"))
    }

    @Test
    fun dateTimeWithoutOffsetUsesItsNamedTimeZone() {
        val value = GoogleDateTime(dateTime = "2026-11-10T10:00:00", timeZone = "Asia/Kolkata")
        assertEquals(Instant.parse("2026-11-10T04:30:00Z").toEpochMilliseconds(), value.millis(TimeZone.UTC))
    }

    @Test
    fun calendarAndEventIdsAreScopedToAccountAndCalendar() {
        val first = GoogleCalendar("shared")
        val second = GoogleCalendar("other")
        val event = GoogleEvent("same-id")
        assertNotEquals(event.localId(first, "a"), event.localId(first, "b"))
        assertNotEquals(event.localId(first, "a"), event.localId(second, "a"))
    }

    @Test
    fun preservesDefaultAndEmailRemindersWhenOnlyTitleChanges() {
        val calendar = GoogleCalendar("id", defaultReminders = listOf(GoogleReminder(minutes = 10), GoogleReminder("email", 60)))
        val original = GoogleEvent("event", start = GoogleDateTime(date = "2026-11-01"), end = GoogleDateTime(date = "2026-11-02"))
        val event = assertNotNull(original.asEvent(calendar, "account"))
        assertEquals(original.reminders, GoogleEventWrite.from(event.copy(title = "New"), original, calendar).reminders)
        val changed = GoogleEventWrite.from(event.copy(reminderMinutes = listOf(30)), original, calendar)
        assertTrue(GoogleReminder("email", 60) in changed.reminders.overrides)
        assertTrue(GoogleReminder(minutes = 30) in changed.reminders.overrides)
    }

    @Test
    fun oauthChecksStateRedirectAndDuplicateParameters() {
        val redirect = "http://127.0.0.1:1234/oauth2callback"
        assertEquals("code", checkedAuthorizationCode("$redirect?code=code&state=expected", redirect, "expected"))
        assertFailsWith<GoogleCalendarException> { checkedAuthorizationCode("$redirect?code=code&state=wrong", redirect, "expected") }
        assertFailsWith<GoogleCalendarException> { checkedAuthorizationCode("$redirect?code=code&state=expected&state=expected", redirect, "expected") }
        assertFailsWith<GoogleCalendarException> { checkedAuthorizationCode("http://127.0.0.1:9999/oauth2callback?code=code&state=expected", redirect, "expected") }
        assertFailsWith<GoogleCalendarException> { checkedAuthorizationCode("$redirect?error=access_denied&state=expected", redirect, "expected") }
        val ios = "com.googleusercontent.apps.test:/oauthredirect"
        assertEquals("code", checkedAuthorizationCode("$ios?code=code&state=expected", ios, "expected"))
    }
}
