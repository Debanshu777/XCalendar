package com.debanshu.xcalendar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * A swipeable calendar view that can be used for day, three-day or week views.
 *
 * Optimized with sliding window approach and pre-calculated event/holiday mappings:
 * - Pre-calculates events and holidays by date to avoid repeated filtering
 * - Maintains efficient sliding window for smooth swiping
 * - Caches overlap detection results for better performance
 *
 * @param startDate The first date to display
 * @param events The list of events to display
 * @param holidays The list of holidays to display
 * @param onDayClick Callback for when a day is clicked
 * @param onEventClick Callback for when an event is clicked
 * @param onDateRangeChange Callback for when the date range changes due to swiping
 * @param numDays The number of days to display (1 for day view, 3 for three-day view, 7 for week view)
 * @param timeRange The range of hours to display
 * @param hourHeightDp The height of each hour cell
 * @param scrollState The scroll state to synchronize scrolling
 * @param currentDate The current date (today)
 * @param dynamicHeaderHeightState The height of the header row
 */
@Composable
fun SwipeableCalendarView(
    modifier: Modifier = Modifier,
    startDate: LocalDate,
    events: List<Event>,
    holidays: List<Holiday>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (Event) -> Unit,
    onDateRangeChange: (LocalDate) -> Unit,
    numDays: Int = 7,
    timeRange: IntRange = 0..23,
    hourHeightDp: Float = 60f,
    scrollState: ScrollState,
    currentDate: LocalDate,
    dynamicHeaderHeightState: MutableState<Int>,
) {
    require(numDays in 1..31) { "numDays must be between 1 and 31" }

    var size by remember { mutableStateOf(IntSize.Zero) }
    val screenWidth by derivedStateOf { size.width.toFloat() }
    var offsetX by remember { mutableStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    var targetOffsetX by remember { mutableStateOf(0f) }

    // Pre-calculate events and holidays by date for efficient lookup
    val eventsByDate =
        remember(events) {
            events.groupBy { event ->
                event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
        }

    val holidaysByDate =
        remember(holidays) {
            holidays.groupBy { holiday ->
                holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
        }

    // Optimized sliding window with persistent state
    val calendarWindow by remember {
        mutableStateOf(
            CalendarWindow(
                previous = startDate.minus(DatePeriod(days = numDays)),
                current = startDate,
                next = startDate.plus(DatePeriod(days = numDays)),
            ),
        )
    }

    // Update window only when startDate actually changes
    val startDateKey = remember(startDate) { startDate.toString() }
    remember(startDateKey) {
        if (calendarWindow.current != startDate) {
            calendarWindow.updateToDate(startDate, numDays)
        }
    }

    val animatedOffset by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = tween(durationMillis = 250), // Slightly faster animation
        finishedListener = {
            if (isAnimating) {
                if (targetOffsetX > 0) {
                    val newStartDate = calendarWindow.previous
                    calendarWindow.updateForPreviousDate(newStartDate, numDays)
                    onDateRangeChange(newStartDate)
                } else if (targetOffsetX < 0) {
                    val newStartDate = calendarWindow.next
                    calendarWindow.updateForNextDate(newStartDate, numDays)
                    onDateRangeChange(newStartDate)
                }
                offsetX = 0f
                isAnimating = false
            }
        },
    )

    val effectiveOffset = if (isAnimating) animatedOffset else offsetX

    Surface(
        modifier =
            modifier
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = screenWidth * 0.25f // More sensitive threshold
                            if (abs(offsetX) > threshold) {
                                isAnimating = true
                                targetOffsetX =
                                    if (offsetX > 0) {
                                        screenWidth
                                    } else {
                                        -screenWidth
                                    }
                            } else {
                                isAnimating = true
                                targetOffsetX = 0f
                            }
                        },
                        onDragCancel = {
                            isAnimating = true
                            targetOffsetX = 0f
                        },
                        onHorizontalDrag = { change, amount ->
                            // Optimize drag handling to reduce state updates
                            if (!isAnimating) {
                                val newOffsetX = offsetX + amount
                                offsetX = newOffsetX
                                targetOffsetX = newOffsetX
                                change.consume()
                            }
                        },
                    )
                },
    ) {
        CalendarContent(
            startDate = calendarWindow.current,
            numDays = numDays,
            eventsByDate = eventsByDate,
            holidaysByDate = holidaysByDate,
            timeRange = timeRange,
            hourHeightDp = hourHeightDp,
            onDayClick = onDayClick,
            onEventClick = onEventClick,
            currentDate = currentDate,
            scrollState = scrollState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(effectiveOffset.roundToInt(), 0)
                    },
            dynamicHeaderHeightState = dynamicHeaderHeightState,
        )

        CalendarContent(
            startDate = calendarWindow.previous,
            numDays = numDays,
            eventsByDate = eventsByDate,
            holidaysByDate = holidaysByDate,
            timeRange = timeRange,
            hourHeightDp = hourHeightDp,
            onDayClick = onDayClick,
            onEventClick = onEventClick,
            currentDate = currentDate,
            scrollState = scrollState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            -screenWidth.roundToInt() + effectiveOffset.roundToInt(),
                            0,
                        )
                    },
            dynamicHeaderHeightState = null,
        )

        CalendarContent(
            startDate = calendarWindow.next,
            numDays = numDays,
            eventsByDate = eventsByDate,
            holidaysByDate = holidaysByDate,
            timeRange = timeRange,
            hourHeightDp = hourHeightDp,
            onDayClick = onDayClick,
            onEventClick = onEventClick,
            currentDate = currentDate,
            scrollState = scrollState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            screenWidth.roundToInt() + effectiveOffset.roundToInt(),
                            0,
                        )
                    },
            dynamicHeaderHeightState = null,
        )
    }
}

@Composable
private fun CalendarContent(
    startDate: LocalDate,
    numDays: Int,
    eventsByDate: Map<LocalDate, List<Event>>,
    holidaysByDate: Map<LocalDate, List<Holiday>>,
    timeRange: IntRange,
    hourHeightDp: Float,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (Event) -> Unit,
    currentDate: LocalDate,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    dynamicHeaderHeightState: MutableState<Int>?,
) {
    Column(modifier) {
        DaysHeaderRow(
            startDate = startDate,
            numDays = numDays,
            currentDate = currentDate,
            holidaysByDate = holidaysByDate,
            onDayClick = onDayClick,
            modifier = Modifier.fillMaxWidth(),
            dynamicHeaderHeightState = dynamicHeaderHeightState,
        )

        CalendarEventsGrid(
            startDate = startDate,
            numDays = numDays,
            eventsByDate = eventsByDate,
            timeRange = timeRange,
            hourHeightDp = hourHeightDp,
            onEventClick = onEventClick,
            currentDate = currentDate,
            scrollState = scrollState,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DaysHeaderRow(
    startDate: LocalDate,
    numDays: Int,
    currentDate: LocalDate,
    holidaysByDate: Map<LocalDate, List<Holiday>>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    dynamicHeaderHeightState: MutableState<Int>?,
) {
    val dates =
        List(numDays) { index ->
            startDate.plus(DatePeriod(days = index))
        }

    Row(
        modifier =
            modifier
                .background(XCalendarTheme.colorScheme.surfaceContainerLow)
                .height(IntrinsicSize.Min)
                .heightIn(min = 60.dp)
                .onGloballyPositioned {
                    if (dynamicHeaderHeightState != null) {
                        dynamicHeaderHeightState.value = it.size.height
                    }
                },
    ) {
        if (numDays > 1) {
            dates.forEach { date ->
                val isToday = date == currentDate
                val currentDayHolidays = holidaysByDate[date] ?: emptyList()

                Column(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .padding(top = 8.dp)
                            .clickable { onDayClick(date) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = date.dayOfWeek.name.take(3),
                        style = XCalendarTheme.typography.labelSmall,
                    )
                    Box(
                        modifier =
                            Modifier
                                .padding(vertical = XCalendarTheme.dimensions.spacing_4)
                                .clip(MaterialShapes.Cookie9Sided.toShape())
                                .size(30.dp)
                                .background(
                                    when {
                                        isToday -> XCalendarTheme.colorScheme.primary
                                        else -> Color.Transparent
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = date.day.toString(),
                            style = XCalendarTheme.typography.bodyMedium,
                            color =
                                when {
                                    isToday -> XCalendarTheme.colorScheme.inverseOnSurface
                                    else -> XCalendarTheme.colorScheme.onSurface
                                },
                        )
                    }
                    if (currentDayHolidays.isNotEmpty()) {
                        Column {
                            currentDayHolidays.take(2).forEach { holiday ->
                                EventTag(
                                    modifier =
                                        Modifier
                                            .padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                                            .fillMaxWidth(),
                                    text = holiday.name,
                                    color = Color(0xFF007F73),
                                    textColor = XCalendarTheme.colorScheme.inverseOnSurface,
                                )
                            }

                            if (currentDayHolidays.size > 2) {
                                val extraCount = currentDayHolidays.size - 2
                                Text(
                                    text = "+$extraCount more",
                                    style = XCalendarTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = XCalendarTheme.colorScheme.onSurfaceVariant,
                                    modifier =
                                        Modifier
                                            .padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                                            .fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val currentDayHolidays = holidaysByDate[dates.first()] ?: emptyList()
            var holidaysExpanded by remember { mutableStateOf(false) }
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(1f),
            ) {
                if (currentDayHolidays.isNotEmpty()) {
                    val displayHolidays =
                        if (holidaysExpanded) {
                            currentDayHolidays
                        } else {
                            currentDayHolidays.take(2)
                        }
                    displayHolidays.forEach { holiday ->
                        Text(
                            text = holiday.name,
                            style = XCalendarTheme.typography.labelMedium,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = XCalendarTheme.colorScheme.inverseOnSurface,
                            modifier =
                                Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .fillMaxWidth()
                                    .background(
                                        Color(0xFF007F73),
                                        RoundedCornerShape(8.dp),
                                    ).padding(8.dp),
                        )
                    }

                    if (currentDayHolidays.size > 2 && !holidaysExpanded) {
                        val extraCount = currentDayHolidays.size - 2
                        Text(
                            text = "+$extraCount more",
                            style = XCalendarTheme.typography.labelMedium,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = XCalendarTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                                    .clickable { holidaysExpanded = true },
                        )
                    } else if (holidaysExpanded && currentDayHolidays.size > 2) {
                        Text(
                            text = "Show less",
                            style = XCalendarTheme.typography.labelMedium,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = XCalendarTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                                    .clickable { holidaysExpanded = false },
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun CalendarEventsGrid(
    startDate: LocalDate,
    numDays: Int,
    eventsByDate: Map<LocalDate, List<Event>>,
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
            val dayEvents = eventsByDate[date] ?: emptyList()

            // Group overlapping events with caching
            val eventGroups = remember(dayEvents) { groupOverlappingEvents(dayEvents) }

            eventGroups.forEach { (_, group) ->
                val totalOverlapping = group.size

                group.forEachIndexed { eventIndex, event ->
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

// Helper function to group overlapping events
private fun groupOverlappingEvents(events: List<Event>): Map<Int, List<Event>> {
    // Sort events by start time
    val sortedEvents = events.sortedBy { it.startTime }

    // Group events that overlap in time
    val groups = mutableMapOf<Int, MutableList<Event>>()
    var groupId = 0

    sortedEvents.forEach { event ->
        val eventStart = event.startTime
        val eventEnd = event.endTime

        // Find a group where this event doesn't overlap with any event in the group
        val existingGroup =
            groups.entries.firstOrNull { (_, groupEvents) ->
                groupEvents.none {
                    // Check if events overlap
                    (eventStart < it.endTime && eventEnd > it.startTime)
                }
            }

        if (existingGroup != null) {
            // Add to existing group
            existingGroup.value.add(event)
        } else {
            // Create a new group
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

/**
 * Optimized sliding window that maintains 3 date ranges for smooth swiping
 */
@Stable
private class CalendarWindow(
    previous: LocalDate,
    current: LocalDate,
    next: LocalDate,
) {
    var previous by mutableStateOf(previous)
    var current by mutableStateOf(current)
    var next by mutableStateOf(next)

    /**
     * Updates the window when swiping to the previous date range
     */
    fun updateForPreviousDate(
        newCurrentDate: LocalDate,
        numDays: Int,
    ) {
        next = current
        current = previous
        previous = newCurrentDate.minus(DatePeriod(days = numDays))
    }

    /**
     * Updates the window when swiping to the next date range
     */
    fun updateForNextDate(
        newCurrentDate: LocalDate,
        numDays: Int,
    ) {
        previous = current
        current = next
        next = newCurrentDate.plus(DatePeriod(days = numDays))
    }

    /**
     * Updates the window to a specific date (for external date changes)
     */
    fun updateToDate(
        targetDate: LocalDate,
        numDays: Int,
    ) {
        current = targetDate
        previous = targetDate.minus(DatePeriod(days = numDays))
        next = targetDate.plus(DatePeriod(days = numDays))
    }
}
