package com.debanshu.xcalendar.ui.components.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.resources.Res
import com.debanshu.xcalendar.resources.ic_notifications
import com.debanshu.xcalendar.resources.ic_unfold_more
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import org.jetbrains.compose.resources.painterResource

/** Existing multiple reminders are preserved unless the user explicitly replaces them. */
@Composable
internal fun NotificationRow(
    reminderMinutes: List<Int>,
    onReminderChange: (List<Int>) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(Res.drawable.ic_notifications), contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Text(
                if (reminderMinutes.isEmpty()) "No reminder" else reminderMinutes.joinToString { formatReminderText(it) },
                modifier = Modifier.weight(1f),
                style = XCalendarTheme.typography.bodyMedium,
            )
            Icon(painterResource(Res.drawable.ic_unfold_more), contentDescription = "Change reminder")
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            listOf(null, 0, 5, 10, 15, 30, 60, 1440, 10080).forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(minutes?.let(::formatReminderText) ?: "No reminder") },
                    onClick = {
                        onReminderChange(if (minutes == null) emptyList() else listOf(minutes))
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun formatReminderText(minutes: Int): String = when {
    minutes < 0 -> "No reminder"
    minutes == 0 -> "At start time"
    minutes == 1 -> "1 minute before"
    minutes % 1440 == 0 -> "${minutes / 1440} ${if (minutes == 1440) "day" else "days"} before"
    minutes % 60 == 0 -> "${minutes / 60} ${if (minutes == 60) "hour" else "hours"} before"
    else -> "$minutes minutes before"
}
