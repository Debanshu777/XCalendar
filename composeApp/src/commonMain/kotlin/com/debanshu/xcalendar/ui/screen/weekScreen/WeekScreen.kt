package com.debanshu.xcalendar.ui.screen.weekScreen

import androidx.compose.runtime.Composable
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.states.DateStateHolder
import com.debanshu.xcalendar.ui.CalendarUiState
import com.debanshu.xcalendar.ui.components.BaseCalendarScreen
import kotlinx.datetime.LocalDate

/**
 * Week view screen that displays a 7-day calendar view.
 */
@Composable
fun WeekScreen(
    dateStateHolder: DateStateHolder,
    events: List<Event>,
    holidays: List<Holiday>,
    onEventClick: (Event) -> Unit
) {
    BaseCalendarScreen(
        dateStateHolder = dateStateHolder,
        events = events,
        holidays = holidays,
        onEventClick = onEventClick,
        numDays = 7,
        getStartDate = { selectedDate ->
            // Get the start date for the week (Sunday)
            CalendarUiState.getWeekStartDate(selectedDate)
        }
    )
}