@file:OptIn(ExperimentalStoreApi::class)

package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.common.model.asEntity
import com.debanshu.xcalendar.data.localDataSource.EventDao
import com.debanshu.xcalendar.data.store.EventKey
import com.debanshu.xcalendar.data.store.SingleEventKey
import com.debanshu.xcalendar.domain.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.mobilenativefoundation.store.core5.ExperimentalStoreApi
import org.mobilenativefoundation.store.store5.MutableStore
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.mobilenativefoundation.store.store5.StoreWriteRequest

/**
 * Repository for event data using Store5 MutableStore.
 *
 * MutableStore provides full CRUD support with:
 * - Automatic caching and sync via SourceOfTruth (Room DAO)
 * - Offline-first architecture
 * - Bookkeeper for tracking failed sync operations
 * - Request deduplication
 *
 * Write Operations Strategy:
 * - Add/Update: Use Store5's write() which persists via SourceOfTruth
 * - Delete: Direct DAO call required since Store5's clear() only clears cache
 *
 * This prevents duplicate writes while ensuring proper delete handling.
 */
@Single(binds = [IEventRepository::class])
class EventRepository(
    @Named("eventStore") private val eventStore: MutableStore<EventKey, List<Event>>,
    @Named("singleEventStore") private val singleEventStore: MutableStore<SingleEventKey, Event>,
    private val eventDao: EventDao,
) : BaseRepository(),
    IEventRepository {
    /**
     * Syncs events for calendars from the network.
     * Store5 handles caching automatically.
     */
    @OptIn(ExperimentalStoreApi::class)
    override suspend fun syncEventsForCalendar(
        calendarIds: List<String>,
        startTime: Long,
        endTime: Long,
    ): Unit =
        safeCallOrThrow("syncEventsForCalendar(range=$startTime-$endTime)") {
            // For now, we use an empty userId since the current API doesn't require it
            // This should be updated when proper user context is available
            val key = EventKey(userId = "", startTime = startTime, endTime = endTime)
            // Force a fresh fetch from the network
            eventStore
                .stream<Unit>(StoreReadRequest.fresh(key))
                .filterIsInstance<StoreReadResponse.Data<List<Event>>>()
                .first()
            Unit
        }

    /**
     * Gets events for a user in a date range.
     *
     * Store5 automatically:
     * - Returns cached data immediately
     * - Refreshes from network in background
     * - Updates cache and emits new data
     */
    @OptIn(ExperimentalStoreApi::class)
    override fun getEventsForCalendarsInRange(
        userId: String,
        start: Long,
        end: Long,
    ): Flow<List<Event>> {
        val key = EventKey(userId = userId, startTime = start, endTime = end)

        return safeFlow(
            flowName = "getEventsForCalendarsInRange",
            defaultValue = emptyList(),
            flow =
                eventStore
                    .stream<Unit>(StoreReadRequest.cached(key, refresh = false))
                    .filterIsInstance<StoreReadResponse.Data<List<Event>>>()
                    .map { it.value },
        )
    }

    /**
     * Adds a new event.
     *
     * Uses Store5's write mechanism which:
     * 1. Writes to SourceOfTruth (Room DAO) for local persistence
     * 2. Queues for network sync via Updater
     * 3. Tracks failures via Bookkeeper for retry
     */
    @OptIn(ExperimentalStoreApi::class)
    override suspend fun addEvent(event: Event): Unit =
        safeCallOrThrow("addEvent(${event.id})") {
            val key = SingleEventKey(event.id)
            singleEventStore.write(StoreWriteRequest.of(key, event))
        }

    /**
     * Updates an existing event.
     *
     * Uses Store5's write mechanism which handles:
     * 1. Local persistence via SourceOfTruth
     * 2. Network sync via Updater
     * 3. Failure tracking via Bookkeeper
     */
    @OptIn(ExperimentalStoreApi::class)
    override suspend fun updateEvent(event: Event): Unit =
        safeCallOrThrow("updateEvent(${event.id})") {
            val key = SingleEventKey(event.id)
            singleEventStore.write(StoreWriteRequest.of(key, event))
        }

    /**
     * Deletes an event.
     *
     * Note: Store5's clear() only clears the cache, not the SourceOfTruth.
     * We must delete from DAO directly, then clear the Store cache to
     * ensure consistency between cache and persistent storage.
     */
    @OptIn(ExperimentalStoreApi::class)
    override suspend fun deleteEvent(event: Event): Unit =
        safeCallOrThrow(
            "deleteEvent(${event.id})",
        ) {
            // Delete reminders first (foreign key constraint)
            eventDao.deleteEventReminders(event.id)

            // Delete from local database
            eventDao.deleteEvent(event.asEntity())

            // Clear from Store cache to prevent stale data
            val key = SingleEventKey(event.id)
            singleEventStore.clear(key)
        }
}
