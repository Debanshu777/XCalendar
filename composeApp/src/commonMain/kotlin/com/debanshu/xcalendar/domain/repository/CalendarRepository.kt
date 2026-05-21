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

/**
 * Repository for user calendars.
 *
 * **Why no Store5**: calendar metadata for this app is small, mutates only
 * via the explicit `refreshCalendarsForUser` call, and has no
 * background-fetch / TTL / coalescing requirements (audit F21). The Room
 * DAO is the source of truth; the remote API is a one-shot refresh that
 * upserts into it. Adding a Store5 layer here would buy us nothing and
 * add a layer of indirection for tests and DI. Revisit only if/when
 * calendars gain real-time push or per-key TTL semantics like events do.
 */
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
