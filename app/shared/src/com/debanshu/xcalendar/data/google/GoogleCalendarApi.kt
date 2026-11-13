package com.debanshu.xcalendar.data.google

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.Single

@Single
internal class GoogleCalendarApi(private val client: HttpClient) {
    private val wireJson = kotlinx.serialization.json.Json { explicitNulls = false; encodeDefaults = true }
    private val base = "https://www.googleapis.com/calendar/v3"

    suspend fun profile(token: String): GoogleProfile = checked(client.get("https://openidconnect.googleapis.com/v1/userinfo") {
        bearerAuth(token)
    }).decodeBody()

    suspend fun calendars(token: suspend () -> String): List<GoogleCalendar> {
        val items = mutableListOf<GoogleCalendar>()
        var page: String? = null
        do {
            val accessToken = token()
            val response: GoogleCalendarPage = checked(client.get("$base/users/me/calendarList") {
                bearerAuth(accessToken)
                parameter("maxResults", 250)
                page?.let { parameter("pageToken", it) }
            }).decodeBody()
            items += response.items.filter { it.accessRole in listOf("reader", "writer", "owner") }
            page = response.nextPageToken
        } while (!page.isNullOrEmpty())
        return items.distinctBy { it.id }.sortedByDescending { it.primary }
    }

    suspend fun events(token: suspend () -> String, calendar: String, start: Long, end: Long): List<GoogleEvent> {
        val items = mutableListOf<GoogleEvent>()
        var page: String? = null
        do {
            val accessToken = token()
            val response: GoogleEventPage = checked(client.get(eventsUrl(calendar)) {
                bearerAuth(accessToken)
                parameter("timeMin", kotlin.time.Instant.fromEpochMilliseconds(start).toString())
                parameter("timeMax", kotlin.time.Instant.fromEpochMilliseconds(end).toString())
                parameter("singleEvents", true)
                parameter("showDeleted", false)
                parameter("maxResults", 2500)
                page?.let { parameter("pageToken", it) }
            }).decodeBody()
            items += response.items.filter { it.status != "cancelled" }
            page = response.nextPageToken
        } while (!page.isNullOrEmpty())
        // Google can return equal-time events in a different order on refresh.
        // Keep snapshot equality and downstream placement stable in that case.
        return withContext(Dispatchers.Default) { items.distinctBy { it.id }.sortedBy { it.id } }
    }

    suspend fun create(token: String, calendar: String, body: GoogleEventWrite): GoogleEvent {
        val response = client.post(eventsUrl(calendar)) {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody(wireJson.encodeToString(body))
        }
        // A stable client-generated ID makes retries safe after a lost response.
        if (response.status == HttpStatusCode.Conflict && body.id != null) {
            val existing: GoogleEvent = checked(
                client.get("${eventsUrl(calendar)}/${body.id.encodeURLPathPart()}") { bearerAuth(token) },
            ).decodeBody()
            if (matchesCreate(existing, body)) return existing
            throw GoogleCalendarException(
                "An event with this ID already exists with different details. Sync and reopen the event before continuing.",
            )
        }
        return checked(response).decodeBody()
    }

    private fun matchesCreate(existing: GoogleEvent, submitted: GoogleEventWrite): Boolean =
        existing.id == submitted.id && existing.status == "confirmed" && existing.eventType == "default" &&
            existing.summary == submitted.summary &&
            existing.description.orEmpty() == submitted.description &&
            existing.location.orEmpty() == submitted.location &&
            sameDateTime(existing.start, submitted.start) &&
            sameDateTime(existing.end, submitted.end) &&
            existing.reminders.useDefault == submitted.reminders.useDefault &&
            existing.reminders.overrides.toSet() == submitted.reminders.overrides.toSet()

    private fun sameDateTime(existing: GoogleDateTime?, submitted: GoogleDateTime): Boolean {
        if (existing == null) return false
        if (existing.date != null || submitted.date != null) {
            return existing.date != null && submitted.date != null && existing.date == submitted.date
        }
        val existingMillis = runCatching {
            existing.millis(TimeZone.UTC, existing.timeZone ?: submitted.timeZone ?: "UTC")
        }.getOrNull() ?: return false
        val submittedMillis = runCatching {
            submitted.millis(TimeZone.UTC, submitted.timeZone ?: "UTC")
        }.getOrNull() ?: return false
        return existingMillis == submittedMillis
    }

    suspend fun update(token: String, calendar: String, original: GoogleEvent, body: GoogleEventWrite): GoogleEvent =
        checked(client.patch("${eventsUrl(calendar)}/${original.id.encodeURLPathPart()}") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            header(HttpHeaders.IfMatch, original.etag)
            setBody(wireJson.encodeToString(body))
        }).decodeBody()

    suspend fun event(token: String, calendar: String, eventId: String): GoogleEvent =
        checked(client.get("${eventsUrl(calendar)}/${eventId.encodeURLPathPart()}") {
            bearerAuth(token)
        }).decodeBody()

    // A suspend HTTP request does not move response deserialization off its
    // caller's dispatcher. Month sync can decode thousands of events while the
    // ViewModel is on Main. Keep that CPU work off the rendering thread, without
    // changing the context of token/auth callbacks or UI completion handlers.
    private suspend inline fun <reified T> HttpResponse.decodeBody(): T =
        withContext(Dispatchers.Default) { body<T>() }

    private fun eventsUrl(calendar: String) = "$base/calendars/${calendar.encodeURLPathPart()}/events"

    private fun checked(response: HttpResponse): HttpResponse {
        if (response.status.isSuccess()) return response
        throw GoogleCalendarException(when (response.status.value) {
            401 -> throw GoogleAccessTokenRejectedException()
            403 -> "Google denied access. Check Calendar permissions and whether this calendar is writable."
            404, 410 -> "This event or calendar is no longer available. Sync and try again."
            412 -> throw GoogleEventConflictException()
            429 -> "Google is receiving too many requests. Please try again shortly."
            else -> "Google Calendar is unavailable. Your changes have not been confirmed; please retry."
        })
    }
}
