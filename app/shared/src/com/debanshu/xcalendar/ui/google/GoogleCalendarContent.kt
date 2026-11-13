package com.debanshu.xcalendar.ui.google

import com.debanshu.xcalendar.data.google.GoogleSnapshot
import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.User
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone

internal data class GoogleCalendarContent(
    val accounts: ImmutableList<User>,
    val calendars: ImmutableList<Calendar>,
    val writableCalendars: ImmutableList<Calendar>,
    val events: ImmutableList<Event>,
)

/** Parse remote timestamps off the UI thread, once per actual data change.
 * A new sync timestamp/window alone must not rebuild every calendar page.
 */
internal fun Flow<List<GoogleSnapshot>>.calendarContent(
    timeZone: () -> TimeZone = TimeZone::currentSystemDefault,
): Flow<GoogleCalendarContent?> =
    map { snapshots -> snapshots.map { it.profile to it.calendars } to timeZone() }
        .distinctUntilChanged()
        .map { (accounts, zone) ->
            if (accounts.isEmpty()) null else {
                GoogleCalendarContent(
                    accounts = accounts.map { (profile, _) -> profile.asUser() }.toImmutableList(),
                    calendars = accounts.flatMap { (profile, entries) ->
                        entries.map { it.calendar.asCalendar(profile.sub) }
                    }.toImmutableList(),
                    writableCalendars = accounts.flatMap { (profile, entries) ->
                        entries.filter { it.calendar.writable }.map { it.calendar.asCalendar(profile.sub) }
                    }.toImmutableList(),
                    events = accounts.flatMap { (profile, entries) ->
                        entries.flatMap { entry -> entry.events.mapNotNull { it.asEvent(entry.calendar, profile.sub, zone) } }
                    }.toImmutableList(),
                )
            }
        }
        .flowOn(Dispatchers.Default)

/** Visibility affects the combined view, not the account's sync or write access. */
internal fun GoogleCalendarContent.withHiddenCalendars(hiddenIds: Set<String>): GoogleCalendarContent =
    if (hiddenIds.isEmpty()) this else copy(
        calendars = calendars.map { it.copy(isVisible = it.id !in hiddenIds) }.toImmutableList(),
        events = events.filterNot { it.calendarId in hiddenIds }.toImmutableList(),
    )
