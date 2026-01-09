package com.debanshu.xcalendar.ui.screen.scheduleScreen.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.debanshu.xcalendar.ui.transition.SharedElementType
import com.debanshu.xcalendar.ui.transition.sharedEventElement

@Composable
fun EventItem(
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    eventId: String? = null,
    isVisible: Boolean = true,
    timeText: String? = null,
) {
    // Optimized: Pre-calculate colors to avoid repeated alpha calculations
    val backgroundColor = remember(color) { color.copy(alpha = 0.15f) }
    val timeTextColor = remember(color) { color.copy(alpha = 0.7f) }

    // Apply shared element modifier only if eventId is provided
    val surfaceModifier =
        if (eventId != null) {
            modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .sharedEventElement(
                    eventId = eventId,
                    type = SharedElementType.EventCard,
                    isVisible = isVisible,
                )
                .clickable(onClick = onClick)
        } else {
            modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable(onClick = onClick)
        }

    Surface(
        modifier = surfaceModifier,
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = title,
                style = XCalendarTheme.typography.bodyMedium,
                color = color,
                fontWeight = FontWeight.Medium,
            )

            timeText?.let {
                Text(
                    text = it,
                    style = XCalendarTheme.typography.labelSmall,
                    color = timeTextColor,
                    fontSize = 12.sp,
                )
            }
        }
    }
}