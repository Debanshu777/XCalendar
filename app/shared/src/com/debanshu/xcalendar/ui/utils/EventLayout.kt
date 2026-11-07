package com.debanshu.xcalendar.ui.utils

import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

internal data class EventPlacement(
    val event: Event,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columnCount: Int,
)

/** Divide each connected group of overlapping intervals into reusable columns. */
internal fun layoutDayEvents(
    events: List<Event>,
    date: LocalDate,
    timeRange: IntRange,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<EventPlacement> {
    val lower = timeRange.first * 60
    val upper = (timeRange.last + 1) * 60
    val intervals = events.mapNotNull { event ->
        val start = event.startTime.toLocalDateTime(timeZone)
        val end = event.endTime.toLocalDateTime(timeZone)
        val startMinute = if (start.date < date) 0 else start.hour * 60 + start.minute
        val endMinute = if (end.date > date) 1440 else end.hour * 60 + end.minute
        val clippedStart = maxOf(lower, startMinute)
        val clippedEnd = minOf(upper, if (event.isAllDay && event.endTime <= event.startTime) 1440 else endMinute)
        if (start.date > date || end.date < date || clippedEnd <= clippedStart) null
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
