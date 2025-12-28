package com.debanshu.xcalendar.ui.components.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import xcalendar.composeapp.generated.resources.Res
import xcalendar.composeapp.generated.resources.ic_notifications
import xcalendar.composeapp.generated.resources.ic_unfold_more

/**
 * Row for displaying and editing notification/reminder settings.
 */
@Composable
internal fun NotificationRow(
    reminderMinutes: Int,
    onReminderChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { /* Open reminder picker */ }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_notifications),
            contentDescription = null,
            tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = formatReminderText(reminderMinutes),
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

/**
 * Formats reminder time for display.
 */
private fun formatReminderText(minutes: Int): String {
    return when {
        minutes <= 0 -> "No reminder"
        minutes < 60 -> "$minutes minutes before"
        minutes == 60 -> "1 hour before"
        minutes < 1440 -> "${minutes / 60} hours before"
        minutes == 1440 -> "1 day before"
        else -> "${minutes / 1440} days before"
    }
}

/**
 * Generic option row with icon and text.
 */
@Composable
internal fun EventOptionRow(
    icon: DrawableResource,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
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

