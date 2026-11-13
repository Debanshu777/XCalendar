package com.debanshu.xcalendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.debanshu.xcalendar.data.google.rememberGoogleAuthFactory
import com.debanshu.xcalendar.ui.google.GoogleCalendarViewModel
import com.debanshu.xcalendar.ui.google.GoogleAccountDialog
import com.debanshu.xcalendar.ui.google.GoogleEditorAction
import com.debanshu.xcalendar.ui.google.GoogleSessionStatus
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private sealed interface EventEditor {
    data object Create : EventEditor
    data class Edit(val event: Event) : EventEditor
}

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
    val googleViewModel = koinViewModel<GoogleCalendarViewModel>()
    val googleAuth = rememberGoogleAuthFactory()
    val googleSnapshots by googleViewModel.snapshots.collectAsStateWithLifecycle()
    val hiddenGoogleCalendars by googleViewModel.hiddenCalendars.collectAsStateWithLifecycle()
    val googleSyncErrors by googleViewModel.syncErrors.collectAsStateWithLifecycle()
    val googleReauthRequired by googleViewModel.reauthRequiredAccounts.collectAsStateWithLifecycle()
    val googleContent by googleViewModel.content.collectAsStateWithLifecycle()
    val googleState by googleViewModel.state.collectAsStateWithLifecycle()
    val googleEditor by googleViewModel.editorState.collectAsStateWithLifecycle()
    var showAccount by remember { mutableStateOf(false) }
    val calendarUiState by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val eventUiState by eventViewModel.uiState.collectAsStateWithLifecycle()
    val dataState by dateStateHolder.currentDateState.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(config, NavigableScreen.Month)
    var scrollScheduleToToday by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EventEditor?>(null) }
    val editingEvent = (editor as? EventEditor.Edit)?.event
    val editorOpen = editor != null

    // Use EventViewModel as single source of truth for selected event
    // The details sheet visibility is derived from whether an event is selected
    val selectedEvent = eventUiState.selectedEvent

    val connected = googleSnapshots.isNotEmpty()
    val googleAccounts = googleContent?.accounts.orEmpty()
    val canEditEvents = googleState.sessionStatus == GoogleSessionStatus.Ready &&
        googleAccounts.map { it.id } == googleSnapshots.map { it.profile.asUser().id }
    val accounts = if (connected) googleAccounts else calendarUiState.accounts
    val calendars = if (connected) googleContent?.calendars.orEmpty() else calendarUiState.calendars
    val writableCalendars = if (connected) googleContent?.writableCalendars.orEmpty() else calendars
    val events = if (connected) googleContent?.events ?: persistentListOf() else calendarUiState.events
    LaunchedEffect(dataState.selectedInViewMonth) {
        googleViewModel.selectMonth(dataState.selectedInViewMonth.year, dataState.selectedInViewMonth.month.ordinal + 1)
    }
    LaunchedEffect(googleAuth) { googleViewModel.restore(googleAuth) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, dateStateHolder) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            dateStateHolder.trackToday()
        }
    }
    LaunchedEffect(connected, editorOpen, selectedEvent, lifecycleOwner) {
        if (connected && !editorOpen && selectedEvent == null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) { delay(60_000); googleViewModel.refreshIfNeeded() }
            }
        }
    }
    val holidays = remember(calendarUiState.holidays) { calendarUiState.holidays }

    // Editor failures belong to the form until retry/cancel. A snackbar behind
    // the modal must not clear that error on its automatic dismissal timer.
    val calendarError = if (connected) null else calendarUiState.displayError
    val eventSnackbarError = eventUiState.errorMessage.takeUnless { editorOpen }
    val googleError = googleState.error.takeUnless { showAccount || editorOpen }
    val displayError = googleError ?: calendarError ?: eventSnackbarError

    Scaffold(
        containerColor = XCalendarTheme.colorScheme.surfaceContainerLow,
        topBar = {
            Column {
                CalendarTopAppBar(
                    dateState = dataState,
                    onSelectToday = {
                        dateStateHolder.resetToToday()
                        if (backStack.lastOrNull() == NavigableScreen.Schedule) scrollScheduleToToday = true
                    },
                    onDayClick = { date ->
                        dateStateHolder.updateSelectedDateState(date)
                        backStack.add(NavigableScreen.Day)
                    },
                    events = events,
                    holidays = holidays,
                    accountLabel = when {
                        googleState.sessionStatus == GoogleSessionStatus.Failed -> "Retry"
                        googleState.sessionStatus == GoogleSessionStatus.Pending -> "Accounts"
                        !connected -> "Connect"
                        accounts.isEmpty() -> "Google"
                        accounts.size > 1 -> "${accounts.size} accounts"
                        else -> accounts.first().name.substringBefore(" ").ifBlank { "Google" }
                    },
                    onAccountClick = { showAccount = true },
                )
                // Reserve the strip even when idle so loading never moves the grid.
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (googleState.busy && !googleState.saving) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = XCalendarTheme.colorScheme.primary,
                            trackColor = XCalendarTheme.colorScheme.surfaceContainerLow,
                        )
                    }
                }
            }
        },
        snackbarHost = {
            ErrorSnackbar(
                message = displayError,
                onDismiss = {
                    if (googleError != null) googleViewModel.clearError()
                    else if (calendarError != null) calendarViewModel.clearError()
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
                scrollScheduleToToday = scrollScheduleToToday,
                onScheduleTodayScrollRequested = { scrollScheduleToToday = false },
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
                onAddClick = if (canEditEvents && accounts.isNotEmpty()) ({
                    eventViewModel.clearSelectedEvent()
                    eventViewModel.clearError()
                    googleViewModel.clearEditorState()
                    editor = EventEditor.Create
                }) else null,
            )
        }
        if (showAccount) {
            GoogleAccountDialog(googleSnapshots, googleState, hiddenGoogleCalendars, googleSyncErrors, googleReauthRequired,
                onConnect = { googleViewModel.connect(googleAuth) },
                onRefresh = googleViewModel::refresh,
                onSignOut = { accountId -> googleViewModel.signOut(accountId) { eventViewModel.clearSelectedEvent() } },
                onReconnect = { accountId -> googleViewModel.connect(googleAuth, accountId) },
                onRestore = { googleViewModel.restore(googleAuth) },
                onCalendarVisibilityChange = googleViewModel::setCalendarVisible,
                onDismiss = { googleViewModel.cancel(); showAccount = false },
            )
        }
        if (editorOpen && canEditEvents) {
            val calendarOwner = calendars.find { it.id == editingEvent?.calendarId }?.userId
            (accounts.find { it.id == calendarOwner }
                ?: accounts.firstOrNull())?.let {
                AddEventDialog(
                    user = it,
                    calendarAccounts = (if (connected) accounts else listOf(it)).toImmutableList(),
                    calendars = writableCalendars.filter { !connected || editingEvent == null || it.id == editingEvent.calendarId }.toImmutableList(),
                    selectedDate = dataState.selectedDate,
                    initialEvent = editingEvent,
                    isSaving = if (connected) googleEditor.action != null else eventUiState.isLoading,
                    busyLabel = when (googleEditor.action) {
                        GoogleEditorAction.Reconnect -> "Reconnecting…"
                        GoogleEditorAction.LoadLatest -> "Loading…"
                        else -> "Saving…"
                    },
                    actionsAvailable = !connected || !googleState.busy,
                    errorMessage = if (connected) googleEditor.error else eventUiState.errorMessage,
                    latestEvent = googleEditor.latestEvent,
                    onLoadLatest = editingEvent?.takeIf { googleEditor.hasConflict && googleEditor.latestEvent == null }
                        ?.let { event ->
                            { googleViewModel.loadLatestEvent(event, eventViewModel::selectEvent) }
                        },
                    reconnectAccountIds = googleReauthRequired.map { "google:$it" }.toSet(),
                    onReconnect = if (connected) {
                        { userId -> googleSnapshots.firstOrNull { it.profile.asUser().id == userId }?.let {
                            googleViewModel.reconnectEditor(googleAuth, it.profile.sub)
                        } }
                    } else null,
                    onSave = { event ->
                        val dismissOnSuccess = {
                            editor = null
                        }
                        if (connected) {
                            googleViewModel.save(event, editing = editingEvent != null) { eventViewModel.clearSelectedEvent(); dismissOnSuccess() }
                        } else if (editingEvent == null) {
                            eventViewModel.addEvent(event, onSuccess = dismissOnSuccess)
                        } else {
                            eventViewModel.editEvent(event, onSuccess = dismissOnSuccess)
                        }
                    },
                    onDismiss = {
                        if (!eventUiState.isLoading && googleEditor.action == null) {
                            editor = null
                            eventViewModel.clearError()
                            googleViewModel.clearEditorState()
                        }
                    },
                )
            }
        }

        if (selectedEvent != null && !editorOpen) {
            EventDetailsDialog(
                event = selectedEvent,
                canEdit = canEditEvents && (!connected || googleViewModel.canEdit(selectedEvent)),
                onEdit = { editedEvent ->
                    eventViewModel.clearError()
                    googleViewModel.clearEditorState()
                    editor = EventEditor.Edit(editedEvent)
                },
                onDismiss = {
                    eventViewModel.clearSelectedEvent()
                },
            )
        }
    }
}
