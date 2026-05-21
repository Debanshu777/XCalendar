package com.debanshu.xcalendar.data.outbox

import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.common.ioDispatcher
import com.debanshu.xcalendar.data.localDataSource.PendingWriteDao
import com.debanshu.xcalendar.data.localDataSource.model.PendingWriteEntity
import kotlinx.coroutines.withContext

/**
 * Drains queued mutations from the outbox once network is available
 * (audit F29).
 *
 * The current build still talks to a static JSON snapshot in the remote API
 * (`RemoteCalendarApiService` reads `assets/<name>.json`), so no real backend
 * accepts writes yet. The default [OutboxSender] binding ([NoopOutboxSender])
 * returns `false`, so rows queue up but are never deleted. When a real write
 * backend lands, swap the binding for one that issues the actual POST/PUT/
 * DELETE — [drain] will then delete each row on a successful send.
 *
 * Thread model: `drain` is suspend and hops to [ioDispatcher] for DB work.
 * Caller (e.g. WorkManager / iOS BGTaskScheduler / desktop scheduler) owns
 * the trigger policy.
 */
class OutboxDrainer(
    private val dao: PendingWriteDao,
    private val sender: OutboxSender,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    /**
     * Pop and send up to [batchSize] queued rows.
     *
     * Returns the [DrainReport] so the trigger can decide whether to retry
     * immediately (rows remain) or back off.
     */
    suspend fun drain(): DrainReport = withContext(ioDispatcher) {
        val batch = dao.peekBatch(batchSize)
        if (batch.isEmpty()) {
            return@withContext DrainReport(processed = 0, succeeded = 0, remaining = 0)
        }

        var succeeded = 0
        batch.forEach { row ->
            val outcome = runCatching { sender.send(row) }
            outcome.fold(
                onSuccess = { success ->
                    if (success) {
                        dao.deleteById(row.id)
                        succeeded++
                    } else {
                        dao.recordAttempt(row.id, "Sender returned not-yet-syncable")
                    }
                },
                onFailure = { err ->
                    AppLogger.e(err) {
                        "Outbox: send failed for id=${row.id} type=${row.entityType} op=${row.operation}"
                    }
                    dao.recordAttempt(row.id, err.message)
                },
            )
        }

        DrainReport(
            processed = batch.size,
            succeeded = succeeded,
            remaining = dao.count(),
        )
    }

    data class DrainReport(
        val processed: Int,
        val succeeded: Int,
        val remaining: Int,
    )

    companion object {
        const val DEFAULT_BATCH_SIZE = 20
    }
}

/**
 * Pluggable backend that replays a single outbox row.
 *
 * Implementations return:
 * - `true` — server accepted the write; row will be deleted.
 * - `false` — write is not yet syncable (e.g. backend disabled in this
 *   build); row is left in the queue and attempt count is bumped.
 * - throws — transient failure; same as `false` plus error is logged.
 *
 * Default binding ([NoopOutboxSender]) returns `false` so rows accumulate
 * safely until a real backend ships.
 */
interface OutboxSender {
    suspend fun send(row: PendingWriteEntity): Boolean
}

/**
 * Placeholder sender for the offline-only build (audit F29 follow-up).
 *
 * Never marks rows as sent — keeps the queue intact so that when a real
 * backend arrives, accumulated history isn't lost.
 */
class NoopOutboxSender : OutboxSender {
    override suspend fun send(row: PendingWriteEntity): Boolean {
        AppLogger.d {
            "NoopOutboxSender: skipping replay for id=${row.id} (no backend wired)"
        }
        return false
    }
}
