package com.debanshu.xcalendar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.debanshu.xcalendar.ui.utils.layoutDayEvents
import com.debanshu.xcalendar.ui.utils.rememberEventContentColor
import com.debanshu.xcalendar.ui.transition.SharedElementType
import com.debanshu.xcalendar.ui.transition.sharedDayColumn
import com.debanshu.xcalendar.ui.transition.sharedEventElement
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

/**
 * Calendar events grid with column-first layout for shared element transitions.
 * Each day column is a shared element that animates when transitioning between
 * Week/ThreeDay/Day views - columns for the same date expand/contract.
 */
@OptIn(ExperimentalTime::class)
@Composable
internal fun CalendarEventsGrid(
    startDate: LocalDate,
    numDays: Int,
    eventsByDate: ImmutableMap<LocalDate, ImmutableList<Event>>,
    isVisible: Boolean = true,
    timeRange: IntRange,
    hourHeightDp: Float,
    onEventClick: (Event) -> Unit,
    currentDate: LocalDate,
    scrollState: ScrollState,
) {
    val dates =
        remember(startDate, numDays) {
            List(numDays) { index -> startDate.plus(DatePeriod(days = index)) }
        }

    val totalHeight = timeRange.count() * hourHeightDp

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(XCalendarTheme.colorScheme.surfaceContainerLow),
    ) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentMinute = now.hour * 60 + now.minute

        // Column-first layout: Each day column is a shared element
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            dates.forEach { date ->
                val dayEvents = eventsByDate[date] ?: persistentListOf()
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
        val cellColor = XCalendarTheme.colorScheme.surfaceContainerHigh
        // Decorative hour cells have no semantics. Draw them in one node instead
        // of composing/measuring 24 boxes for every visible day column.
        Canvas(Modifier.fillMaxSize()) {
            val inset = 2.dp.toPx()
            val hourHeight = hourHeightDp.dp.toPx()
            repeat(timeRange.count()) { hour ->
                drawRoundRect(
                    color = cellColor,
                    topLeft = Offset(inset, hour * hourHeight + inset),
                    size = Size((size.width - 2 * inset).coerceAtLeast(0f), (hourHeight - 2 * inset).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
        }

        // Current time indicator line
        if (isCurrentDay && currentMinute / 60 in timeRange) {
            val offsetY = ((currentMinute / 60f - timeRange.first) * hourHeightDp).dp
            Box(
                modifier =
                    Modifier
                        .offset(y = offsetY)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(XCalendarTheme.colorScheme.primary),
            )
        }

        val timeZone = TimeZone.currentSystemDefault()
        val placements = remember(events, date, timeRange, timeZone) {
            layoutDayEvents(events, date, timeRange, timeZone)
        }
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                placements.forEach { placement ->
                    key(placement.event.id) {
                        EventItem(
                            event = placement.event,
                            date = date,
                            onClick = { onEventClick(placement.event) },
                            modifier = Modifier.height(
                                ((placement.endMinute - placement.startMinute) / 60f * hourHeightDp).dp,
                            ).padding(1.dp),
                            isOverlapping = placement.columnCount > 1,
                            isVisible = isVisible,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val placeables = measurables.mapIndexed { index, measurable ->
                val placement = placements[index]
                val left = constraints.maxWidth * placement.column / placement.columnCount
                val right = constraints.maxWidth * (placement.column + 1) / placement.columnCount
                measurable.measure(constraints.copy(minWidth = right - left, maxWidth = right - left, minHeight = 0))
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { index, placeable ->
                    val placement = placements[index]
                    val x = constraints.maxWidth * placement.column / placement.columnCount
                    val y = ((placement.startMinute / 60f - timeRange.first) * hourHeightDp).dp.roundToPx()
                    placeable.placeRelative(x, y)
                }
            }
        }
    }
}

@Composable
private fun EventItem(
    event: Event,
    date: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOverlapping: Boolean = false,
    isVisible: Boolean = true,
) {
    val backgroundColor = Color(event.color).copy(alpha = if (isOverlapping) 0.7f else 0.9f)
    val textColor = rememberEventContentColor(backgroundColor, XCalendarTheme.colorScheme.surfaceContainerHigh)
    Box(
        modifier =
            modifier
                .sharedEventElement(
                    eventId = event.id,
                    date = date,
                    type = SharedElementType.EventCard,
                    isVisible = isVisible,
                )
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, color = Color(event.color))
                .background(backgroundColor)
                .clickable(onClick = onClick)
                // Keep the title on the full clickable card, even when a narrow
                // overlap column leaves no room to draw the text itself.
                .semantics { text = AnnotatedString(event.title) }
                .padding(4.dp),
    ) {
        Text(
            text = event.title,
            modifier = Modifier.clearAndSetSemantics {},
            style = XCalendarTheme.typography.labelSmall,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
