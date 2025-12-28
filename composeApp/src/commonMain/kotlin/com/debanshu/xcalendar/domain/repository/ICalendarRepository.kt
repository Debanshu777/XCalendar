package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.domain.model.Calendar
import kotlinx.coroutines.flow.Flow

interface ICalendarRepository {
    suspend fun refreshCalendarsForUser(userId: String)

    fun getCalendarsForUser(userId: String): Flow<List<Calendar>>

    suspend fun upsertCalendar(calendars: List<Calendar>)

    suspend fun deleteCalendar(calendar: Calendar)
}

