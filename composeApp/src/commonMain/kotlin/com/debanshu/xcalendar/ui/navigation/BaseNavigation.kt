package com.debanshu.xcalendar.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.states.dateState.DateStateHolder
import com.debanshu.xcalendar.ui.screen.dayScreen.DayScreen
import com.debanshu.xcalendar.ui.screen.monthScreen.MonthScreen
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleScreen
import com.debanshu.xcalendar.ui.screen.threeDayScreen.ThreeDayScreen
import com.debanshu.xcalendar.ui.screen.weekScreen.WeekScreen

@Composable
fun BaseNavigation(
    modifier: Modifier,
    navController: NavHostController,
    dateStateHolder: DateStateHolder,
    events: () -> List<Event>,
    holidays: () -> List<Holiday>,
    onEventClick: (Event) -> Unit,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = NavigableScreen.Month.toString(),
    ) {
        composable(route = NavigableScreen.Month.toString()) {
            MonthScreen(
                dateStateHolder = dateStateHolder,
                events = events,
                holidays = holidays,
                onDateClick = {
                    navController.navigate(NavigableScreen.Day.toString())
                },
            )
        }
        composable(route = NavigableScreen.Week.toString()) {
            WeekScreen(
                dateStateHolder = dateStateHolder,
                events = events,
                holidays = holidays,
                onEventClick = onEventClick,
                onDateClickCallback = {
                    navController.navigate(NavigableScreen.Day.toString())
                },
            )
        }
        composable(route = NavigableScreen.Day.toString()) {
            DayScreen(
                dateStateHolder = dateStateHolder,
                events = events,
                holidays = holidays,
                onEventClick = onEventClick,
            )
        }
        composable(route = NavigableScreen.ThreeDay.toString()) {
            ThreeDayScreen(
                dateStateHolder = dateStateHolder,
                events = events,
                holidays = holidays,
                onEventClick = onEventClick,
                onDateClickCallback = {
                    navController.navigate(NavigableScreen.Day.toString())
                },
            )
        }
        composable(route = NavigableScreen.Schedule.toString()) {
            ScheduleScreen(
                dateStateHolder = dateStateHolder,
                events = events,
                holidays = holidays,
                onEventClick = onEventClick,
            )
        }
    }
}
