package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.common.model.asEntity
import com.debanshu.xcalendar.common.model.asEvent
import com.debanshu.xcalendar.data.localDataSource.EventDao
import com.debanshu.xcalendar.data.localDataSource.model.EventReminderEntity
import com.debanshu.xcalendar.data.remoteDataSource.RemoteCalendarApiService
import com.debanshu.xcalendar.data.remoteDataSource.Result
import com.debanshu.xcalendar.domain.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class EventRepository(
    private val eventDao: EventDao,
    private val apiService: RemoteCalendarApiService,
) {
    suspend fun getEventsForCalendar(
        calendarIds: List<String>,
        startTime: Long,
        endTime: Long,
    ) {
        when (val apiEvents = apiService.fetchEventsForCalendar(calendarIds, startTime, endTime)) {
            is Result.Error -> {
                println("HEREEEEEEE" + apiEvents.error.toString())
            }

            is Result.Success -> {
                val events = apiEvents.data.map { it.asEvent() }
                events.forEach { event ->
                    addEvent(event)
                }
            }
        }
    }

    fun getEventsForCalendarsInRange(
        userId: String,
        start: Long,
        end: Long,
    ): Flow<List<Event>> =
        eventDao.getEventsBetweenDates(userId, start, end).map { entities ->
            entities.map { it.asEvent() }
        }

    suspend fun addEvent(event: Event) {
        val eventEntity = event.asEntity()
        val reminderEntities =
            event.reminderMinutes.map { minutes -> EventReminderEntity(event.id, minutes) }
        eventDao.insertEventWithReminders(eventEntity, reminderEntities)
    }

    suspend fun updateEvent(event: Event) {
        val eventEntity = event.asEntity()
        eventDao.upsertEvent(eventEntity)

        eventDao.deleteEventReminders(event.id)
        val reminderEntities =
            event.reminderMinutes.map { minutes ->
                EventReminderEntity(event.id, minutes)
            }
        reminderEntities.forEach { reminder ->
            eventDao.insertEventReminder(reminder)
        }
    }

    suspend fun deleteEvent(event: Event) {
        eventDao.deleteEvent(event.asEntity())
    }
}
