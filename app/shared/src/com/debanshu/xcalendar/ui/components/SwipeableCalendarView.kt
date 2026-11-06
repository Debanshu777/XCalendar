package com.debanshu.xcalendar.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/**
 * A swipeable calendar view that can be used for day, three-day or week views.
 *
 * This component uses the generic [SwipeablePager] for paging logic and provides
 * date-specific offset calculations and calendar content rendering.
 *
 * Uses a callback approach to ensure it always receives the current date state.
 *
 * @param modifier The modifier to apply to the component
 * @param startDate Lambda providing the first date to display
 * @param events Lambda providing the list of events to display
 * @param holidays Lambda providing the list of holidays to display
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
internal fun SwipeableCalendarView(
    modifier: Modifier = Modifier,
    startDate: LocalDate,
    events: ImmutableList<Event>,
    holidays: ImmutableList<Holiday>,
    isVisible: Boolean = true,
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

    val eventsByDate =
        remember(events) {
            events
                .groupBy { event ->
                    event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
                }.mapValues {
                    it.value.toImmutableList()
                }.toImmutableMap()
        }

    val holidaysByDate =
        remember(holidays) {
            holidays
                .groupBy { holiday ->
                    holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date
                }.mapValues {
                    it.value.toImmutableList()
                }.toImmutableMap()
        }

    SwipeablePager(
        modifier = modifier.fillMaxHeight(),
        currentReference = startDate,
        calculateOffset = { current, base ->
            val daysDiff = (current.toEpochDays() - base.toEpochDays()).toInt()
            daysDiff / numDays
        },
        pageToReference = { baseDate, initialPage, page ->
            val offset = (page - initialPage) * numDays
            baseDate.plus(DatePeriod(days = offset))
        },
        onReferenceChange = onDateRangeChange,
    ) { pageStartDate ->
        CalendarContent(
            startDate = pageStartDate,
            numDays = numDays,
            eventsByDate = eventsByDate,
            holidaysByDate = holidaysByDate,
            isVisible = isVisible,
            timeRange = timeRange,
            hourHeightDp = hourHeightDp,
            onDayClick = onDayClick,
            onEventClick = onEventClick,
            currentDate = currentDate,
            scrollState = scrollState,
            modifier = Modifier.fillMaxSize(),
            dynamicHeaderHeightState = dynamicHeaderHeightState,
        )
    }
}

@Composable
private fun CalendarContent(
    startDate: LocalDate,
    numDays: Int,
    eventsByDate: ImmutableMap<LocalDate, ImmutableList<Event>>,
    holidaysByDate: ImmutableMap<LocalDate, ImmutableList<Holiday>>,
    isVisible: Boolean = true,
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
            isVisible = isVisible,
            onDayClick = onDayClick,
            modifier = Modifier.fillMaxWidth(),
            dynamicHeaderHeightState = dynamicHeaderHeightState,
        )

        CalendarEventsGrid(
            startDate = startDate,
            numDays = numDays,
            eventsByDate = eventsByDate,
            isVisible = isVisible,
            timeRange = timeRange,
            hourHeightDp = hourHeightDp,
            onEventClick = onEventClick,
            currentDate = currentDate,
            scrollState = scrollState,
        )
    }
}
