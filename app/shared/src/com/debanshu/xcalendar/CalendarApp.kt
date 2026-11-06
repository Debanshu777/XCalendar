package com.debanshu.xcalendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import com.debanshu.xcalendar.ui.CalendarViewModel
import com.debanshu.xcalendar.ui.components.CalendarBottomNavigationBar
import com.debanshu.xcalendar.ui.components.CalendarTopAppBar
import com.debanshu.xcalendar.ui.components.ErrorSnackbar
import com.debanshu.xcalendar.ui.components.dialog.AddEventDialog
import com.debanshu.xcalendar.ui.components.dialog.EventDetailsDialog
import com.debanshu.xcalendar.ui.navigation.NavigableScreen
import com.debanshu.xcalendar.ui.navigation.NavigationHost
import com.debanshu.xcalendar.ui.navigation.replaceLast
import com.debanshu.xcalendar.ui.state.DateStateHolder
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.debanshu.xcalendar.ui.viewmodel.EventViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val config =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(NavigableScreen.Schedule::class, NavigableScreen.Schedule.serializer())
                    subclass(NavigableScreen.Day::class, NavigableScreen.Day.serializer())
                    subclass(NavigableScreen.ThreeDay::class, NavigableScreen.ThreeDay.serializer())
                    subclass(NavigableScreen.Week::class, NavigableScreen.Week.serializer())
                    subclass(NavigableScreen.Month::class, NavigableScreen.Month.serializer())
                }
            }
    }

@Composable
fun CalendarApp() {
    val calendarViewModel = koinViewModel<CalendarViewModel>()
    val eventViewModel = koinViewModel<EventViewModel>()
    val dateStateHolder = koinInject<DateStateHolder>()
    XCalendarTheme {
        CalendarApp(
            calendarViewModel = calendarViewModel,
            eventViewModel = eventViewModel,
            dateStateHolder = dateStateHolder,
        )
    }
}

@Composable
private fun CalendarApp(
    calendarViewModel: CalendarViewModel,
    eventViewModel: EventViewModel,
    dateStateHolder: DateStateHolder,
) {
    val calendarUiState by calendarViewModel.uiState.collectAsState()
    val eventUiState by eventViewModel.uiState.collectAsState()
    val dataState by dateStateHolder.currentDateState.collectAsState()
    val backStack = rememberNavBackStack(config, NavigableScreen.Month)
    var showAddBottomSheet by remember { mutableStateOf(false) }

    // Use EventViewModel as single source of truth for selected event
    // The details sheet visibility is derived from whether an event is selected
    val selectedEvent = eventUiState.selectedEvent
    val showDetailsBottomSheet = selectedEvent != null

    val visibleCalendars by remember(calendarUiState.calendars) {
        derivedStateOf { calendarUiState.calendars.filter { it.isVisible } }
    }
    val events = remember(calendarUiState.events) { calendarUiState.events }
    val holidays = remember(calendarUiState.holidays) { calendarUiState.holidays }

    // Combine error messages from both ViewModels
    val displayError = calendarUiState.displayError ?: eventUiState.errorMessage

    Scaffold(
        containerColor = XCalendarTheme.colorScheme.surfaceContainerLow,
        topBar = {
            CalendarTopAppBar(
                dateState = dataState,
                onSelectToday = {
                    dateStateHolder.updateSelectedDateState(dataState.currentDate)
                },
                onDayClick = { date ->
                    dateStateHolder.updateSelectedDateState(date)
                    backStack.add(NavigableScreen.Day)
                },
                events = events,
                holidays = holidays,
            )
        },
        snackbarHost = {
            ErrorSnackbar(
                message = displayError,
                onDismiss = {
                    calendarViewModel.clearError()
                    eventViewModel.clearError()
                },
            )
        },
    ) { paddingValues ->
        Box {
            NavigationHost(
                modifier =
                    Modifier.padding(
                        top = paddingValues.calculateTopPadding(),
                        start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                        end = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
                    ),
                backStack = backStack,
                dateStateHolder = dateStateHolder,
                events = events,
                holidays = holidays,
                onEventClick = { event ->
                    eventViewModel.selectEvent(event)
                },
            )
            CalendarBottomNavigationBar(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = paddingValues.calculateBottomPadding()),
                selectedView = backStack.lastOrNull() as? NavigableScreen ?: NavigableScreen.Month,
                onViewSelect = { view ->
                    backStack.replaceLast(view)
                },
                onAddClick = { showAddBottomSheet = true },
            )
        }
        if (showAddBottomSheet) {
            calendarUiState.accounts.firstOrNull()?.let {
                AddEventDialog(
                    user = it,
                    calendars = visibleCalendars.toImmutableList(),
                    selectedDate = dataState.currentDate,
                    onSave = { event ->
                        eventViewModel.addEvent(event)
                        showAddBottomSheet = false
                    },
                    onDismiss = {
                        showAddBottomSheet = false
                    },
                )
            }
        }

        if (showDetailsBottomSheet && selectedEvent != null) {
            EventDetailsDialog(
                event = selectedEvent,
                onEdit = { editedEvent ->
                    eventViewModel.editEvent(editedEvent)
                },
                onDismiss = {
                    eventViewModel.clearSelectedEvent()
                },
            )
        }
    }
}
