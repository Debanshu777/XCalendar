package com.debanshu.xcalendar.ui.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Controlled date/time fields keep incomplete edits in the draft for validation. */
@Composable
internal fun CalendarTimeSection(
    isAllDay: Boolean,
    startDate: String,
    endDate: String,
    startTime: String,
    endTime: String,
    onAllDayChange: (Boolean) -> Unit,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("All day")
            Switch(checked = isAllDay, onCheckedChange = onAllDayChange, enabled = enabled)
        }
        DateTimeInputRow("Start", startDate, startTime, isAllDay, onStartDateChange, onStartTimeChange, enabled)
        DateTimeInputRow("End", endDate, endTime, isAllDay, onEndDateChange, onEndTimeChange, enabled)
        Text(if (isAllDay) "End date is included." else "Dates: YYYY-MM-DD · Times: HH:mm (24-hour)")
    }
}

@Composable
private fun DateTimeInputRow(
    label: String,
    date: String,
    time: String,
    isAllDay: Boolean,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    enabled: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = date,
            onValueChange = onDateChange,
            label = { Text("$label date") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.weight(1.4f),
        )
        if (!isAllDay) {
            OutlinedTextField(
                value = time,
                onValueChange = onTimeChange,
                label = { Text("$label time") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
