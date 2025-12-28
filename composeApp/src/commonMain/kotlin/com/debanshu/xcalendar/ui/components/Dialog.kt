package com.debanshu.xcalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.common.convertStringToColor
import com.debanshu.xcalendar.common.noRippleClickable
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.User
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.skydoves.landscapist.coil3.CoilImage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import xcalendar.composeapp.generated.resources.Res
import xcalendar.composeapp.generated.resources.ic_clock
import xcalendar.composeapp.generated.resources.ic_close
import xcalendar.composeapp.generated.resources.ic_description
import xcalendar.composeapp.generated.resources.ic_edit
import xcalendar.composeapp.generated.resources.ic_location
import xcalendar.composeapp.generated.resources.ic_notifications
import xcalendar.composeapp.generated.resources.ic_unfold_more
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
internal fun AddEventDialog(
    user: User,
    calendars: ImmutableList<Calendar>,
    selectedDate: LocalDate,
    onSave: (Event) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedCalendarId by remember { mutableStateOf(calendars.firstOrNull()?.id ?: "") }
    var isAllDay by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    var selectedEventType by remember { mutableStateOf(EventType.EVENT) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var startDateTime by remember {
        mutableStateOf(
            LocalDateTime(
                selectedDate.year,
                selectedDate.month,
                selectedDate.day,
                12,
                0,
            ),
        )
    }
    var endDateTime by remember {
        mutableStateOf(
            LocalDateTime(
                selectedDate.year,
                selectedDate.month,
                selectedDate.day,
                12,
                30,
            ),
        )
    }
    var showMoreOptions by remember { mutableStateOf(false) }
    var reminderMinutes by remember { mutableStateOf(10) }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Cancel",
                    modifier =
                        Modifier.clickable {
                            onDismiss()
                        },
                    color = XCalendarTheme.colorScheme.primary,
                )
                Text(
                    "Save",
                    style = XCalendarTheme.typography.bodyLarge,
                    modifier =
                        Modifier.clickable {
                            if (title.isNotBlank()) {
                                val selectedCalendar =
                                    calendars.find { it.id == selectedCalendarId }
                                val event =
                                    Event(
                                        id = Uuid.random().toString(),
                                        calendarId = selectedCalendarId,
                                        calendarName = selectedCalendar?.name ?: "",
                                        title = title,
                                        description = description.takeIf { it.isNotBlank() },
                                        location = location.takeIf { it.isNotBlank() },
                                        startTime =
                                            if (isAllDay) {
                                                LocalDateTime(
                                                    selectedDate.year,
                                                    selectedDate.month,
                                                    selectedDate.day,
                                                    0,
                                                    0,
                                                ).toInstant(TimeZone.currentSystemDefault())
                                                    .toEpochMilliseconds()
                                            } else {
                                                startDateTime
                                                    .toInstant(TimeZone.currentSystemDefault())
                                                    .toEpochMilliseconds()
                                            },
                                        endTime =
                                            if (isAllDay) {
                                                LocalDateTime(
                                                    selectedDate.year,
                                                    selectedDate.month,
                                                    selectedDate.day,
                                                    23,
                                                    59,
                                                ).toInstant(TimeZone.currentSystemDefault())
                                                    .toEpochMilliseconds()
                                            } else {
                                                endDateTime
                                                    .toInstant(TimeZone.currentSystemDefault())
                                                    .toEpochMilliseconds()
                                            },
                                        isAllDay = isAllDay,
                                        reminderMinutes =
                                            if (reminderMinutes > 0) {
                                                listOf(
                                                    reminderMinutes,
                                                )
                                            } else {
                                                emptyList()
                                            },
                                        color =
                                            selectedCalendar?.color
                                                ?: convertStringToColor("defaultColor", 255),
                                    )
                                onSave(event)
                            }
                        },
                    color = XCalendarTheme.colorScheme.primary,
                )
            }

            TextField(
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                value = title,
                onValueChange = { title = it },
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                interactionSource = interactionSource,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                placeholder = {
                    Text(
                        text = "Add title",
                        color = XCalendarTheme.colorScheme.onSurface,
                        style = XCalendarTheme.typography.headlineSmall,
                    )
                },
            )

            EventTypeSelector(
                selectedType = selectedEventType,
                onTypeSelected = { selectedEventType = it },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 1.dp)

            CaladerTimeSection(
                isAllDayBase = isAllDay,
                selectedDate = selectedDate,
                startDateTime = startDateTime,
                endDateTime = endDateTime,
                onSwitchAllDayChange = {
                    isAllDay = it
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 1.dp)

            CalenderSelectionSection(
                user,
                calendars,
                selectedCalendarId,
                onCalendarSelected =
                    { selectedCalendarId = it },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 1.dp)

            AddEventOption(
                icon = Res.drawable.ic_location,
                text = "Add location",
                onClick = {
                    showMoreOptions = !showMoreOptions
                },
            )

            if (showMoreOptions) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    placeholder = { Text("Enter location") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 1.dp)
            NotificationRow(
                reminderMinutes = reminderMinutes,
                onReminderChange = { reminderMinutes = it },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 1.dp)
            AddEventOption(
                icon = Res.drawable.ic_description,
                text = "Add description",
                onClick = { /* Handle add description */ },
            )
        }
    }
}

@Composable
private fun CalenderSelectionSection(
    user: User,
    calendars: ImmutableList<Calendar>,
    incomingSelectedCalendarId: String,
    onCalendarSelected: (String) -> Unit,
) {
    var selectedCalendarId by remember { mutableStateOf(incomingSelectedCalendarId) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = 8.dp,
                        start = 16.dp,
                    ),
        ) {
            CoilImage(
                imageModel = { user.photoUrl },
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = user.email,
                style = XCalendarTheme.typography.bodySmall,
                color = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        onCalenderList(
            calendars,
            selectedCalendarId,
            onCalendarSelected = {
                selectedCalendarId = it
                onCalendarSelected(it)
            },
        )
    }
}

@Composable
private fun CaladerTimeSection(
    isAllDayBase: Boolean,
    selectedDate: LocalDate,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
    onSwitchAllDayChange: (Boolean) -> Unit,
) {
    var isAllDay by remember { mutableStateOf(isAllDayBase) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth().padding(
                horizontal = 16.dp,
            ),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_clock),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "All day",
            style = XCalendarTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.weight(1f))

        Switch(checked = isAllDay, onCheckedChange = {
            isAllDay = !isAllDay
            onSwitchAllDayChange(isAllDay)
        })
    }

    // Date and time selection
    if (isAllDay) {
        TimeDisplayRow(
            label = "${
                selectedDate.month.name.lowercase().replaceFirstChar {
                    it.titlecase()
                }
            } ${selectedDate.day}, ${selectedDate.year}",
            onClick = { /* Handle date picker */ },
        )
    } else {
        TimeDisplayRow(
            label = "${
                selectedDate.month.name.lowercase().replaceFirstChar {
                    it.titlecase()
                }
            } ${selectedDate.day}, ${selectedDate.year}",
            startTime = formatTime(startDateTime),
            endTime = formatTime(endDateTime),
            onClick = { /* Handle time picker */ },
        )
    }
}

@Composable
private fun onCalenderList(
    calendars: ImmutableList<Calendar>,
    selectedCalendarId: String,
    onCalendarSelected: (String) -> Unit = {},
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(calendars) { index, calendar ->
            Row(
                modifier =
                    Modifier
                        .padding(
                            start = if (index == 0) 50.dp else 0.dp,
                            end = if (index == calendars.size - 1) 16.dp else 0.dp,
                        ).border(
                            0.5.dp,
                            XCalendarTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        ).background(
                            color =
                                if (selectedCalendarId != calendar.id) {
                                    XCalendarTheme.colorScheme.surfaceContainerLow
                                } else {
                                    XCalendarTheme.colorScheme.primary
                                },
                            RoundedCornerShape(8.dp),
                        ).padding(8.dp)
                        .noRippleClickable {
                            onCalendarSelected(calendar.id)
                        },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(calendar.color)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = calendar.name,
                    style = XCalendarTheme.typography.bodySmall,
                    color =
                        if (selectedCalendarId != calendar.id) {
                            XCalendarTheme.colorScheme.onSurfaceVariant
                        } else {
                            XCalendarTheme.colorScheme.onPrimary
                        },
                )
            }
        }
    }
}

enum class EventType(
    val displayName: String,
) {
    EVENT("Event"),
    TASK("Task"),
    WORKING_LOCATION("Working location"),
    OUT_OF_OFFICE("Out of office"),
}

@Composable
private fun EventTypeSelector(
    selectedType: EventType,
    onTypeSelected: (EventType) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(EventType.entries.toTypedArray()) { index, type ->
            val isSelected = selectedType == type
            Box(
                modifier =
                    Modifier
                        .padding(
                            start = if (index == 0) 54.dp else 0.dp,
                            end =
                                if (index ==
                                    EventType.entries.size - 1
                                ) {
                                    16.dp
                                } else {
                                    0.dp
                                },
                        ).clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) {
                                XCalendarTheme.colorScheme.primary
                            } else {
                                XCalendarTheme.colorScheme.surfaceVariant
                            },
                        ).clickable { onTypeSelected(type) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = type.displayName,
                    color =
                        if (isSelected) {
                            XCalendarTheme.colorScheme.onPrimary
                        } else {
                            XCalendarTheme.colorScheme.onSurfaceVariant
                        },
                    style = XCalendarTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TimeDisplayRow(
    label: String,
    startTime: String? = null,
    endTime: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(top = 8.dp, bottom = 8.dp, start = 52.dp, end = 16.dp),
    ) {
        Text(
            text = label,
            style = XCalendarTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (!startTime.isNullOrEmpty()) {
            Text(
                text = startTime,
                style = XCalendarTheme.typography.bodyMedium,
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(top = 8.dp, bottom = 8.dp, start = 52.dp, end = 16.dp),
    ) {
        Text(
            text = label,
            style = XCalendarTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (!endTime.isNullOrEmpty()) {
            Text(
                text = endTime,
                style = XCalendarTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AddEventOption(
    icon: DrawableResource,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            style = XCalendarTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NotificationRow(
    reminderMinutes: Int,
    onReminderChange: (Int) -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_notifications),
            contentDescription = null,
            tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "$reminderMinutes minutes before",
            style = XCalendarTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            painter = painterResource(Res.drawable.ic_unfold_more),
            contentDescription = null,
            tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

private fun formatTime(dateTime: LocalDateTime): String {
    val hour =
        if (dateTime.hour == 0) {
            12
        } else if (dateTime.hour > 12) {
            dateTime.hour - 12
        } else {
            dateTime.hour
        }
    val minute = dateTime.minute.toString().padStart(2, '0')
    val amPm = if (dateTime.hour >= 12) "PM" else "AM"
    return "$hour:$minute $amPm"
}

private fun formatEventSubheading(event: Event): String {
    val startDateTime = event.startTime.toLocalDateTime(TimeZone.currentSystemDefault())
    val endDateTime = event.endTime.toLocalDateTime(TimeZone.currentSystemDefault())

    // Format day of week (e.g., "Friday")
    val dayOfWeek =
        startDateTime.date.dayOfWeek.name
            .lowercase()
            .replaceFirstChar { it.titlecase() }

    // Format day and month (e.g., "20 Jun")
    val day = startDateTime.date.day
    val month =
        startDateTime.date.month.name
            .take(3)
            .lowercase()
            .replaceFirstChar { it.titlecase() }

    // Build the main date/time string
    val mainLine =
        if (event.isAllDay) {
            "$dayOfWeek, $day $month"
        } else {
            // Format time range (e.g., "6-7PM")
            val startHour =
                if (startDateTime.hour == 0) {
                    12
                } else if (startDateTime.hour > 12) {
                    startDateTime.hour - 12
                } else {
                    startDateTime.hour
                }
            val endHour =
                if (endDateTime.hour == 0) {
                    12
                } else if (endDateTime.hour > 12) {
                    endDateTime.hour - 12
                } else {
                    endDateTime.hour
                }
            val startMinute =
                if (startDateTime.minute == 0) {
                    ""
                } else {
                    ":${
                        startDateTime.minute.toString().padStart(2, '0')
                    }"
                }
            val endMinute =
                if (endDateTime.minute == 0) {
                    ""
                } else {
                    ":${
                        endDateTime.minute.toString().padStart(2, '0')
                    }"
                }

            val timeRange =
                when {
                    // Same AM/PM period
                    (startDateTime.hour < 12 && endDateTime.hour < 12) || (startDateTime.hour >= 12 && endDateTime.hour >= 12) -> {
                        val amPm = if (endDateTime.hour >= 12) "PM" else "AM"
                        "$startHour$startMinute-$endHour$endMinute$amPm"
                    }

                    // Different AM/PM periods
                    else -> {
                        val startAmPm = if (startDateTime.hour >= 12) "PM" else "AM"
                        val endAmPm = if (endDateTime.hour >= 12) "PM" else "AM"
                        "$startHour$startMinute$startAmPm-$endHour$endMinute$endAmPm"
                    }
                }

            "$dayOfWeek, $day $month $timeRange"
        }

    // Add recurring information if applicable
    return if (event.isRecurring && !event.recurringRule.isNullOrBlank()) {
        val recurringText =
            when {
                event.recurringRule.contains("WEEKLY", ignoreCase = true) -> "Repeat every week"
                event.recurringRule.contains("DAILY", ignoreCase = true) -> "Repeat every day"
                event.recurringRule.contains("MONTHLY", ignoreCase = true) -> "Repeat every month"
                event.recurringRule.contains("YEARLY", ignoreCase = true) -> "Repeat every year"
                else -> "Recurring event"
            }
        "$mainLine\n$recurringText"
    } else {
        mainLine
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsDialog(
    event: Event,
    onEdit: (Event) -> Unit,
    onDismiss: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = "Close",
                    modifier =
                        Modifier
                            .clickable { onDismiss() },
                    tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Icon(
                    painter = painterResource(Res.drawable.ic_edit),
                    contentDescription = "Edit",
                    modifier =
                        Modifier
                            .clickable { onEdit(event) },
                    tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(16.dp)
                            .height(16.dp)
                            .background(
                                Color(event.color),
                                RoundedCornerShape(2.dp),
                            ),
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = event.title,
                    style = XCalendarTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formatEventSubheading(event),
                style = XCalendarTheme.typography.bodyMedium,
                color = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 52.dp, end = 16.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_location),
                    contentDescription = null,
                    tint = XCalendarTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Join with Google Meet",
                    style = XCalendarTheme.typography.bodyMedium,
                    color = XCalendarTheme.colorScheme.primary,
                )
            }

            // Location section
            event.location?.let { location ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_location),
                        contentDescription = null,
                        tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = location,
                        style = XCalendarTheme.typography.bodyMedium,
                        color = XCalendarTheme.colorScheme.onSurface,
                    )
                }
            }

            // Time section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_notifications),
                    contentDescription = null,
                    tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "10 minutes before",
                    style = XCalendarTheme.typography.bodyMedium,
                    color = XCalendarTheme.colorScheme.onSurface,
                )
            }

            // Guest list section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_description),
                    contentDescription = null,
                    tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "The full guest list has been hidden at the organiser's request.",
                    style = XCalendarTheme.typography.bodyMedium,
                    color = XCalendarTheme.colorScheme.onSurface,
                )
            }

            // Description if available
            event.description?.let { description ->
                if (description.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_description),
                            contentDescription = null,
                            tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = description,
                            style = XCalendarTheme.typography.bodyMedium,
                            color = XCalendarTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
