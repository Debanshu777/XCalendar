package com.debanshu.xcalendar.data.google

import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.User
import io.ktor.http.encodeURLPathPart
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal val GOOGLE_SCOPES = listOf(
    "openid", "email", "profile",
    "https://www.googleapis.com/auth/calendar.calendarlist.readonly",
    "https://www.googleapis.com/auth/calendar.events",
)

@Serializable
internal data class GoogleProfile(val sub: String, val email: String = "", val name: String = "", val picture: String = "") {
    fun asUser() = User("google:$sub", name.ifBlank { email }, email, picture)
}

@Serializable
internal data class GoogleCalendar(
    val id: String,
    val summary: String = "Calendar",
    val backgroundColor: String = "#4285f4",
    val accessRole: String = "reader",
    val primary: Boolean = false,
    val timeZone: String = "UTC",
    val defaultReminders: List<GoogleReminder> = emptyList(),
) {
    val writable get() = accessRole == "owner" || accessRole == "writer"
    fun localId(account: String) = "google:$account:${id.encodeURLPathPart()}"
    fun asCalendar(account: String) = Calendar(
        localId(account), summary, color(), "google:$account", isPrimary = primary,
    )
    fun color(): Int = (0xff000000L or (backgroundColor.removePrefix("#").toLongOrNull(16) ?: 0x4285f4)).toInt()
}

@Serializable
internal data class GoogleDateTime(val date: String? = null, val dateTime: String? = null, val timeZone: String? = null) {
    fun millis(displayZone: TimeZone, calendarZone: String = "UTC"): Long {
        date?.let { return LocalDate.parse(it).atStartOfDayIn(displayZone).toEpochMilliseconds() }
        val value = requireNotNull(dateTime)
        val instant = runCatching { Instant.parse(value) }.getOrElse {
            LocalDateTime.parse(value).toInstant(TimeZone.of(timeZone ?: calendarZone))
        }
        return instant.toEpochMilliseconds()
    }
}

@Serializable
internal data class GoogleReminder(val method: String = "popup", val minutes: Int)
@Serializable
internal data class GoogleReminders(val useDefault: Boolean = true, val overrides: List<GoogleReminder> = emptyList())
@Serializable
internal data class GoogleEvent(
    val id: String,
    val etag: String = "",
    val status: String = "confirmed",
    val summary: String = "(No title)",
    val description: String? = null,
    val location: String? = null,
    val start: GoogleDateTime? = null,
    val end: GoogleDateTime? = null,
    val recurringEventId: String? = null,
    val eventType: String = "default",
    val reminders: GoogleReminders = GoogleReminders(),
) {
    fun localId(calendar: GoogleCalendar, account: String) = "${calendar.localId(account)}:${id.encodeURLPathPart()}"
    fun asEvent(calendar: GoogleCalendar, account: String, zone: TimeZone = TimeZone.currentSystemDefault()): Event? {
        if (status == "cancelled" || start == null || end == null) return null
        return Event(
            id = localId(calendar, account), calendarId = calendar.localId(account), calendarName = calendar.summary,
            title = summary, description = description, location = location,
            startTime = start.millis(zone, calendar.timeZone), endTime = end.millis(zone, calendar.timeZone), isAllDay = start.date != null,
            isRecurring = recurringEventId != null, color = calendar.color(), remoteEtag = etag,
            reminderMinutes = (if (reminders.useDefault) calendar.defaultReminders else reminders.overrides)
                .filter { it.method == "popup" }.map { it.minutes },
        )
    }
}

@Serializable
internal data class GoogleCalendarPage(val items: List<GoogleCalendar> = emptyList(), val nextPageToken: String? = null)
@Serializable
internal data class GoogleEventPage(val items: List<GoogleEvent> = emptyList(), val nextPageToken: String? = null)
@Serializable
internal data class GoogleCalendarData(val calendar: GoogleCalendar, val events: List<GoogleEvent>)
@Serializable
internal data class GoogleSnapshot(
    val profile: GoogleProfile,
    val calendars: List<GoogleCalendarData> = emptyList(),
    val syncedAt: Long = 0,
    val rangeStart: Long = 0,
    val rangeEnd: Long = 0,
)

/** PATCH only editor-owned fields; never replace attendees, conferencing, or recurrence. */
@Serializable
internal data class GoogleEventWrite(
    val summary: String,
    val description: String,
    val location: String,
    val start: GoogleDateTime,
    val end: GoogleDateTime,
    val reminders: GoogleReminders,
    val id: String? = null,
) {
    companion object {
        fun from(event: Event, original: GoogleEvent? = null, calendar: GoogleCalendar? = null, id: String? = null,
                 zone: TimeZone = TimeZone.currentSystemDefault()): GoogleEventWrite {
            fun date(millis: Long) = if (event.isAllDay) {
                GoogleDateTime(date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date.toString())
            } else GoogleDateTime(dateTime = Instant.fromEpochMilliseconds(millis).toString(), timeZone = zone.id)
            val previousReminders = original?.let {
                if (it.reminders.useDefault) calendar?.defaultReminders.orEmpty() else it.reminders.overrides
            }.orEmpty()
            val unchangedReminders = original != null && event.reminderMinutes == previousReminders.filter { it.method == "popup" }.map { it.minutes }
            return GoogleEventWrite(
                event.title, event.description.orEmpty(), event.location.orEmpty(), date(event.startTime), date(event.endTime),
                if (unchangedReminders) original!!.reminders else GoogleReminders(false,
                    previousReminders.filter { it.method != "popup" } + event.reminderMinutes.map { GoogleReminder(minutes = it) }), id,
            )
        }
    }
}

internal open class GoogleCalendarException(message: String) : Exception(message)
internal class GoogleEventConflictException :
    GoogleCalendarException("This event changed in Google Calendar. Load the latest version to review it before saving.")
internal class GoogleReauthRequiredException(message: String = "Reconnect this Google account to continue. Google may ask you to verify your identity.") : GoogleCalendarException(message)
internal class GoogleAccessTokenRejectedException : GoogleCalendarException("Google rejected the access token.")
