@file:OptIn(ExperimentalStoreApi::class)

package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.common.model.asEntity
import com.debanshu.xcalendar.data.localDataSource.EventDao
import com.debanshu.xcalendar.data.outbox.OutboxWriter
import com.debanshu.xcalendar.di.UserSession
import com.debanshu.xcalendar.data.store.EventKey
import com.debanshu.xcalendar.data.store.StoreEventValidator
import com.debanshu.xcalendar.data.store.SingleEventKey
import com.debanshu.xcalendar.domain.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Named
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
class EventRepository(
    @Named("eventStore") private val eventStore: MutableStore<EventKey, List<Event>>,
    @Named("singleEventStore") private val singleEventStore: MutableStore<SingleEventKey, Event>,
    private val eventDao: EventDao,
    private val outbox: OutboxWriter,
    private val userSession: UserSession,
    private val eventValidator: StoreEventValidator,
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
            val key =
                EventKey(
                    userId = userSession.userId,
                    startTime = startTime,
                    endTime = endTime,
                )
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
     *
     * `refresh` is driven by [StoreEventValidator.isStale] — the network is hit
     * only when the TTL (see [com.debanshu.xcalendar.data.store.CacheDuration.EVENTS_CACHE_HOURS])
     * has elapsed for this key. Within the TTL window the cached SoT serves
     * everything, eliminating the per-pan refetch that used to happen even
     * when nothing changed (audit F5).
     */
    @OptIn(ExperimentalStoreApi::class)
    override fun getEventsForCalendarsInRange(
        userId: String,
        start: Long,
        end: Long,
    ): Flow<List<Event>> {
        val key = EventKey(userId = userId, startTime = start, endTime = end)
        val shouldRefresh = eventValidator.isStale(key)

        return safeFlow(
            flowName = "getEventsForCalendarsInRange",
            defaultValue = emptyList(),
            flow =
                eventStore
                    .stream<Unit>(StoreReadRequest.cached(key, refresh = shouldRefresh))
                    .filterIsInstance<StoreReadResponse.Data<List<Event>>>()
                    .map { it.value },
        )
    }

    /**
     * Adds a new event.
     *
     * 1. Local persistence via Store5's `write()` (SourceOfTruth → Room DAO).
     * 2. Outbox row enqueued so the eventual remote backend can replay this
     *    CREATE op even after a process death or network outage (audit F29).
     *    The Updater currently no-ops; without the outbox the write would be
     *    silently lost when a real backend ships.
     * 3. Failure tracking via Bookkeeper if the Updater fails.
     */
    @OptIn(ExperimentalStoreApi::class)
    override suspend fun addEvent(event: Event): Unit =
        safeCallOrThrow("addEvent(${event.id})") {
            val key = SingleEventKey(event.id)
            singleEventStore.write(StoreWriteRequest.of(key, event))
            outbox.enqueueEventCreate(event)
        }

    /**
     * Updates an existing event.
     *
     * Local persistence via Store5; remote replay queued in the outbox
     * (audit F29). See [addEvent] for the rationale.
     */
    @OptIn(ExperimentalStoreApi::class)
    override suspend fun updateEvent(event: Event): Unit =
        safeCallOrThrow("updateEvent(${event.id})") {
            val key = SingleEventKey(event.id)
            singleEventStore.write(StoreWriteRequest.of(key, event))
            outbox.enqueueEventUpdate(event)
        }

    /**
     * Deletes an event.
     *
     * DB deletion (parent + reminder rows) is wrapped in a Room
     * `@Transaction` inside [EventDao.deleteEventWithReminders] (audit F22).
     * Without the transaction a partial failure could leave orphan reminder
     * rows after the parent row is gone.
     *
     * Store5's `clear()` only drops the cache, so it runs after the DB
     * transaction commits — if the DAO call throws, the cache is left alone
     * and the next read repopulates from the still-present row.
     *
     * A DELETE row is enqueued in the outbox so the remote replay knows the
     * event was removed locally (audit F29).
     */
    @OptIn(ExperimentalStoreApi::class)
    override suspend fun deleteEvent(event: Event): Unit =
        safeCallOrThrow(
            "deleteEvent(${event.id})",
        ) {
            eventDao.deleteEventWithReminders(event.asEntity())

            // Clear from Store cache only after the DB transaction committed.
            val key = SingleEventKey(event.id)
            singleEventStore.clear(key)

            outbox.enqueueEventDelete(event.id)
        }
}
