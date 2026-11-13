package com.debanshu.xcalendar.ui.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.debanshu.xcalendar.ui.utils.DateTimeFormatter
import org.jetbrains.compose.resources.painterResource
import com.debanshu.xcalendar.resources.Res
import com.debanshu.xcalendar.resources.ic_close
import com.debanshu.xcalendar.resources.ic_description
import com.debanshu.xcalendar.resources.ic_edit
import com.debanshu.xcalendar.resources.ic_location
import com.debanshu.xcalendar.resources.ic_notifications

/**
 * Bottom sheet dialog showing event details with edit option.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsDialog(
    event: Event,
    onEdit: (Event) -> Unit,
    onDismiss: () -> Unit = {},
    canEdit: Boolean = true,
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
            // Header with close and edit icons
            DetailsHeader(
                onClose = onDismiss,
                onEdit = { onEdit(event) },
                canEdit = canEdit,
            )

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))

                // Event title with color indicator - shared element for transition
                EventTitleRow(
                    title = event.title,
                    color = event.color,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Date/time subheading
                Text(
                    text = DateTimeFormatter.formatEventSubheading(event),
                    style = XCalendarTheme.typography.bodyMedium,
                    color = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Location section
                event.location?.let { location ->
                    DetailRow(
                        icon = Res.drawable.ic_location,
                        text = location,
                    )
                }

                DetailRow(
                    icon = Res.drawable.ic_notifications,
                    text = if (event.reminderMinutes.isEmpty()) "No reminder" else
                        event.reminderMinutes.joinToString { if (it == 0) "At start time" else "$it minutes before" },
                )

                // Description if available
                event.description?.takeIf { it.isNotEmpty() }?.let { description ->
                    DetailRow(
                        icon = Res.drawable.ic_description,
                        text = description,
                        verticalAlignment = Alignment.Top,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsHeader(
    onClose: () -> Unit,
    onEdit: () -> Unit,
    canEdit: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = "Close",
                tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        IconButton(onClick = onEdit, enabled = canEdit) {
            Icon(
                painter = painterResource(Res.drawable.ic_edit),
                contentDescription = "Edit",
                tint = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun EventTitleRow(
    title: String,
    color: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Color indicator
        Box(
            modifier =
                Modifier
                    .width(16.dp)
                    .height(16.dp)
                    .background(
                        Color(color),
                        RoundedCornerShape(2.dp),
                    ),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = XCalendarTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DetailRow(
    icon: org.jetbrains.compose.resources.DrawableResource,
    text: String,
    iconTint: Color = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    textColor: Color = XCalendarTheme.colorScheme.onSurface,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    Row(
        verticalAlignment = verticalAlignment,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            style = XCalendarTheme.typography.bodyMedium,
            color = textColor,
        )
    }
}
