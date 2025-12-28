package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.data.store.HolidayKey
import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse

/**
 * Repository for holiday data using Store5.
 * 
 * Store5 automatically handles:
 * - Caching to Room database
 * - Network fetching with automatic retry
 * - Request deduplication (multiple requesters share the same network call)
 * - Offline-first: serves cached data while refreshing from network
 */
@Single(binds = [IHolidayRepository::class])
class HolidayRepository(
    private val holidayStore: Store<HolidayKey, List<Holiday>>,
) : BaseRepository(), IHolidayRepository {

    /**
     * Refreshes holidays from the network.
     * Store5 handles caching automatically after a successful fetch.
     */
    override suspend fun updateHolidays(
        countryCode: String,
        year: Int,
    ) = safeCallOrThrow("updateHolidays($countryCode, $year)") {
        val key = HolidayKey(countryCode, year)
        // Force a fresh fetch from the network using stream with skipCache
        val request = StoreReadRequest.fresh(key)
        holidayStore.stream(request).collect { response ->
            when (response) {
                is StoreReadResponse.Data -> {
                    logDebug { "Successfully refreshed holidays for $countryCode, $year: ${response.value.size} holidays" }
                    return@collect
                }
                is StoreReadResponse.Error.Exception -> {
                    throw response.error
                }
                is StoreReadResponse.Error.Message -> {
                    throw RepositoryException(response.message)
                }
                else -> {
                    // Loading, NoNewData, etc. - continue collecting
                }
            }
        }
    }

    /**
     * Gets holidays for a specific year and country.
     * 
     * Store5 automatically:
     * - Returns cached data immediately if available
     * - Fetches from network in the background
     * - Updates the cache and emits new data
     */
    override fun getHolidaysForYear(
        countryCode: String,
        year: Int,
    ): Flow<List<Holiday>> {
        val key = HolidayKey(countryCode, year)
        
        return safeFlow(
            flowName = "getHolidaysForYear($countryCode, $year)",
            defaultValue = emptyList(),
            flow = holidayStore.stream(StoreReadRequest.cached(key, refresh = true))
                .filterIsInstance<StoreReadResponse.Data<List<Holiday>>>()
                .map { it.value }
        )
    }
}
