package com.debanshu.xcalendar

import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.domain.states.dateState.DateStateHolder
import com.debanshu.xcalendar.ui.CalendarViewModel
import com.debanshu.xcalendar.ui.components.AddEventDialog
import com.debanshu.xcalendar.ui.components.CalendarDrawer
import com.debanshu.xcalendar.ui.components.CalendarTopAppBar
import com.debanshu.xcalendar.ui.components.EventDetailsDialog
import com.debanshu.xcalendar.ui.navigation.NavigableScreen
import com.debanshu.xcalendar.ui.navigation.NavigationHost
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview
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
    val calendarUiState by viewModel.uiState.collectAsState()
    val dataState by dateStateHolder.currentDateState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<NavigableScreen>(NavigableScreen.Month) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddBottomSheet by remember { mutableStateOf(false) }
    var showDetailsBottomSheet by remember { mutableStateOf(false) }

    val drawerAccounts = remember(calendarUiState.accounts) { calendarUiState.accounts }
    val drawerCalendars = remember(calendarUiState.calendars) { calendarUiState.calendars }

    val visibleCalendars by remember(calendarUiState.calendars) {
        derivedStateOf { calendarUiState.calendars.filter { it.isVisible } }
    }
    val events = remember { calendarUiState.events }
    val holidays = remember { calendarUiState.holidays }

    ModalNavigationDrawer(
        modifier = Modifier.testTag("ModalNavigationDrawer"),
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                CalendarDrawer(
                    selectedView = backStack.lastOrNull() ?: NavigableScreen.Month,
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
                    events,
                    holidays,
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddBottomSheet = true }) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = FontAwesomeIcons.Solid.Plus,
                        contentDescription = "Add Event",
                    )
                }
            },
        ) { paddingValues ->
            NavigationHost(
                modifier = Modifier.padding(paddingValues),
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
                    AddEventDialog(
                        user = calendarUiState.accounts[0],
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
