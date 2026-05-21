package com.debanshu.xcalendar.data.localDataSource.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Outbox row for offline writes that need to be replayed against the server
 * once connectivity returns (audit F29).
 *
 * Each row captures a pending mutation against the remote API. The local
 * `SourceOfTruth` (Room) has already been updated — the outbox exists so the
 * sync worker can mirror the change upstream without losing it across a
 * process restart or a network outage.
 *
 * @property id Stable autoincrement primary key — also used as ordering when
 *  draining the queue (FIFO).
 * @property entityType Discriminator describing the target table / aggregate
 *  (e.g. "event", "calendar"). See [EntityTypes].
 * @property entityId Domain identifier for the affected row (event id, …).
 *  Carried separately from [payloadJson] so the worker can de-dupe by id.
 * @property operation One of [Operations.CREATE] / [Operations.UPDATE] /
 *  [Operations.DELETE]. Stored as a short string so future ops add easily.
 * @property payloadJson Serialized payload required to replay the op
 *  (`null` for DELETE — the server only needs [entityId]). Format is opaque
 *  to the DAO; the sync worker owns the schema.
 * @property createdAt Wall-clock ms when the op was enqueued. Used for
 *  monitoring + age-based eviction.
 * @property attemptCount Number of replay attempts so far. Worker increments
 *  on every drain attempt; reset to zero on a successful sync (the row is
 *  deleted in that case, so this is only seen for failed entries).
 * @property lastError Most recent failure reason — debug only.
 */
@Entity(tableName = "pending_writes")
data class PendingWriteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payloadJson: String?,
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
) {
    object EntityTypes {
        const val EVENT = "event"
    }

    object Operations {
        const val CREATE = "CREATE"
        const val UPDATE = "UPDATE"
        const val DELETE = "DELETE"
    }
}
