package com.debanshu.xcalendar.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.screen.dayScreen.DayScreen
import com.debanshu.xcalendar.ui.screen.monthScreen.MonthScreen
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleScreen
import com.debanshu.xcalendar.ui.screen.threeDayScreen.ThreeDayScreen
import com.debanshu.xcalendar.ui.screen.weekScreen.WeekScreen
import com.debanshu.xcalendar.ui.state.DateStateHolder
import kotlinx.collections.immutable.ImmutableList

@Composable
fun NavigationHost(
    modifier: Modifier,
    backStack: NavBackStack<NavKey>,
    dateStateHolder: DateStateHolder,
    events: ImmutableList<Event>,
    holidays: ImmutableList<Holiday>,
    onEventClick: (Event) -> Unit,
) {
    // Track current screen for shared element visibility
    val currentScreen = backStack.lastOrNull()

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Calendar modes share moving date/event content. The iOS default
        // slide-and-veil transition leaves dark strips behind those elements.
        // Fade the scenes while their shared bounds animate independently.
        transitionSpec = { calendarViewTransition() },
        popTransitionSpec = { calendarViewTransition() },
        predictivePopTransitionSpec = { calendarViewTransition() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry(NavigableScreen.Month) {
                    MonthScreen(
                        dateStateHolder = dateStateHolder,
                        events = events,
                        holidays = holidays,
                        isVisible = currentScreen == NavigableScreen.Month,
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
                        isVisible = currentScreen == NavigableScreen.Week,
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
                        isVisible = currentScreen == NavigableScreen.Day,
                        onEventClick = onEventClick,
                    )
                }
                entry(NavigableScreen.ThreeDay) {
                    ThreeDayScreen(
                        dateStateHolder = dateStateHolder,
                        events = events,
                        holidays = holidays,
                        isVisible = currentScreen == NavigableScreen.ThreeDay,
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
                        isVisible = currentScreen == NavigableScreen.Schedule,
                        onEventClick = onEventClick,
                    )
                }
            },
    )
}

private fun calendarViewTransition(): ContentTransform =
    fadeIn(tween(durationMillis = 300)) togetherWith fadeOut(tween(durationMillis = 300))
