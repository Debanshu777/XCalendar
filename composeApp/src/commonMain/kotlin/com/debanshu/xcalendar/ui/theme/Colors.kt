package com.debanshu.xcalendar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Extended color scheme for XCalendar-specific colors.
 * These colors supplement Material3's ColorScheme for domain-specific needs.
 */
object XCalendarColors {
    // Holiday colors
    val holiday = Color(0xFF007F73)
    val holidayContainer = Color(0xFF007F73).copy(alpha = 0.1f)
    val onHoliday = Color.White
    
    // Schedule holiday (green variant)
    val scheduleHoliday = Color(0xFF4CAF50)
    val scheduleHolidayContainer = Color(0xFF4CAF50).copy(alpha = 0.15f)
    
    // Event indicator colors for month view
    val eventDot = Color(0xFF2196F3)
    
    // Calendar grid colors
    val gridLine = Color(0xFFE0E0E0)
    val currentTimeLine = Color(0xFFEA4335) // Google Calendar red for current time
    
    // Today highlight
    val todayBackground = Color(0xFF4285F4) // Google Blue
    val onToday = Color.White
    
    // Weekend day text color
    val weekendText = Color(0xFF5F6368).copy(alpha = 0.7f)
}

/**
 * Access extended colors through XCalendarTheme.
 * Usage: XCalendarTheme.extendedColors.holiday
 */
val XCalendarTheme.extendedColors: XCalendarColors
    @Composable @ReadOnlyComposable
    get() = XCalendarColors

