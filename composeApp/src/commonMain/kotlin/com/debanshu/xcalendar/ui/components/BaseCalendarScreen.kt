package com.debanshu.xcalendar.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.states.dateState.DateStateHolder
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.collections.immutable.ImmutableList

/**
 * Base calendar screen that provides common structure for day, three-day, and week views.
 *
 * Optimized with caching and efficient state management:
 * - Caches events and holidays to avoid repeated processing
 * - Optimized state management for smooth interactions
 * - Efficient date state handling
 *
 * @param dateStateHolder The date state holder
 * @param events The list of events to display
 * @param holidays The list of holidays to display
 * @param onEventClick Callback for when an event is clicked
 * @param numDays The number of days to display (1 for day view, 3 for three-day view, 7 for week view)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BaseCalendarScreen(
    modifier: Modifier = Modifier,
    dateStateHolder: DateStateHolder,
    events: ImmutableList<Event>,
    holidays: ImmutableList<Holiday>,
    onEventClick: (Event) -> Unit,
    onDateClickCallback: () -> Unit,
    numDays: Int,
) {
    val dateState by dateStateHolder.currentDateState.collectAsState()
    val verticalScrollState = rememberScrollState()
    val timeColumnWidth = 60.dp
    val timeRange = 0..23

    val isToday = dateState.selectedDate == dateState.currentDate

    // Cache dynamic header height to avoid recalculation
    val dynamicHeightOfHeaderComposableWithHolidays = remember { mutableStateOf(0) }
    val heightDp =
        with(LocalDensity.current) {
            dynamicHeightOfHeaderComposableWithHolidays.value.coerceAtLeast(160).toDp()
        }

    Row(
        modifier = modifier,
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .height(heightDp)
                        .width(timeColumnWidth)
                        .background(color = XCalendarTheme.colorScheme.surfaceContainerLow)
                        .animateContentSize(),
            ) {
                if (numDays == 1) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Text(
                            text =
                                dateState.selectedDate.dayOfWeek.name
                                    .take(3),
                            style = XCalendarTheme.typography.labelSmall,
                            color =
                                when {
                                    isToday -> XCalendarTheme.colorScheme.onPrimaryContainer
                                    else -> XCalendarTheme.colorScheme.onSurface
                                },
                        )
                        Box(
                            modifier =
                                Modifier
                                    .padding(vertical = 4.dp)
                                    .size(30.dp)
                                    .clip(MaterialShapes.Cookie9Sided.toShape())
                                    .background(
                                        when {
                                            isToday -> XCalendarTheme.colorScheme.primary
                                            else -> Color.Transparent
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = dateState.selectedDate.day.toString(),
                                style = XCalendarTheme.typography.bodyLarge,
                                color =
                                    when {
                                        isToday -> XCalendarTheme.colorScheme.inverseOnSurface
                                        else -> XCalendarTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    }
                }
            }
            TimeColumn(
                modifier =
                    Modifier
                        .background(XCalendarTheme.colorScheme.surfaceContainerLow)
                        .width(timeColumnWidth),
                timeRange = timeRange,
                scrollState = verticalScrollState,
            )
        }
        SwipeableCalendarView(
            startDate = dateState.selectedDate,
            events = events,
            holidays = holidays,
            onDayClick = { date ->
                dateStateHolder.updateSelectedDateState(date)
                onDateClickCallback()
            },
            onEventClick = onEventClick,
            onDateRangeChange = { newStartDate ->
                dateStateHolder.updateSelectedDateState(newStartDate)
            },
            numDays = numDays,
            timeRange = timeRange,
            scrollState = verticalScrollState,
            currentDate = dateState.currentDate,
            dynamicHeaderHeightState = dynamicHeightOfHeaderComposableWithHolidays,
        )
    }
}
