package com.debanshu.xcalendar.ui.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.ui.theme.XCalendarTheme

/**
 * Event type options for creating calendar entries.
 */
enum class EventType(
    val displayName: String,
) {
    EVENT("Event"),
    TASK("Task"),
    WORKING_LOCATION("Working location"),
    OUT_OF_OFFICE("Out of office"),
}

/**
 * Horizontal selector for event types.
 */
@Composable
internal fun EventTypeSelector(
    selectedType: EventType,
    onTypeSelected: (EventType) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(EventType.entries.toTypedArray()) { index, type ->
            val isSelected = selectedType == type
            Box(
                modifier = Modifier
                    .padding(
                        start = if (index == 0) 54.dp else 0.dp,
                        end = if (index == EventType.entries.size - 1) 16.dp else 0.dp,
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) {
                            XCalendarTheme.colorScheme.primary
                        } else {
                            XCalendarTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable { onTypeSelected(type) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = type.displayName,
                    color = if (isSelected) {
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


