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

@Single
class CalendarRepository(
    private val calendarDao: CalendarDao,
    private val apiService: RemoteCalendarApiService,
) {
    suspend fun getCalendersForUser(userId: String) {
        when (val apiCalendars = apiService.fetchCalendarsForUser(userId)) {
            is Result.Error -> {
                println("HEREEEEEEE" + apiCalendars.error.toString())
            }

            is Result.Success -> {
                println("HEREEEEEEE 2" + apiCalendars.toString())
                val calendars = apiCalendars.data.map { it.asCalendar() }
                upsertCalendar(calendars)
            }
        }
    }

    fun getCalendarsForUser(userId: String): Flow<List<Calendar>> =
        calendarDao
            .getCalendarsByUserId(userId)
            .map { entities -> entities.map { it.asCalendar() } }

    suspend fun upsertCalendar(calendars: List<Calendar>) {
        calendarDao.upsertCalendar(calendars.map { it.asCalendarEntity() })
    }

    suspend fun deleteCalendar(calendar: Calendar) {
        calendarDao.deleteCalendar(calendar.asCalendarEntity())
    }
}
