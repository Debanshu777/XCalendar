package com.debanshu.xcalendar.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.states.dateState.DateStateHolder
import com.debanshu.xcalendar.ui.screen.dayScreen.DayScreen
import com.debanshu.xcalendar.ui.screen.monthScreen.MonthScreen
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleScreen
import com.debanshu.xcalendar.ui.screen.threeDayScreen.ThreeDayScreen
import com.debanshu.xcalendar.ui.screen.weekScreen.WeekScreen

@Composable
fun NavigationHost(
    modifier: Modifier,
    backStack: MutableList<NavigableScreen>,
    dateStateHolder: DateStateHolder,
    events: List<Event>,
    holidays: List<Holiday>,
    onEventClick: (Event) -> Unit,
) {
    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider {
                entry(NavigableScreen.Month) {
                    MonthScreen(
                        dateStateHolder = dateStateHolder,
                        events = events,
                        holidays = holidays,
                        onDateClick = {
                            backStack.add(NavigableScreen.Day)
                        },
                    )
                }
                entry(NavigableScreen.Week) {
                    WeekScreen(
                        dateStateHolder = dateStateHolder,
                        events = events,
                        holidays = holidays,
                        onEventClick = onEventClick,
                        onDateClickCallback = {
                            backStack.add(NavigableScreen.Day)
                        },
                    )
                }
                entry(NavigableScreen.Day) {
                    DayScreen(
                        dateStateHolder = dateStateHolder,
                        events = events,
                        holidays = holidays,
                        onEventClick = onEventClick,
                    )
                }
                entry(NavigableScreen.ThreeDay) {
                    ThreeDayScreen(
                        dateStateHolder = dateStateHolder,
                        events = events,
                        holidays = holidays,
                        onEventClick = onEventClick,
                        onDateClickCallback = {
                            backStack.add(NavigableScreen.Day)
                        },
                    )
                }
                entry(NavigableScreen.Schedule) {
                    ScheduleScreen(
                        dateStateHolder = dateStateHolder,
                        events = events,
                        holidays = holidays,
                        onEventClick = onEventClick,
                    )
                }
            },
    )
}
