package com.debanshu.xcalendar.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Event(
    val id: String,
    val calendarId: String,
    val calendarName: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean = false,
    val isRecurring: Boolean = false,
    val recurringRule: String? = null,
    val reminderMinutes: List<Int> = emptyList(),
    val color: Int,
)
