package com.debanshu.xcalendar.ui.screen.weekScreen

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
 * Week view screen that displays a 7-day calendar view.
 */
@Composable
fun WeekScreen(
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
        numDays = 7,
        onDateClickCallback = onDateClickCallback,
    )
}
