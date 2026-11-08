package com.debanshu.xcalendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import com.debanshu.xcalendar.domain.model.Event
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
    val calendarUiState by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val eventUiState by eventViewModel.uiState.collectAsStateWithLifecycle()
    val dataState by dateStateHolder.currentDateState.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(config, NavigableScreen.Month)
    var showAddBottomSheet by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }

    // Use EventViewModel as single source of truth for selected event
    // The details sheet visibility is derived from whether an event is selected
    val selectedEvent = eventUiState.selectedEvent

    val events = remember(calendarUiState.events) { calendarUiState.events }
    val holidays = remember(calendarUiState.holidays) { calendarUiState.holidays }

    // Editor failures belong to the form until retry/cancel. A snackbar behind
    // the modal must not clear that error on its automatic dismissal timer.
    val calendarError = calendarUiState.displayError
    val eventSnackbarError = eventUiState.errorMessage.takeUnless { showAddBottomSheet }
    val displayError = calendarError ?: eventSnackbarError

    Scaffold(
        containerColor = XCalendarTheme.colorScheme.surfaceContainerLow,
        topBar = {
            CalendarTopAppBar(
                dateState = dataState,
                onSelectToday = {
                    dateStateHolder.resetToToday()
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
                    if (calendarError != null) calendarViewModel.clearError()
                    else eventViewModel.clearError()
                },
            )
        },
    ) { paddingValues ->
        // Scaffold updates these insets while the dropdown changes height. Read
        // them during measurement so the navigation tree can skip composition.
        val bottomNavigationPadding = remember(paddingValues) {
            object : PaddingValues {
                override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
                override fun calculateTopPadding() = 0.dp
                override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
                override fun calculateBottomPadding() = paddingValues.calculateBottomPadding()
            }
        }
        Box {
            NavigationHost(
                modifier =
                    Modifier
                        .padding(paddingValues)
                        .padding(bottom = 72.dp),
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
                        .padding(bottomNavigationPadding),
                selectedView = backStack.lastOrNull() as? NavigableScreen ?: NavigableScreen.Month,
                onViewSelect = { view ->
                    backStack.replaceLast(view)
                },
                onAddClick = {
                    editingEvent = null
                    eventViewModel.clearSelectedEvent()
                    eventViewModel.clearError()
                    showAddBottomSheet = true
                },
            )
        }
        if (showAddBottomSheet) {
            val calendarOwner = calendarUiState.calendars.find { it.id == editingEvent?.calendarId }?.userId
            (calendarUiState.accounts.find { it.id == calendarOwner }
                ?: calendarUiState.accounts.firstOrNull())?.let {
                AddEventDialog(
                    user = it,
                    calendars = calendarUiState.calendars.toImmutableList(),
                    selectedDate = dataState.selectedDate,
                    initialEvent = editingEvent,
                    isSaving = eventUiState.isLoading,
                    errorMessage = eventUiState.errorMessage,
                    onSave = { event ->
                        val dismissOnSuccess = {
                            showAddBottomSheet = false
                            editingEvent = null
                        }
                        if (editingEvent == null) {
                            eventViewModel.addEvent(event, onSuccess = dismissOnSuccess)
                        } else {
                            eventViewModel.editEvent(event, onSuccess = dismissOnSuccess)
                        }
                    },
                    onDismiss = {
                        if (!eventUiState.isLoading) {
                            showAddBottomSheet = false
                            editingEvent = null
                            eventViewModel.clearError()
                        }
                    },
                )
            }
        }

        if (selectedEvent != null && !showAddBottomSheet) {
            EventDetailsDialog(
                event = selectedEvent,
                onEdit = { editedEvent ->
                    editingEvent = editedEvent
                    eventViewModel.clearError()
                    showAddBottomSheet = true
                },
                onDismiss = {
                    eventViewModel.clearSelectedEvent()
                },
            )
        }
    }
}
