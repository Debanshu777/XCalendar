package com.debanshu.xcalendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.debanshu.xcalendar.di.UserSessionScopeHolder
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
    val userSessionScopeHolder = koinInject<UserSessionScopeHolder>()
    val calendarViewModel = koinViewModel<CalendarViewModel>(scope = userSessionScopeHolder.scope)
    val eventViewModel = koinViewModel<EventViewModel>(scope = userSessionScopeHolder.scope)
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
    val accounts by calendarViewModel.accounts.collectAsStateWithLifecycle()
    val calendars by calendarViewModel.calendars.collectAsStateWithLifecycle()
    val events by calendarViewModel.events.collectAsStateWithLifecycle()
    val holidays by calendarViewModel.holidays.collectAsStateWithLifecycle()
    val eventsByDate by calendarViewModel.eventsByDate.collectAsStateWithLifecycle()
    val holidaysByDate by calendarViewModel.holidaysByDate.collectAsStateWithLifecycle()
    val calendarError by calendarViewModel.calendarError.collectAsStateWithLifecycle()
    val isLoading by calendarViewModel.isLoading.collectAsStateWithLifecycle()
    val eventUiState by eventViewModel.uiState.collectAsStateWithLifecycle()
    val dataState by dateStateHolder.currentDateState.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(config, NavigableScreen.Month)
    var showAddBottomSheet by remember { mutableStateOf(false) }

    // Use EventViewModel as single source of truth for selected event
    // The details sheet visibility is derived from whether an event is selected
    val selectedEvent = eventUiState.selectedEvent

    val visibleCalendars by remember(calendars) {
        derivedStateOf { calendars.filter { it.isVisible } }
    }

    // Combine error messages from both ViewModels
    val displayError = calendarError?.message ?: eventUiState.errorMessage

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
                eventsByDate = eventsByDate,
                holidaysByDate = holidaysByDate,
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
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                NavigationHost(
                    modifier =
                        Modifier.padding(
                            top = paddingValues.calculateTopPadding(),
                            start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                            end = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
                        ),
                    backStack = backStack,
                    dateStateHolder = dateStateHolder,
                    eventsByDate = eventsByDate,
                    holidaysByDate = holidaysByDate,
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
        }
        if (showAddBottomSheet) {
            accounts.firstOrNull()?.let {
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

        if (selectedEvent != null) {
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
