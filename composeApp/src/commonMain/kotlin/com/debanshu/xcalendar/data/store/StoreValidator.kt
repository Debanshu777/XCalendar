package com.debanshu.xcalendar.data.store

import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import org.mobilenativefoundation.store.store5.Validator

/**
 * Cache duration constants for data freshness validation.
 */
object CacheDuration {
    /** Holidays are relatively static - cache for 24 hours */
    const val HOLIDAYS_CACHE_HOURS = 24L
    
    /** Events can change more frequently - cache for 1 hour */
    const val EVENTS_CACHE_HOURS = 1L
    
    /** Convert hours to milliseconds */
    fun hoursToMillis(hours: Long): Long = hours * 60 * 60 * 1000
}

/**
 * In-memory cache timestamp tracker for Store validation.
 * 
 * Used by Store Validators to track when data was last fetched
 * and determine if cached data needs refresh.
 */
object CacheTimestampTracker {
    private val timestamps = mutableMapOf<String, Long>()

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun currentTimeMillis(): Long = 
        kotlin.time.Clock.System.now().toEpochMilliseconds()

    fun recordFetch(key: String) {
        timestamps[key] = currentTimeMillis()
    }

    fun isExpired(key: String, maxAgeMillis: Long): Boolean {
        val lastFetch = timestamps[key] ?: return true
        return (currentTimeMillis() - lastFetch) > maxAgeMillis
    }

    fun clear(key: String) {
        timestamps.remove(key)
    }

    fun clearAll() {
        timestamps.clear()
    }
}

/**
 * Validator for Holiday data that checks if cached data is still fresh.
 * 
 * Holidays are relatively static, so we use a longer cache duration (24 hours).
 */
object HolidayValidator {
    
    private val maxAgeMillis = CacheDuration.hoursToMillis(CacheDuration.HOLIDAYS_CACHE_HOURS)

    /**
     * Creates a Validator for holiday data.
     */
    fun create(): Validator<List<Holiday>> = Validator.by { holidays ->
        if (holidays.isEmpty()) {
            AppLogger.d { "Holiday cache is empty, needs refresh" }
            false
        } else {
            AppLogger.d { "Holiday cache has ${holidays.size} items, considered valid" }
            true
        }
    }

    /**
     * Checks if holiday data for a specific key is stale and needs refresh.
     */
    fun isStale(key: HolidayKey): Boolean {
        val cacheKey = "holiday:${key.countryCode}:${key.year}"
        return CacheTimestampTracker.isExpired(cacheKey, maxAgeMillis)
    }

    /**
     * Records that holiday data was fetched for a key.
     */
    fun recordFetch(key: HolidayKey) {
        val cacheKey = "holiday:${key.countryCode}:${key.year}"
        CacheTimestampTracker.recordFetch(cacheKey)
    }
}

/**
 * Validator for Event list data that checks if cached data is still fresh.
 * 
 * Events can change more frequently, so we use a shorter cache duration (1 hour).
 */
object EventValidator {
    
    private val maxAgeMillis = CacheDuration.hoursToMillis(CacheDuration.EVENTS_CACHE_HOURS)

    /**
     * Creates a Validator for event list data.
     */
    fun create(): Validator<List<Event>> = Validator.by { events ->
        AppLogger.d { "Event cache has ${events.size} items" }
        true
    }

    /**
     * Checks if event data for a specific key is stale and needs refresh.
     */
    fun isStale(key: EventKey): Boolean {
        val cacheKey = "event:${key.userId}:${key.startTime}:${key.endTime}"
        return CacheTimestampTracker.isExpired(cacheKey, maxAgeMillis)
    }

    /**
     * Records that event data was fetched for a key.
     */
    fun recordFetch(key: EventKey) {
        val cacheKey = "event:${key.userId}:${key.startTime}:${key.endTime}"
        CacheTimestampTracker.recordFetch(cacheKey)
    }

    /**
     * Invalidates cached event data, forcing a refresh on next access.
     */
    fun invalidate(key: EventKey) {
        val cacheKey = "event:${key.userId}:${key.startTime}:${key.endTime}"
        CacheTimestampTracker.clear(cacheKey)
    }
}
