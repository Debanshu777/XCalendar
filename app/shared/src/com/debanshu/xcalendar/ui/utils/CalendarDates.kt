package com.debanshu.xcalendar.ui.utils

import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Sunday-first, six-week grid shared by the calendar and its date calculations. */
internal fun monthGridDates(month: YearMonth): List<LocalDate> {
    val first = LocalDate(month.year, month.month, 1)
    val start = first.minus(DatePeriod(days = (first.dayOfWeek.ordinal + 1) % 7))
    return List(42) { start.plus(DatePeriod(days = it)) }
}

/** Index the displayed interval, including events that began on an earlier day.
 * End times are exclusive; an event ending at midnight does not occupy the next day.
 * Clipping before expansion bounds long events by the visible window.
 */
internal fun indexEventsByDate(
    events: List<Event>,
    firstDate: LocalDate,
    lastDate: LocalDate,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ImmutableMap<LocalDate, ImmutableList<Event>> {
    val result = mutableMapOf<LocalDate, MutableList<Event>>()
    if (lastDate < firstDate || events.isEmpty()) return persistentMapOf()
    val windowStart = firstDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
    val windowEnd = lastDate.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).toEpochMilliseconds()
    for (event in events) {
        val lastInstant = if (event.endTime > event.startTime) event.endTime - 1 else event.startTime
        // A synced calendar contains several months of events. Reject off-page
        // instants before invoking native time-zone conversion on the UI thread.
        if (event.startTime >= windowEnd || lastInstant < windowStart) continue
        val start = if (event.startTime <= windowStart) firstDate else event.startTime.toLocalDateTime(timeZone).date
        val end = if (lastInstant >= windowEnd) lastDate else lastInstant.toLocalDateTime(timeZone).date
        var day = maxOf(start, firstDate)
        val last = minOf(end, lastDate)
        while (day <= last) {
            result.getOrPut(day) { mutableListOf() }.add(event)
            day = day.plus(DatePeriod(days = 1))
        }
    }
    return result.mapValues { (_, value) ->
        value.sortedWith(compareBy<Event> { it.startTime }.thenBy { it.id }).toImmutableList()
    }.toImmutableMap()
}
