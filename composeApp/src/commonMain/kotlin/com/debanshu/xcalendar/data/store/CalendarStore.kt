package com.debanshu.xcalendar.data.store

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
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse

/**
 * Store5 wrapper for Calendar data.
 * Provides offline-first caching with automatic synchronization between
 * local database (source of truth) and remote API.
 */
@Single
class CalendarStore(
    private val calendarDao: CalendarDao,
    private val apiService: RemoteCalendarApiService,
) {
    /**
     * Store5 instance that manages calendar data caching.
     * - Fetcher: Gets data from the remote API
     * - SourceOfTruth: Gets/stores data in Room database
     */
    private val store: Store<String, List<Calendar>> = StoreBuilder.from(
        fetcher = Fetcher.of { userId: String ->
            when (val result = apiService.fetchCalendarsForUser(userId)) {
                is Result.Error -> {
                    AppLogger.e { "Store: Failed to fetch calendars: ${result.error}" }
                    throw StoreException("Failed to fetch calendars: ${result.error}")
                }
                is Result.Success -> {
                    AppLogger.d { "Store: Successfully fetched ${result.data.size} calendars" }
                    result.data.map { it.asCalendar() }
                }
            }
        },
        sourceOfTruth = SourceOfTruth.of(
            reader = { userId: String ->
                calendarDao.getCalendarsByUserId(userId).map { entities ->
                    entities.map { it.asCalendar() }
                }
            },
            writer = { _: String, calendars: List<Calendar> ->
                calendarDao.upsertCalendar(calendars.map { it.asCalendarEntity() })
            },
            delete = { userId: String ->
                // Delete all calendars for the user
                calendarDao.getCalendarsByUserId(userId).collect { calendars ->
                    calendars.forEach { calendar ->
                        calendarDao.deleteCalendar(calendar)
                    }
                }
            },
            deleteAll = {
                // Clear all calendars - not implemented for safety
            }
        )
    ).build()
    
    /**
     * Get calendars for a user with offline-first behavior.
     * Returns cached data immediately, then refreshes from network.
     */
    fun getCalendars(userId: String): Flow<StoreReadResponse<List<Calendar>>> =
        store.stream(StoreReadRequest.cached(userId, refresh = true))
    
    /**
     * Get calendars from cache only, without network refresh.
     */
    fun getCachedCalendars(userId: String): Flow<StoreReadResponse<List<Calendar>>> =
        store.stream(StoreReadRequest.cached(userId, refresh = false))
    
    /**
     * Force refresh calendars from network.
     * Collects the first successful result from the stream.
     */
    suspend fun refreshCalendars(userId: String): List<Calendar> {
        var result: List<Calendar> = emptyList()
        store.stream(StoreReadRequest.fresh(userId)).collect { response ->
            if (response is StoreReadResponse.Data) {
                result = response.value
                return@collect
            }
        }
        return result
    }
    
    /**
     * Get cached data or fetch from network if not available.
     * Collects the first successful result from the stream.
     */
    suspend fun getOrFetchCalendars(userId: String): List<Calendar> {
        var result: List<Calendar> = emptyList()
        store.stream(StoreReadRequest.cached(userId, refresh = true)).collect { response ->
            if (response is StoreReadResponse.Data) {
                result = response.value
                return@collect
            }
        }
        return result
    }
}

/**
 * Exception thrown when Store operations fail.
 */
class StoreException(message: String) : Exception(message)

