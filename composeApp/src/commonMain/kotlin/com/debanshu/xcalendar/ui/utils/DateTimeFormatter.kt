package com.debanshu.xcalendar.ui.utils

import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

/**
 * Centralized date/time formatting utilities for the UI layer.
 * Consolidates all time formatting logic to avoid duplication.
 */
object DateTimeFormatter {

    /**
     * Formats a LocalDateTime to a 12-hour time string with AM/PM.
     * Example: "2:30 PM", "12:00 AM"
     */
    fun formatTime(dateTime: LocalDateTime): String {
        val hour = when {
            dateTime.hour == 0 -> 12
            dateTime.hour > 12 -> dateTime.hour - 12
            else -> dateTime.hour
        }
        val minute = dateTime.minute.toString().padStart(2, '0')
        val amPm = if (dateTime.hour >= 12) "PM" else "AM"
        return "$hour:$minute $amPm"
    }

    /**
     * Formats a time range between two LocalDateTime values.
     * Example: "2:30 PM – 4:00 PM"
     */
    fun formatTimeRange(start: LocalDateTime, end: LocalDateTime): String {
        return "${formatTime(start)} – ${formatTime(end)}"
    }

    /**
     * Formats a compact time range for event display.
     * Handles same/different AM/PM periods intelligently.
     * Example: "2-4PM" or "11AM-1PM"
     */
    fun formatCompactTimeRange(start: LocalDateTime, end: LocalDateTime): String {
        val startHour = when {
            start.hour == 0 -> 12
            start.hour > 12 -> start.hour - 12
            else -> start.hour
        }
        val endHour = when {
            end.hour == 0 -> 12
            end.hour > 12 -> end.hour - 12
            else -> end.hour
        }
        val startMinute = if (start.minute == 0) "" else ":${start.minute.toString().padStart(2, '0')}"
        val endMinute = if (end.minute == 0) "" else ":${end.minute.toString().padStart(2, '0')}"

        return when {
            // Same AM/PM period
            (start.hour < 12 && end.hour < 12) || (start.hour >= 12 && end.hour >= 12) -> {
                val amPm = if (end.hour >= 12) "PM" else "AM"
                "$startHour$startMinute-$endHour$endMinute$amPm"
            }
            // Different AM/PM periods
            else -> {
                val startAmPm = if (start.hour >= 12) "PM" else "AM"
                val endAmPm = if (end.hour >= 12) "PM" else "AM"
                "$startHour$startMinute$startAmPm-$endHour$endMinute$endAmPm"
            }
        }
    }

    /**
     * Formats an event's date and time as a descriptive subheading.
     * Example: "Friday, 20 Jun 6-7PM" or "Friday, 20 Jun" for all-day events.
     */
    fun formatEventSubheading(event: Event): String {
        val startDateTime = event.startTime.toLocalDateTime(TimeZone.currentSystemDefault())
        val endDateTime = event.endTime.toLocalDateTime(TimeZone.currentSystemDefault())

        val dayOfWeek = startDateTime.date.dayOfWeek.name
            .lowercase()
            .replaceFirstChar { it.titlecase() }

        val day = startDateTime.date.day
        val month = startDateTime.date.month.name
            .take(3)
            .lowercase()
            .replaceFirstChar { it.titlecase() }

        val mainLine = if (event.isAllDay) {
            "$dayOfWeek, $day $month"
        } else {
            val timeRange = formatCompactTimeRange(startDateTime, endDateTime)
            "$dayOfWeek, $day $month $timeRange"
        }

        // Add recurring information if applicable
        return if (event.isRecurring && !event.recurringRule.isNullOrBlank()) {
            val recurringText = when {
                event.recurringRule.contains("WEEKLY", ignoreCase = true) -> "Repeat every week"
                event.recurringRule.contains("DAILY", ignoreCase = true) -> "Repeat every day"
                event.recurringRule.contains("MONTHLY", ignoreCase = true) -> "Repeat every month"
                event.recurringRule.contains("YEARLY", ignoreCase = true) -> "Repeat every year"
                else -> "Recurring event"
            }
            "$mainLine\n$recurringText"
        } else {
            mainLine
        }
    }

    /**
     * Formats hour for time column display.
     * Returns empty string for midnight (0), otherwise "12 pm", "1 am", etc.
     */
    fun formatHour(hour: Int): String {
        val displayHour = when {
            hour == 0 || hour == 12 -> "12"
            hour > 12 -> (hour - 12).toString()
            else -> hour.toString()
        }
        val amPm = if (hour >= 12) "pm" else "am"
        return if (hour == 0) "" else "$displayHour $amPm"
    }

    /**
     * Formats a date as a month and year string.
     * Example: "January 2025"
     */
    fun formatMonthYear(dateTime: LocalDateTime): String {
        val month = dateTime.month.name
            .lowercase()
            .replaceFirstChar { it.titlecase() }
        return "$month ${dateTime.year}"
    }

    /**
     * Formats a date for display with day of week.
     * Example: "December 28, 2025"
     */
    fun formatFullDate(dateTime: LocalDateTime): String {
        val month = dateTime.month.name
            .lowercase()
            .replaceFirstChar { it.titlecase() }
        return "$month ${dateTime.dayOfMonth}, ${dateTime.year}"
    }
}


