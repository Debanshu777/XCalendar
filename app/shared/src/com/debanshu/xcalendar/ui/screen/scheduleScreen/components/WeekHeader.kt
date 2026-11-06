package com.debanshu.xcalendar.ui.screen.scheduleScreen.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.datetime.LocalDate

@Composable
fun WeekHeader(startDate: LocalDate, endDate: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 64.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${startDate.day} – ${endDate.day} ${endDate.month.name.take(3)}"
                .uppercase(),
            style = XCalendarTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}