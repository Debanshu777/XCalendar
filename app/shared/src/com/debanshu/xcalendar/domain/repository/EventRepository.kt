@file:OptIn(ExperimentalStoreApi::class)

package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.data.store.EventKey
import com.debanshu.xcalendar.data.store.SingleEventKey
import com.debanshu.xcalendar.domain.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.mobilenativefoundation.store.core5.ExperimentalStoreApi
import org.mobilenativefoundation.store.store5.MutableStore
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.mobilenativefoundation.store.store5.StoreWriteRequest
import org.mobilenativefoundation.store.store5.StoreWriteResponse

/**
 * Reads Room-backed demo event ranges and persists individual edits through Store5.
 */
@Single(binds = [IEventRepository::class])
class EventRepository(
    @Named("eventStore") private val eventStore: Store<EventKey, List<Event>>,
    @Named("singleEventStore") private val singleEventStore: MutableStore<SingleEventKey, Event>,
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
                .stream(StoreReadRequest.fresh(key))
                .onEach { it.throwIfError() }
                .filterIsInstance<StoreReadResponse.Data<List<Event>>>()
                .first()
            Unit
        }

    /** Observes cached events in a date range. Seed refresh is requested separately. */
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
                    .stream(StoreReadRequest.cached(key, refresh = false))
                    .filterIsInstance<StoreReadResponse.Data<List<Event>>>()
                    .map { it.value },
        )
    }

    @OptIn(ExperimentalStoreApi::class)
    override suspend fun addEvent(event: Event): Unit =
        safeCallOrThrow("addEvent(${event.id})") {
            val key = SingleEventKey(event.id)
            singleEventStore.write(StoreWriteRequest.of(key, event)).requireWriteSuccess()
        }

    @OptIn(ExperimentalStoreApi::class)
    override suspend fun updateEvent(event: Event): Unit =
        safeCallOrThrow("updateEvent(${event.id})") {
            val key = SingleEventKey(event.id)
            singleEventStore.write(StoreWriteRequest.of(key, event)).requireWriteSuccess()
        }

    @OptIn(ExperimentalStoreApi::class)
    override suspend fun deleteEvent(event: Event): Unit =
        safeCallOrThrow(
            "deleteEvent(${event.id})",
        ) {
            // Store5 invalidates the cached value and calls the Room source of truth.
            singleEventStore.clear(SingleEventKey(event.id))
        }
}

/** Store reports local write failures as values, including wrapped cancellation. */
internal fun StoreWriteResponse.requireWriteSuccess(): Unit = when (this) {
    is StoreWriteResponse.Success -> Unit
    is StoreWriteResponse.Error.Exception -> throw error
    is StoreWriteResponse.Error.Message -> throw RepositoryException(message)
}
