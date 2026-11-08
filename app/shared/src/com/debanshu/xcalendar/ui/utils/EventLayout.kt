package com.debanshu.xcalendar.ui.utils

import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

internal data class EventPlacement(
    val event: Event,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columnCount: Int,
)

/** Divide timed intervals into reusable columns; all-day events belong in the header. */
internal fun layoutDayEvents(
    events: List<Event>,
    date: LocalDate,
    timeRange: IntRange,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<EventPlacement> {
    val lower = timeRange.first * 60
    val upper = (timeRange.last + 1) * 60
    val dayStart = date.atStartOfDayIn(timeZone).toEpochMilliseconds()
    val dayEnd = date.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).toEpochMilliseconds()
    val intervals = events.mapNotNull { event ->
        if (event.isAllDay) return@mapNotNull null
        // Intersect real instants before projecting onto wall-clock minutes.
        // This rejects reversed/empty intervals and exclusive midnight ends.
        val startMillis = maxOf(event.startTime, dayStart)
        val endMillis = minOf(event.endTime, dayEnd)
        if (endMillis <= startMillis) return@mapNotNull null
        val start = startMillis.toLocalDateTime(timeZone)
        val end = endMillis.toLocalDateTime(timeZone)
        val startMinute = if (startMillis == dayStart) 0 else start.hour * 60 + start.minute
        val endMinute = when {
            endMillis == dayEnd -> 1440
            // A fixed 24-hour grid cannot draw a repeated DST hour twice. Keep
            // a fold-crossing event visible using elapsed duration from its
            // displayed start; details retain the exact original timestamps.
            end <= start -> startMinute + ((endMillis - startMillis + 59_999L) / 60_000L).toInt()
            else -> end.hour * 60 + end.minute + if (end.second > 0 || end.nanosecond > 0) 1 else 0
        }
        val clippedStart = maxOf(lower, startMinute)
        val clippedEnd = minOf(upper, 1440, endMinute)
        if (clippedEnd <= clippedStart) null
        else EventPlacement(event, clippedStart, clippedEnd, 0, 1)
    }.sortedWith(compareBy<EventPlacement> { it.startMinute }.thenByDescending { it.endMinute })
    val result = mutableListOf<EventPlacement>()
    val cluster = mutableListOf<EventPlacement>()
    val columnEnds = mutableListOf<Int>()
    var clusterEnd = Int.MIN_VALUE

    fun flush() {
        result.addAll(cluster.map { it.copy(columnCount = columnEnds.size) })
        cluster.clear()
        columnEnds.clear()
    }

    for (interval in intervals) {
        if (interval.startMinute >= clusterEnd) flush()
        val freeColumn = columnEnds.indexOfFirst { it <= interval.startMinute }
        val column = if (freeColumn < 0) columnEnds.size else freeColumn
        if (freeColumn < 0) columnEnds.add(interval.endMinute) else columnEnds[column] = interval.endMinute
        cluster.add(interval.copy(column = column))
        clusterEnd = maxOf(clusterEnd, interval.endMinute)
    }
    flush()
    return result
}
