package com.debanshu.xcalendar

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import com.debanshu.xcalendar.domain.states.dateState.DateStateHolder
import com.debanshu.xcalendar.ui.CalendarViewModel
import com.debanshu.xcalendar.ui.components.AddEventDialog
import com.debanshu.xcalendar.ui.components.CalendarDrawer
import com.debanshu.xcalendar.ui.components.CalendarTopAppBar
import com.debanshu.xcalendar.ui.components.EventDetailsDialog
import com.debanshu.xcalendar.ui.navigation.NavigableScreen
import com.debanshu.xcalendar.ui.navigation.NavigationHost
import com.debanshu.xcalendar.ui.theme.LocalSharedTransitionScope
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
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
    val viewModel = koinViewModel<CalendarViewModel>()
    val dateStateHolder = koinInject<DateStateHolder>()
    XCalendarTheme {
        CalendarApp(viewModel, dateStateHolder)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CalendarApp(
    viewModel: CalendarViewModel,
    dateStateHolder: DateStateHolder,
) {
    val sharedElementScope = LocalSharedTransitionScope.current
    val calendarUiState by viewModel.uiState.collectAsState()
    val dataState by dateStateHolder.currentDateState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(config, NavigableScreen.Month)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddBottomSheet by remember { mutableStateOf(false) }
    var showDetailsBottomSheet by remember { mutableStateOf(false) }

    val drawerAccounts = remember(calendarUiState.accounts) { calendarUiState.accounts }
    val drawerCalendars = remember(calendarUiState.calendars) { calendarUiState.calendars }

    val visibleCalendars by remember(calendarUiState.calendars) {
        derivedStateOf { calendarUiState.calendars.filter { it.isVisible } }
    }
    val events = remember(calendarUiState.events) { calendarUiState.events }
    val holidays = remember(calendarUiState.holidays) { calendarUiState.holidays }

    with(sharedElementScope) {
        ModalNavigationDrawer(
            modifier =
                Modifier.skipToLookaheadSize(),
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    CalendarDrawer(
                        selectedView = backStack.lastOrNull() as NavigableScreen,
                        onViewSelect = { view ->
                            scope.launch {
                                backStack.add(view)
                                drawerState.close()
                            }
                        },
                        accounts = drawerAccounts,
                        calendars = drawerCalendars,
                        onCalendarToggle = { calendar ->
                            viewModel.toggleCalendarVisibility(calendar)
                        },
                    )
                }
            },
        ) {
            Scaffold(
                containerColor = XCalendarTheme.colorScheme.surfaceContainerLow,
                topBar = {
                    CalendarTopAppBar(
                        dateState = dataState,
                        onMenuClick = { scope.launch { drawerState.open() } },
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
                floatingActionButton = {
                    FloatingActionButton(
                        modifier =
                            Modifier.renderInSharedTransitionScopeOverlay(
                                1f,
                            ),
                        onClick = { showAddBottomSheet = true },
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = FontAwesomeIcons.Solid.Plus,
                            contentDescription = "Add Event",
                        )
                    }
                },
            ) { paddingValues ->
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
                        viewModel.selectEvent(event)
                        showDetailsBottomSheet = true
                    },
                )
                if (showAddBottomSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showAddBottomSheet = false },
                        sheetState = sheetState,
                        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = true),
                    ) {
                        calendarUiState.accounts.firstOrNull()?.let {
                            AddEventDialog(
                                user = it,
                                calendars = visibleCalendars.toImmutableList(),
                                selectedDate = dataState.currentDate,
                                onSave = { event ->
                                    viewModel.addEvent(event)
                                    showAddBottomSheet = false
                                },
                                onDismiss = {
                                    showAddBottomSheet = false
                                },
                            )
                        }
                    }
                }

                if (showDetailsBottomSheet) {
                    calendarUiState.selectedEvent?.let { event ->
                        ModalBottomSheet(
                            onDismissRequest = { showDetailsBottomSheet = false },
                            sheetState = sheetState,
                            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = true),
                        ) {
                            EventDetailsDialog(
                                event = event,
                                onEdit = {
                                    viewModel.editEvent(it)
                                    viewModel.clearSelectedEvent()
                                    showDetailsBottomSheet = false
                                },
                                onDismiss = {
                                    viewModel.clearSelectedEvent()
                                    showDetailsBottomSheet = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
