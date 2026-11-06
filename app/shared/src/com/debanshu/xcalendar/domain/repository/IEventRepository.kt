package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.domain.model.Event
import kotlinx.coroutines.flow.Flow

interface IEventRepository {
    suspend fun syncEventsForCalendar(
        calendarIds: List<String>,
        startTime: Long,
        endTime: Long,
    )

    fun getEventsForCalendarsInRange(
        userId: String,
        start: Long,
        end: Long,
    ): Flow<List<Event>>

    suspend fun addEvent(event: Event)

    suspend fun updateEvent(event: Event)

    suspend fun deleteEvent(event: Event)
}
