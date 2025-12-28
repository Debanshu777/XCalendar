package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.common.model.asCalendar
import com.debanshu.xcalendar.common.model.asCalendarEntity
import com.debanshu.xcalendar.data.localDataSource.CalendarDao
import com.debanshu.xcalendar.data.remoteDataSource.RemoteCalendarApiService
import com.debanshu.xcalendar.data.remoteDataSource.Result
import com.debanshu.xcalendar.domain.model.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [ICalendarRepository::class])
class CalendarRepository(
    private val calendarDao: CalendarDao,
    private val apiService: RemoteCalendarApiService,
) : ICalendarRepository {
    /**
     * Refreshes calendars from API and stores them locally.
     * @throws RepositoryException if the API call fails
     */
    override suspend fun refreshCalendarsForUser(userId: String) {
        when (val apiCalendars = apiService.fetchCalendarsForUser(userId)) {
            is Result.Error -> {
                val errorMessage = "Failed to fetch calendars: ${apiCalendars.error}"
                AppLogger.e { errorMessage }
                throw RepositoryException(errorMessage)
            }

            is Result.Success -> {
                AppLogger.d { "Successfully fetched ${apiCalendars.data.size} calendars" }
                val calendars = apiCalendars.data.map { it.asCalendar() }
                upsertCalendar(calendars)
            }
        }
    }

    override fun getCalendarsForUser(userId: String): Flow<List<Calendar>> =
        calendarDao
            .getCalendarsByUserId(userId)
            .map { entities -> entities.map { it.asCalendar() } }

    override suspend fun upsertCalendar(calendars: List<Calendar>) {
        calendarDao.upsertCalendar(calendars.map { it.asCalendarEntity() })
    }

    override suspend fun deleteCalendar(calendar: Calendar) {
        calendarDao.deleteCalendar(calendar.asCalendarEntity())
    }
}
