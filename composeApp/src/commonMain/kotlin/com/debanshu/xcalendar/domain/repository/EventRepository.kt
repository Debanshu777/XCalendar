package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.common.AppLogger
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

@Single(binds = [IEventRepository::class])
class EventRepository(
    private val eventDao: EventDao,
    private val apiService: RemoteCalendarApiService,
) : IEventRepository {
    /**
     * Fetches events from API and stores them locally.
     * @throws RepositoryException if the API call fails
     */
    override suspend fun getEventsForCalendar(
        calendarIds: List<String>,
        startTime: Long,
        endTime: Long,
    ) {
        when (val apiEvents = apiService.fetchEventsForCalendar(calendarIds, startTime, endTime)) {
            is Result.Error -> {
                val errorMessage = "Failed to fetch events: ${apiEvents.error}"
                AppLogger.e { errorMessage }
                throw RepositoryException(errorMessage)
            }

            is Result.Success -> {
                val events = apiEvents.data.map { it.asEvent() }
                events.forEach { event ->
                    addEvent(event)
                }
            }
        }
    }

    override fun getEventsForCalendarsInRange(
        userId: String,
        start: Long,
        end: Long,
    ): Flow<List<Event>> =
        eventDao.getEventsWithRemindersBetweenDates(userId, start, end).map { eventsWithReminders ->
            eventsWithReminders.map { it.asEvent() }
        }

    override suspend fun addEvent(event: Event) {
        val eventEntity = event.asEntity()
        val reminderEntities =
            event.reminderMinutes.map { minutes -> EventReminderEntity(event.id, minutes) }
        eventDao.insertEventWithReminders(eventEntity, reminderEntities)
    }

    override suspend fun updateEvent(event: Event) {
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

    override suspend fun deleteEvent(event: Event) {
        eventDao.deleteEvent(event.asEntity())
    }
}
