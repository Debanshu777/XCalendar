package com.debanshu.xcalendar.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.ui.model.EventsByDate
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.debanshu.xcalendar.ui.transition.SharedElementType
import com.debanshu.xcalendar.ui.transition.sharedDayColumn
import com.debanshu.xcalendar.ui.transition.sharedEventElement
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Calendar events grid with column-first layout for shared element transitions.
 * Each day column is a shared element that animates when transitioning between
 * Week/ThreeDay/Day views - columns for the same date expand/contract.
 */
private val HourCellShape = RoundedCornerShape(10.dp)

@OptIn(ExperimentalTime::class)
@Composable
internal fun CalendarEventsGrid(
    startDate: LocalDate,
    numDays: Int,
    eventsByDate: EventsByDate,
    isVisible: Boolean = true,
    timeRange: IntRange,
    hourHeightDp: Float,
    onEventClick: (Event) -> Unit,
    currentDate: LocalDate,
    scrollState: ScrollState,
) {
    val dates = remember(startDate, numDays) {
        List(numDays) { index ->
            startDate.plus(DatePeriod(days = index))
        }
    }

    val totalHeight = timeRange.count() * hourHeightDp

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(XCalendarTheme.colorScheme.surfaceContainerLow),
    ) {
        // F10 optimization: Drive currentMinute via produceState ticker (1-min granularity)
        // instead of per-recomposition Clock.System.now() to prevent drift.
        val currentMinute by produceState(
            initialValue = run {
                val now = Clock.System.now().toEpochMilliseconds().toLocalDateTime(TimeZone.currentSystemDefault())
                now.hour * 60 + now.minute
            },
            key1 = Unit
        ) {
            while (true) {
                val now = Clock.System.now().toEpochMilliseconds().toLocalDateTime(TimeZone.currentSystemDefault())
                val newMinute = now.hour * 60 + now.minute
                value = newMinute
                
                // Wait until the next minute boundary
                val secondsUntilNextMinute = 60 - now.second
                delay(secondsUntilNextMinute * 1000L)
            }
        }

        // Column-first layout: Each day column is a shared element
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            dates.forEachIndexed { _, date ->
                val dayEvents = eventsByDate[date]
                val isCurrentDay = date == currentDate

                // Each day column with shared element transition
                DayColumn(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(totalHeight.dp)
                            .sharedDayColumn(
                                date = date,
                                isVisible = isVisible,
                            ),
                    date = date,
                    events = dayEvents,
                    timeRange = timeRange,
                    hourHeightDp = hourHeightDp,
                    isCurrentDay = isCurrentDay,
                    currentMinute = currentMinute,
                    isVisible = isVisible,
                    onEventClick = onEventClick,
                )
            }
        }
    }
}

private data class EventLocalTimes(
    val localStart: LocalDateTime,
    val localEnd: LocalDateTime,
)

/**
 * Min-heap of (lastEndTimeMillis, groupId) for O(log N) interval partitioning.
 * Tie-break on groupId for deterministic behaviour.
 */
private class EndTimeMinHeap {
    private val heap = mutableListOf<Pair<Long, Int>>()

    fun isNotEmpty(): Boolean = heap.isNotEmpty()

    fun peek(): Pair<Long, Int> = heap.first()

    fun poll(): Pair<Long, Int> {
        val result = heap.first()
        if (heap.size == 1) {
            heap.clear()
            return result
        }
        heap[0] = heap.removeAt(heap.lastIndex)
        siftDown(0)
        return result
    }

    fun add(
        lastEnd: Long,
        groupId: Int,
    ) {
        heap.add(lastEnd to groupId)
        siftUp(heap.lastIndex)
    }

    private fun less(
        a: Pair<Long, Int>,
        b: Pair<Long, Int>,
    ): Boolean = a.first < b.first || (a.first == b.first && a.second < b.second)

    private fun siftUp(i: Int) {
        var c = i
        while (c > 0) {
            val p = (c - 1) shr 1
            if (!less(heap[c], heap[p])) break
            swap(p, c)
            c = p
        }
    }

    private fun siftDown(i: Int) {
        var c = i
        val n = heap.size
        while (true) {
            val l = c * 2 + 1
            if (l >= n) break
            val r = l + 1
            var m = l
            if (r < n && less(heap[r], heap[l])) m = r
            if (!less(heap[m], heap[c])) break
            swap(m, c)
            c = m
        }
    }

    private fun swap(
        i: Int,
        j: Int,
    ) {
        val t = heap[i]
        heap[i] = heap[j]
        heap[j] = t
    }
}

/**
 * Greedy interval partitioning in start-time order: O(N log N) using a min-heap
 * of lane end times. Each map entry is a visual lane; events in the same lane
 * do not overlap in time.
 */
private fun groupOverlappingEvents(events: List<Event>): Map<Int, List<Event>> {
    if (events.isEmpty()) return emptyMap()
    val sorted = events.sortedBy { it.startTime }
    val heap = EndTimeMinHeap()
    val groups = LinkedHashMap<Int, MutableList<Event>>()
    var nextGroupId = 0

    for (event in sorted) {
        val freed = mutableListOf<Int>()
        while (heap.isNotEmpty() && heap.peek().first <= event.startTime) {
            freed.add(heap.poll().second)
        }
        val groupId = freed.minOrNull() ?: nextGroupId++
        groups.getOrPut(groupId) { mutableListOf() }.add(event)
        heap.add(event.endTime, groupId)
    }
    return groups
}

/**
 * A single day column in the calendar grid.
 * Contains time slot cells and events for that day.
 */
@Composable
private fun DayColumn(
    modifier: Modifier = Modifier,
    date: LocalDate,
    events: ImmutableList<Event>,
    timeRange: IntRange,
    hourHeightDp: Float,
    isCurrentDay: Boolean,
    currentMinute: Int,
    isVisible: Boolean,
    onEventClick: (Event) -> Unit,
) {
    Box(modifier = modifier) {
        // Background time slot cells
        Column(modifier = Modifier.fillMaxSize()) {
            val borderColor = XCalendarTheme.colorScheme.surfaceContainerLow
            val bgColor = XCalendarTheme.colorScheme.surfaceContainerHigh
            timeRange.forEach { _ ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(hourHeightDp.dp)
                            .border(
                                width = 2.dp,
                                color = borderColor,
                                shape = HourCellShape,
                            )
                            .clip(HourCellShape)
                            .background(bgColor),
                )
            }
        }

        // Current time indicator line
        if (isCurrentDay) {
            val offsetY = (currentMinute / 60f * hourHeightDp).dp
            Box(
                modifier =
                    Modifier
                        .offset(y = offsetY)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(XCalendarTheme.colorScheme.primary),
            )
        }

        val tz = TimeZone.currentSystemDefault()
        val localByEvent =
            remember(events, tz) {
                events.associateWith { e ->
                    EventLocalTimes(
                        localStart = e.startTime.toLocalDateTime(tz),
                        localEnd = e.endTime.toLocalDateTime(tz),
                    )
                }
            }
        val eventGroups = remember(events) { groupOverlappingEvents(events) }

        eventGroups.forEach { (_, group) ->
            val totalOverlapping = group.size

            group.forEach { event ->
                val span = localByEvent.getValue(event)
                val eventStart = span.localStart
                val eventEnd = span.localEnd
                val hour = eventStart.hour
                val minute = eventStart.minute

                if (hour in timeRange) {
                    val durationMinutes =
                        if (eventStart.date == eventEnd.date) {
                            (eventEnd.hour - hour) * 60 + (eventEnd.minute - minute)
                        } else {
                            (24 - hour) * 60 - minute
                        }

                    val topOffset =
                        (hour - timeRange.first) * hourHeightDp + (minute / 60f) * hourHeightDp
                    val eventHeight = (durationMinutes / 60f) * hourHeightDp

                    EventItem(
                        event = event,
                        onClick = { onEventClick(event) },
                        modifier =
                            Modifier
                                .offset(y = topOffset.dp)
                                .fillMaxWidth()
                                .height(eventHeight.dp.coerceAtLeast(30.dp))
                                .padding(1.dp),
                        isOverlapping = totalOverlapping > 1,
                        isVisible = isVisible,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventItem(
    event: Event,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOverlapping: Boolean = false,
    isVisible: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .sharedEventElement(
                    eventId = event.id,
                    type = SharedElementType.EventCard,
                    isVisible = isVisible,
                )
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, color = Color(event.color))
                .background(Color(event.color).copy(alpha = if (isOverlapping) 0.7f else 0.9f))
                .clickable(onClick = onClick)
                .padding(4.dp),
    ) {
        Text(
            text = event.title,
            style = XCalendarTheme.typography.labelSmall,
            color = XCalendarTheme.colorScheme.inverseOnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
