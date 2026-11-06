package com.debanshu.xcalendar.ui.screen.monthScreen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeekdayHeader() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth(),
    ) {
        val today =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        val ordinalToday = if (today.dayOfWeek.ordinal == 6) 0 else today.dayOfWeek.ordinal + 1
        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        daysOfWeek.forEachIndexed { dayIndex, day ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .background(XCalendarTheme.colorScheme.surfaceContainerLow)
                        .padding(vertical = XCalendarTheme.dimensions.spacing_8),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day,
                    style = XCalendarTheme.typography.bodySmallEmphasized,
                    color =
                        if (dayIndex == ordinalToday) {
                            XCalendarTheme.colorScheme.primary
                        } else {
                            XCalendarTheme.colorScheme.onSurface
                        },
                )
            }
        }
    }
}
