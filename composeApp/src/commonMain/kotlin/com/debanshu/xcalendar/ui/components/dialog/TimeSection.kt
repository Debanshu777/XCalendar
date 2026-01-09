package com.debanshu.xcalendar.ui.components.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.debanshu.xcalendar.ui.utils.DateTimeFormatter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.painterResource
import xcalendar.composeapp.generated.resources.Res
import xcalendar.composeapp.generated.resources.ic_clock

/**
 * Time section for event dialogs with all-day toggle and time display.
 */
@Composable
internal fun CalendarTimeSection(
    isAllDayInitial: Boolean,
    selectedDate: LocalDate,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
    onAllDayChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isAllDay by remember { mutableStateOf(isAllDayInitial) }

    // All-day toggle row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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

        Switch(
            checked = isAllDay,
            onCheckedChange = {
                isAllDay = !isAllDay
                onAllDayChange(isAllDay)
            },
        )
    }

    // Date and time display
    val dateLabel = formatDateLabel(selectedDate)

    if (isAllDay) {
        TimeDisplayRow(
            label = dateLabel,
            onClick = { /* Handle date picker */ },
        )
    } else {
        TimeDisplayRow(
            label = dateLabel,
            startTime = DateTimeFormatter.formatTime(startDateTime),
            endTime = DateTimeFormatter.formatTime(endDateTime),
            onClick = { /* Handle time picker */ },
        )
    }
}

/**
 * Row displaying date/time with optional start and end times.
 */
@Composable
internal fun TimeDisplayRow(
    label: String,
    startTime: String? = null,
    endTime: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Start time row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
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

    // End time row (only if endTime is provided)
    if (endTime != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(top = 8.dp, bottom = 8.dp, start = 52.dp, end = 16.dp),
        ) {
            Text(
                text = label,
                style = XCalendarTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = endTime,
                style = XCalendarTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Formats a date for display in time section.
 */
private fun formatDateLabel(date: LocalDate): String {
    val month =
        date.month.name
            .lowercase()
            .replaceFirstChar { it.titlecase() }
    return "$month ${date.dayOfMonth}, ${date.year}"
}
