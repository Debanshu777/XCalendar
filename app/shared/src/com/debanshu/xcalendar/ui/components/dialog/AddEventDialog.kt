@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.debanshu.xcalendar.ui.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.User
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.debanshu.xcalendar.ui.utils.DateTimeFormatter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Event editor; the owner dismisses only after persistence succeeds. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun AddEventDialog(
    user: User,
    calendars: ImmutableList<Calendar>,
    selectedDate: LocalDate,
    onSave: (Event) -> Unit = {},
    onDismiss: () -> Unit = {},
    initialEvent: Event? = null,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onReconnect: ((String) -> Unit)? = null,
    reconnectAccountIds: Set<String> = emptySet(),
    calendarAccounts: ImmutableList<User> = kotlinx.collections.immutable.persistentListOf(user),
    busyLabel: String = "Saving…",
    actionsAvailable: Boolean = true,
    latestEvent: Event? = null,
    onLoadLatest: (() -> Unit)? = null,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val initialStart = remember(initialEvent?.id, selectedDate, timeZone) {
        initialEvent?.let { Instant.fromEpochMilliseconds(it.startTime).toLocalDateTime(timeZone) }
            ?: LocalDateTime(selectedDate, LocalTime(12, 0))
    }
    val initialEnd = remember(initialEvent?.id, selectedDate, timeZone) {
        initialEvent?.let {
            // All-day end instants are exclusive; present the final included date.
            val end = if (it.isAllDay && it.endTime > it.startTime) it.endTime - 1 else it.endTime
            Instant.fromEpochMilliseconds(end).toLocalDateTime(timeZone)
        } ?: LocalDateTime(selectedDate, LocalTime(12, 30))
    }
    var title by rememberSaveable(initialEvent?.id) { mutableStateOf(initialEvent?.title.orEmpty()) }
    var description by rememberSaveable(initialEvent?.id) { mutableStateOf(initialEvent?.description.orEmpty()) }
    var location by rememberSaveable(initialEvent?.id) { mutableStateOf(initialEvent?.location.orEmpty()) }
    var selectedCalendarId by rememberSaveable(initialEvent?.id) {
        mutableStateOf(initialEvent?.calendarId ?: calendars.firstOrNull { it.userId == user.id }?.id.orEmpty())
    }
    var isAllDay by rememberSaveable(initialEvent?.id) { mutableStateOf(initialEvent?.isAllDay ?: false) }
    var startDate by rememberSaveable(initialEvent?.id) { mutableStateOf(initialStart.date.toString()) }
    var endDate by rememberSaveable(initialEvent?.id) { mutableStateOf(initialEnd.date.toString()) }
    var startTime by rememberSaveable(initialEvent?.id) { mutableStateOf(editorTime(initialStart)) }
    var endTime by rememberSaveable(initialEvent?.id) { mutableStateOf(editorTime(initialEnd)) }
    var reminders by rememberSaveable(initialEvent?.id) { mutableStateOf(initialEvent?.reminderMinutes ?: listOf(10)) }
    val eventId = rememberSaveable(initialEvent?.id) { initialEvent?.id ?: Uuid.random().toString() }
    val scrollState = rememberScrollState()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) scrollState.animateScrollTo(0)
    }
    val currentIsSaving by rememberUpdatedState(isSaving)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !currentIsSaving },
    )

    // A hidden calendar may still own the edited event. Preserve that association.
    val selectableCalendars = remember(calendars, initialEvent, user.id, calendarAccounts) {
        val accountIds = calendarAccounts.map { it.id }.toSet()
        val ownedCalendars = calendars.filter { it.userId in accountIds }
        if (initialEvent != null && ownedCalendars.none { it.id == initialEvent.calendarId }) {
            (ownedCalendars + Calendar(initialEvent.calendarId, initialEvent.calendarName, initialEvent.color, user.id)).toImmutableList()
        } else ownedCalendars.toImmutableList()
    }
    LaunchedEffect(selectableCalendars, selectedCalendarId) {
        if (selectableCalendars.none { it.id == selectedCalendarId }) {
            selectedCalendarId = selectableCalendars.firstOrNull()?.id.orEmpty()
        }
    }
    val selectedCalendar = selectableCalendars.find { it.id == selectedCalendarId }
    val interval = remember(startDate, endDate, startTime, endTime, isAllDay, timeZone, initialEvent) {
        resolveEventInterval(startDate, endDate, startTime, endTime, isAllDay, timeZone, initialEvent)
    }
    val validationError = when {
        title.length > 200 -> "Title cannot exceed 200 characters."
        selectedCalendar == null -> "No calendar is available. Wait for calendars to load before saving."
        interval == null -> "Use dates as YYYY-MM-DD and times as HH:mm; the end must follow the start."
        else -> null
    }
    val canSave = title.isNotBlank() && validationError == null && !isSaving &&
        actionsAvailable && onLoadLatest == null

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !isSaving,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !isSaving,
            shouldDismissOnClickOutside = !isSaving,
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
                TextButton(
                    enabled = canSave,
                    onClick = save@{
                        val calendar = selectedCalendar ?: return@save
                        val times = interval ?: return@save
                        val event = Event(
                            id = eventId,
                            calendarId = calendar.id,
                            calendarName = calendar.name,
                            title = title.trim(),
                            description = description.takeIf { it.isNotBlank() },
                            location = location.takeIf { it.isNotBlank() },
                            startTime = times.first,
                            endTime = times.second,
                            isAllDay = isAllDay,
                            isRecurring = initialEvent?.isRecurring ?: false,
                            recurringRule = initialEvent?.recurringRule,
                            reminderMinutes = reminders,
                            color = calendar.color,
                            remoteEtag = initialEvent?.remoteEtag,
                        )
                        onSave(event)
                    },
                ) {
                    Text(when {
                        isSaving -> busyLabel
                        latestEvent != null -> "Save draft"
                        else -> "Save"
                    })
                }
            }
            HorizontalDivider()
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(scrollState).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val visibleError = errorMessage ?: validationError
                if (visibleError != null) {
                    Text(visibleError, color = XCalendarTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (onReconnect != null && selectedCalendar?.userId in reconnectAccountIds) {
                    TextButton(
                        onClick = { selectedCalendar?.let { onReconnect(it.userId) } },
                        enabled = actionsAvailable && !isSaving,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Reconnect Google account") }
                }
                if (onLoadLatest != null) {
                    TextButton(
                        onClick = onLoadLatest,
                        enabled = actionsAvailable && !isSaving,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Load latest version") }
                }
                if (latestEvent != null) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Current version on Google Calendar", style = XCalendarTheme.typography.titleSmall)
                        Text(latestEvent.title)
                        Text(DateTimeFormatter.formatEventSubheading(latestEvent))
                        latestEvent.location?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        latestEvent.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        Text(if (latestEvent.reminderMinutes.isEmpty()) "No reminder" else
                            latestEvent.reminderMinutes.joinToString { "$it min before" })
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("Add title") },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = title.length > 200,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                CalendarTimeSection(
                    isAllDay = isAllDay,
                    startDate = startDate,
                    endDate = endDate,
                    startTime = startTime,
                    endTime = endTime,
                    onAllDayChange = { isAllDay = it },
                    onStartDateChange = { startDate = it },
                    onEndDateChange = { endDate = it },
                    onStartTimeChange = { startTime = it },
                    onEndTimeChange = { endTime = it },
                    enabled = !isSaving,
                )
                HorizontalDivider()
                calendarAccounts.forEach { account ->
                    val accountCalendars = selectableCalendars.filter { it.userId == account.id }.toImmutableList()
                    if (accountCalendars.isNotEmpty()) {
                        CalendarSelectionSection(
                            user = account,
                            calendars = accountCalendars,
                            selectedCalendarId = selectedCalendarId,
                            onCalendarSelected = { selectedCalendarId = it },
                            enabled = !isSaving,
                        )
                    }
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                NotificationRow(
                    reminderMinutes = reminders,
                    onReminderChange = { reminders = it },
                    enabled = !isSaving,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
    }
}

private fun editorTime(value: LocalDateTime): String =
    "${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"

/** Preserve sub-minute precision and DST-fold offsets when date/time inputs are unchanged. */
internal fun resolveEventInterval(
    startDate: String,
    endDate: String,
    startTime: String,
    endTime: String,
    isAllDay: Boolean,
    timeZone: TimeZone,
    initialEvent: Event? = null,
): Pair<Long, Long>? {
    if (initialEvent != null && !isAllDay && !initialEvent.isAllDay && initialEvent.endTime > initialEvent.startTime) {
        val originalStart = Instant.fromEpochMilliseconds(initialEvent.startTime).toLocalDateTime(timeZone)
        val originalEnd = Instant.fromEpochMilliseconds(initialEvent.endTime).toLocalDateTime(timeZone)
        if (startDate == originalStart.date.toString() && endDate == originalEnd.date.toString() &&
            startTime == editorTime(originalStart) && endTime == editorTime(originalEnd)
        ) return initialEvent.startTime to initialEvent.endTime
    }
    return parseEventInterval(startDate, endDate, startTime, endTime, isAllDay, timeZone)
}

/** Returns half-open event bounds; all-day input dates are inclusive calendar dates. */
internal fun parseEventInterval(
    startDate: String,
    endDate: String,
    startTime: String,
    endTime: String,
    isAllDay: Boolean,
    timeZone: TimeZone,
): Pair<Long, Long>? = runCatching {
    val firstDate = LocalDate.parse(startDate)
    val lastDate = LocalDate.parse(endDate)
    val start = if (isAllDay) firstDate.atStartOfDayIn(timeZone) else {
        val local = LocalDateTime(firstDate, LocalTime.parse(startTime))
        local.toInstant(timeZone).also { if (it.toLocalDateTime(timeZone) != local) return@runCatching null }
    }
    val end = if (isAllDay) lastDate.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone) else {
        val local = LocalDateTime(lastDate, LocalTime.parse(endTime))
        local.toInstant(timeZone).also { if (it.toLocalDateTime(timeZone) != local) return@runCatching null }
    }
    if (end <= start) null else start.toEpochMilliseconds() to end.toEpochMilliseconds()
}.getOrNull()
