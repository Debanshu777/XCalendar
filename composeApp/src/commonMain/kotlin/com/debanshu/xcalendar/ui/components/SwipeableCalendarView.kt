package com.debanshu.xcalendar.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    startDate: () -> LocalDate,
    events: () -> List<Event>,
    holidays: () -> List<Holiday>,
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

    // Get current values reactively
    val currentEvents = events()
    val currentHolidays = holidays()

    val eventsByDate =
        remember(currentEvents) {
            currentEvents.groupBy { event ->
                event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
        }

    val holidaysByDate =
        remember(currentHolidays) {
            currentHolidays.groupBy { holiday ->
                holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
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
