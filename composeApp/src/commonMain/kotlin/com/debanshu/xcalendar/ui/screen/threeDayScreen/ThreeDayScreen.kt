package com.debanshu.xcalendar.ui.screen.threeDayScreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.state.DateStateHolder
import com.debanshu.xcalendar.ui.model.EventsByDate
import com.debanshu.xcalendar.ui.model.HolidaysByDate
import com.debanshu.xcalendar.ui.components.BaseCalendarScreen
import kotlinx.collections.immutable.ImmutableList

/**
 * Three-day view screen that displays a 3-day calendar view.
 */
@Composable
fun ThreeDayScreen(
    modifier: Modifier = Modifier,
    dateStateHolder: DateStateHolder,
    eventsByDate: EventsByDate,
    holidaysByDate: HolidaysByDate,
    isVisible: Boolean = true,
    onEventClick: (Event) -> Unit,
    onDateClickCallback: () -> Unit,
) {
    BaseCalendarScreen(
        modifier = modifier,
        dateStateHolder = dateStateHolder,
        eventsByDate = eventsByDate,
        holidaysByDate = holidaysByDate,
        isVisible = isVisible,
        onEventClick = onEventClick,
        numDays = 3,
        onDateClickCallback = onDateClickCallback,
    )
}
