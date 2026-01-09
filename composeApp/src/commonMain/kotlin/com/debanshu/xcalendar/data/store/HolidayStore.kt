@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.debanshu.xcalendar.data.store

import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.common.DateRangeHelper
import com.debanshu.xcalendar.common.model.asHoliday
import com.debanshu.xcalendar.common.model.asHolidayEntity
import com.debanshu.xcalendar.data.localDataSource.HolidayDao
import com.debanshu.xcalendar.data.remoteDataSource.HolidayApiService
import com.debanshu.xcalendar.data.remoteDataSource.Result
import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.coroutines.flow.map
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder

/**
 * Creates a Store for holidays that handles:
 * - Fetching from the Calendarific API
 * - Caching to Room database
 * - Converting between network, local, and domain models
 */
object HolidayStoreFactory {

    fun create(
        holidayApiService: HolidayApiService,
        holidayDao: HolidayDao
    ): Store<HolidayKey, List<Holiday>> {
        return StoreBuilder.from(
            fetcher = createFetcher(holidayApiService),
            sourceOfTruth = createSourceOfTruth(holidayDao)
        )
            .validator(HolidayValidator.create())
            .build()
    }

    private fun createFetcher(
        holidayApiService: HolidayApiService
    ): Fetcher<HolidayKey, List<Holiday>> = Fetcher.of { key ->
        AppLogger.d { "Fetching holidays for ${key.countryCode}, year ${key.year}" }
        when (val response = holidayApiService.getHolidays(key.countryCode, key.year)) {
            is Result.Error -> {
                AppLogger.e { "Failed to fetch holidays: ${response.error}" }
                throw StoreException("Failed to fetch holidays: ${response.error}")
            }
            is Result.Success -> {
                AppLogger.d { "Fetched ${response.data.response.holidays.size} holidays" }
                // Record the fetch time for cache freshness tracking
                HolidayValidator.recordFetch(key)
                // Convert network model to domain model
                response.data.response.holidays.map { it.asHoliday() }
            }
        }
    }

    private fun createSourceOfTruth(
        holidayDao: HolidayDao
    ): SourceOfTruth<HolidayKey, List<Holiday>, List<Holiday>> = SourceOfTruth.of(
        reader = { key ->
            val (startDate, endDate) = getDateRangeForYear(key.year)
            holidayDao.getHolidaysInRange(startDate, endDate).map { entities ->
                entities
                    .filter { it.countryCode.equals(key.countryCode, ignoreCase = true) }
                    .map { it.asHoliday() }
            }
        },
        writer = { _, holidays ->
            holidayDao.insertHolidays(holidays.map { it.asHolidayEntity() })
        },
        delete = { _ ->
            // No-op for now
        },
        deleteAll = {
            // No-op for now
        }
    )

    /**
     * Gets the date range for a specific year.
     * Delegates to DateRangeHelper for consistent date handling across the app.
     */
    private fun getDateRangeForYear(year: Int): Pair<Long, Long> =
        DateRangeHelper.getYearRange(year)
}

/**
 * Custom exception for Store operations
 */
class StoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
