package com.debanshu.xcalendar.domain.repository

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
) : BaseRepository(), ICalendarRepository {
    
    override suspend fun refreshCalendarsForUser(userId: String) = safeCallOrThrow("refreshCalendarsForUser($userId)") {
        when (val apiCalendars = apiService.fetchCalendarsForUser(userId)) {
            is Result.Error -> {
                throw RepositoryException("Failed to fetch calendars: ${apiCalendars.error}")
            }
            is Result.Success -> {
                val calendars = apiCalendars.data.map { it.asCalendar() }
                upsertCalendar(calendars)
            }
        }
    }

    override fun getCalendarsForUser(userId: String): Flow<List<Calendar>> =
        safeFlow(
            flowName = "getCalendarsForUser($userId)",
            defaultValue = emptyList(),
            flow = calendarDao
                .getCalendarsByUserId(userId)
                .map { entities -> entities.map { it.asCalendar() } }
        )

    override suspend fun upsertCalendar(calendars: List<Calendar>) =
        safeCallOrThrow("upsertCalendar(${calendars.size} calendars)") {
            calendarDao.upsertCalendar(calendars.map { it.asCalendarEntity() })
        }

    override suspend fun deleteCalendar(calendar: Calendar) =
        safeCallOrThrow("deleteCalendar(${calendar.id})") {
            calendarDao.deleteCalendar(calendar.asCalendarEntity())
        }
}
