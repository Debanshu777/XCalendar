package com.debanshu.xcalendar.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
internal fun CalendarEventsGrid(
    startDate: LocalDate,
    numDays: Int,
    eventsByDate: ImmutableMap<LocalDate, ImmutableList<Event>>,
    timeRange: IntRange,
    hourHeightDp: Float,
    onEventClick: (Event) -> Unit,
    currentDate: LocalDate,
    scrollState: ScrollState,
) {
    val dates =
        List(numDays) { index ->
            startDate.plus(DatePeriod(days = index))
        }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(XCalendarTheme.colorScheme.surfaceContainerLow),
    ) {
        val dayColumnWidth = maxWidth / numDays
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentMinute = now.hour * 60 + now.minute

        Column {
            timeRange.forEach { _ ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(hourHeightDp.dp),
                ) {
                    repeat(numDays) {
                        Box(
                            Modifier
                                .weight(1f)
                                .border(
                                    width = 2.dp,
                                    color = XCalendarTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(10.dp),
                                ).fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(XCalendarTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                }
            }
        }

        if (dates.any { it == currentDate }) {
            val dayIndex = dates.indexOfFirst { it == currentDate }
            if (dayIndex >= 0) {
                val offsetX = dayColumnWidth * dayIndex
                val offsetY = (currentMinute / 60f * hourHeightDp).dp

                Box(
                    modifier =
                        Modifier
                            .offset(x = offsetX, y = offsetY)
                            .width(dayColumnWidth)
                            .height(2.dp)
                            .background(XCalendarTheme.colorScheme.primary),
                )
            }
        }

        // Process events by date using pre-calculated mapping
        dates.forEachIndexed { dayIndex, date ->
            val dayEvents = eventsByDate[date] ?: persistentListOf()

            // Group overlapping events with caching
            val eventGroups = remember(dayEvents) { groupOverlappingEvents(dayEvents) }

            eventGroups.forEach { (_, group) ->
                val totalOverlapping = group.size

                group.forEachIndexed { _, event ->
                    val eventStart =
                        event.startTime.toLocalDateTime(TimeZone.currentSystemDefault())
                    val eventEnd = event.endTime.toLocalDateTime(TimeZone.currentSystemDefault())
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

                        // Calculate width and horizontal position based on overlap
                        EventItem(
                            event = event,
                            onClick = { onEventClick(event) },
                            modifier =
                                Modifier
                                    .offset(
                                        x = dayColumnWidth * dayIndex,
                                        y = topOffset.dp,
                                    ).width(dayColumnWidth)
                                    .height(eventHeight.dp.coerceAtLeast(30.dp))
                                    .padding(1.dp),
                            isOverlapping = totalOverlapping > 1,
                        )
                    }
                }
            }
        }
    }
}

private fun groupOverlappingEvents(events: List<Event>): Map<Int, List<Event>> {
    val sortedEvents = events.sortedBy { it.startTime }
    val groups = mutableMapOf<Int, MutableList<Event>>()
    var groupId = 0

    sortedEvents.forEach { event ->
        val eventStart = event.startTime
        val eventEnd = event.endTime

        val existingGroup =
            groups.entries.firstOrNull { (_, groupEvents) ->
                groupEvents.none {
                    (eventStart < it.endTime && eventEnd > it.startTime)
                }
            }

        if (existingGroup != null) {
            existingGroup.value.add(event)
        } else {
            groups[groupId] = mutableListOf(event)
            groupId++
        }
    }

    return groups
}

@Composable
private fun EventItem(
    event: Event,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOverlapping: Boolean = false,
) {
    Box(
        modifier =
            modifier
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
