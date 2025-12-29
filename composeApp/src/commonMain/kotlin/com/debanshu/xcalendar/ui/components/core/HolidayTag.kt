package com.debanshu.xcalendar.ui.components.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debanshu.xcalendar.ui.theme.XCalendarColors
import com.debanshu.xcalendar.ui.theme.XCalendarTheme

/**
 * Reusable holiday tag component for displaying holiday names.
 * 
 * Used consistently across:
 * - DaysHeaderRow (Week/3-day view)
 * - DayCell (Month view)
 * - CalendarTopAppBar (Mini calendar)
 * 
 * @param name The holiday name to display
 * @param modifier Modifier for the tag
 * @param compact Whether to use compact styling (smaller font, less padding)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HolidayTag(
    name: String,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    Text(
        text = name,
        style = if (compact) {
            XCalendarTheme.typography.labelSmallEmphasized.copy(fontSize = 8.sp)
        } else {
            XCalendarTheme.typography.labelMedium
        },
        textAlign = TextAlign.Start,
        maxLines = 1,
        color = XCalendarColors.onHoliday,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .background(
                XCalendarColors.holiday,
                RoundedCornerShape(if (compact) 4.dp else 8.dp)
            )
            .padding(if (compact) 2.dp else 8.dp),
    )
}

/**
 * Holiday tag variant for schedule screen with different styling.
 */
@Composable
fun ScheduleHolidayTag(
    name: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = name,
        style = XCalendarTheme.typography.bodyMedium,
        maxLines = 1,
        color = XCalendarColors.scheduleHoliday,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .background(
                XCalendarColors.scheduleHolidayContainer,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}


