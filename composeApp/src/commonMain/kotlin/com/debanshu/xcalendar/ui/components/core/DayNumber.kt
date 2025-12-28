package com.debanshu.xcalendar.ui.components.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.ui.theme.XCalendarTheme

/**
 * Reusable day number component that displays a day with optional "today" highlighting.
 * 
 * Used consistently across:
 * - DayCell (Month view)
 * - BaseCalendarScreen (Day view)
 * - DaysHeaderRow (Week/3-day view headers)
 * - CalendarTopAppBar (Mini calendar)
 * 
 * @param day The day number to display (1-31)
 * @param isToday Whether this day is today (shows primary background)
 * @param isCurrentMonth Whether this day belongs to the current month (affects text color)
 * @param isSelected Whether this day is selected (can add border or different styling)
 * @param modifier Modifier for the container
 * @param size The size of the day circle (default 30.dp)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DayNumber(
    day: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    isCurrentMonth: Boolean = true,
    isSelected: Boolean = false,
    size: Dp = 30.dp,
) {
    Box(
        modifier = modifier
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .size(size)
            .background(
                when {
                    isToday -> XCalendarTheme.colorScheme.primary
                    isSelected -> XCalendarTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = XCalendarTheme.typography.labelSmall,
            color = when {
                isToday -> XCalendarTheme.colorScheme.inverseOnSurface
                isSelected -> XCalendarTheme.colorScheme.onPrimaryContainer
                isCurrentMonth -> XCalendarTheme.colorScheme.onSurface
                else -> XCalendarTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Variant with larger text for header displays.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DayNumberLarge(
    day: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    Box(
        modifier = modifier
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .size(size)
            .background(
                when {
                    isToday -> XCalendarTheme.colorScheme.primary
                    else -> Color.Transparent
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = XCalendarTheme.typography.bodyMedium,
            color = when {
                isToday -> XCalendarTheme.colorScheme.inverseOnSurface
                else -> XCalendarTheme.colorScheme.onSurface
            },
        )
    }
}

