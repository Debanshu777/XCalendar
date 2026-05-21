@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.debanshu.xcalendar.data.outbox

import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.data.localDataSource.PendingWriteDao
import com.debanshu.xcalendar.data.localDataSource.model.PendingWriteEntity
import com.debanshu.xcalendar.domain.model.Event
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Append-only writer for the offline outbox (audit F29).
 *
 * Repositories call into this *after* they have persisted the change locally
 * via the `SourceOfTruth`. Each `enqueue*` records the mutation so that the
 * eventual remote backend can replay it once a real `Updater` exists.
 *
 * This intentionally has no knowledge of network state. Drain logic lives in
 * [OutboxDrainer].
 */
class OutboxWriter(
    private val dao: PendingWriteDao,
    private val json: Json,
) {
    suspend fun enqueueEventCreate(event: Event): Long =
        enqueueEvent(event, PendingWriteEntity.Operations.CREATE)

    suspend fun enqueueEventUpdate(event: Event): Long =
        enqueueEvent(event, PendingWriteEntity.Operations.UPDATE)

    suspend fun enqueueEventDelete(eventId: String): Long {
        val row = PendingWriteEntity(
            entityType = PendingWriteEntity.EntityTypes.EVENT,
            entityId = eventId,
            operation = PendingWriteEntity.Operations.DELETE,
            payloadJson = null,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        val id = dao.insert(row)
        AppLogger.d { "Outbox: enqueued DELETE event=$eventId id=$id" }
        return id
    }

    private suspend fun enqueueEvent(event: Event, op: String): Long {
        val payload = json.encodeToString(EventOutboxPayload.from(event))
        val row = PendingWriteEntity(
            entityType = PendingWriteEntity.EntityTypes.EVENT,
            entityId = event.id,
            operation = op,
            payloadJson = payload,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        val id = dao.insert(row)
        AppLogger.d { "Outbox: enqueued $op event=${event.id} id=$id" }
        return id
    }
}

/**
 * Serializable shape stored in `pending_writes.payloadJson`. Drops the
 * derived `color` field — it's recomputed from `calendarId + calendarName`
 * (see [com.debanshu.xcalendar.common.model.eventExtension]) — so the
 * outbox stays decoupled from UI palette logic.
 */
@Serializable
internal data class EventOutboxPayload(
    val id: String,
    val calendarId: String,
    val calendarName: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean = false,
    val isRecurring: Boolean = false,
    val recurringRule: String? = null,
    val reminderMinutes: List<Int> = emptyList(),
) {
    companion object {
        fun from(event: Event) = EventOutboxPayload(
            id = event.id,
            calendarId = event.calendarId,
            calendarName = event.calendarName,
            title = event.title,
            description = event.description,
            location = event.location,
            startTime = event.startTime,
            endTime = event.endTime,
            isAllDay = event.isAllDay,
            isRecurring = event.isRecurring,
            recurringRule = event.recurringRule,
            reminderMinutes = event.reminderMinutes,
        )
    }
}
