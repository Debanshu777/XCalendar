package com.debanshu.xcalendar.data.store

import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
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
 * Thread-safe in-memory cache timestamp tracker for Store validation.
 *
 * Backed by `atomicfu` AtomicRef<PersistentMap> with CAS-style update loop —
 * lock-free, multi-thread safe across Dispatchers.Default/IO workers.
 *
 * One instance is bound to each Koin [UserSession] scope so TTL state cannot
 * leak across users (audit F23 / Phase 9).
 *
 * Key format: callers MUST namespace keys with `userId` / `countryCode`
 * (see [recordFetch] call sites in [StoreEventValidator] /
 * [StoreHolidayValidator]).
 */
class CacheTimestampTracker {
    private val state = atomic<PersistentMap<String, Long>>(persistentMapOf())

    /**
     * Pluggable time source. Production uses [kotlin.time.Clock.System]. Tests
     * may swap this via [setClockForTest] / [resetClockForTest] to drive
     * TTL expiry deterministically without sleeping.
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    private val clock = atomic<() -> Long>(
        { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    )

    private fun currentTimeMillis(): Long = clock.value.invoke()

    /** Inject a deterministic time source for tests. */
    internal fun setClockForTest(provider: () -> Long) {
        clock.value = provider
    }

    /** Restore the real system clock. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    internal fun resetClockForTest() {
        clock.value = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
    }

    fun recordFetch(key: String) {
        val now = currentTimeMillis()
        state.update { it.put(key, now) }
    }

    fun isExpired(key: String, maxAgeMillis: Long): Boolean {
        val lastFetch = state.value[key] ?: return true
        return (currentTimeMillis() - lastFetch) > maxAgeMillis
    }

    fun clear(key: String) {
        state.update { it.remove(key) }
    }

    /**
     * Drop every entry whose key starts with the given prefix.
     *
     * Use on targeted eviction (e.g. legacy logout hooks). Prefer closing the
     * Koin user session scope so this tracker instance is discarded entirely.
     */
    fun clearByPrefix(prefix: String) {
        state.update { current ->
            current.keys.filter { it.startsWith(prefix) }
                .fold(current) { acc, k -> acc.remove(k) }
        }
    }

    fun clearAll() {
        state.update { persistentMapOf() }
    }

    // Test/debug only.
    internal fun snapshot(): Map<String, Long> = state.value
}

/**
 * Validator for Holiday data that checks if cached data is still fresh.
 *
 * Holidays are relatively static, so we use a longer cache duration (24 hours).
 */
class StoreHolidayValidator(
    private val timestamps: CacheTimestampTracker,
) {
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
    fun isStale(key: HolidayKey): Boolean =
        timestamps.isExpired(cacheKey(key), maxAgeMillis)

    /**
     * Records that holiday data was fetched for a key.
     */
    fun recordFetch(key: HolidayKey) {
        timestamps.recordFetch(cacheKey(key))
    }

    private fun cacheKey(key: HolidayKey): String =
        "holiday:${key.countryCode}:${key.year}"
}

/**
 * Validator for Event list data that checks if cached data is still fresh.
 *
 * Events can change more frequently, so we use a shorter cache duration (1 hour).
 */
class StoreEventValidator(
    private val timestamps: CacheTimestampTracker,
) {
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
    fun isStale(key: EventKey): Boolean =
        timestamps.isExpired(cacheKey(key), maxAgeMillis)

    /**
     * Records that event data was fetched for a key.
     */
    fun recordFetch(key: EventKey) {
        timestamps.recordFetch(cacheKey(key))
    }

    /**
     * Invalidates cached event data, forcing a refresh on next access.
     */
    fun invalidate(key: EventKey) {
        timestamps.clear(cacheKey(key))
    }

    /**
     * Drops all event cache entries for a user. Call when recycling a session.
     */
    fun invalidateForUser(userId: String) {
        timestamps.clearByPrefix("event:$userId:")
    }

    private fun cacheKey(key: EventKey): String =
        "event:${key.userId}:${key.startTime}:${key.endTime}"
}
