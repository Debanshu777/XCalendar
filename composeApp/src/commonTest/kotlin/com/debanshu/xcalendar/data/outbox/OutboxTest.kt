package com.debanshu.xcalendar.data.outbox

import com.debanshu.xcalendar.data.localDataSource.PendingWriteDao
import com.debanshu.xcalendar.data.localDataSource.model.PendingWriteEntity
import com.debanshu.xcalendar.test.TestDataFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 3 / F29 verification.
 *
 * The outbox is the load-bearing piece of the offline-write story: without
 * it, [com.debanshu.xcalendar.domain.repository.EventRepository] lies to the
 * user about save success (the Updater currently no-ops). These tests pin
 * the contract the upcoming sync worker depends on:
 *
 * 1. Every CRUD repository call results in exactly one outbox row.
 * 2. Rows carry enough payload to replay later (CREATE/UPDATE serialise the
 *    full event; DELETE only needs the id).
 * 3. The drainer processes rows in FIFO order, deletes on `send -> true`,
 *    and increments `attemptCount` on `send -> false` / throw without
 *    losing the row.
 */
class OutboxTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `enqueueEventCreate inserts a CREATE row with serialised payload`() = runTest {
        val dao = FakePendingWriteDao()
        val writer = OutboxWriter(dao, json)
        val event = TestDataFactory.createEvent(id = "evt-1", title = "Standup")

        val id = writer.enqueueEventCreate(event)

        assertEquals(1, dao.count())
        val row = dao.peekBatch(10).single()
        assertEquals(id, row.id)
        assertEquals(PendingWriteEntity.EntityTypes.EVENT, row.entityType)
        assertEquals(PendingWriteEntity.Operations.CREATE, row.operation)
        assertEquals("evt-1", row.entityId)
        assertNotNull(row.payloadJson, "CREATE payload must round-trip the event")
        assertTrue(row.payloadJson.contains("Standup"), "Payload should embed the event title")
    }

    @Test
    fun `enqueueEventUpdate inserts an UPDATE row`() = runTest {
        val dao = FakePendingWriteDao()
        val writer = OutboxWriter(dao, json)
        val event = TestDataFactory.createEvent(id = "evt-2")

        writer.enqueueEventUpdate(event)

        val row = dao.peekBatch(10).single()
        assertEquals(PendingWriteEntity.Operations.UPDATE, row.operation)
        assertNotNull(row.payloadJson)
    }

    @Test
    fun `enqueueEventDelete inserts a DELETE row with no payload`() = runTest {
        val dao = FakePendingWriteDao()
        val writer = OutboxWriter(dao, json)

        writer.enqueueEventDelete("evt-3")

        val row = dao.peekBatch(10).single()
        assertEquals(PendingWriteEntity.Operations.DELETE, row.operation)
        assertEquals("evt-3", row.entityId)
        assertNull(row.payloadJson, "DELETE doesn't need a payload; the id is enough")
    }

    @Test
    fun `drain deletes rows the sender accepts and keeps the rest`() = runTest {
        val dao = FakePendingWriteDao()
        val writer = OutboxWriter(dao, json)
        repeat(3) { i ->
            writer.enqueueEventCreate(TestDataFactory.createEvent(id = "evt-$i"))
        }

        // Sender accepts evt-0 and evt-2 but rejects evt-1 (e.g. validation failure).
        val sender = ScriptedSender { row ->
            when (row.entityId) {
                "evt-0", "evt-2" -> SendResult.Success
                else -> SendResult.NotSyncable
            }
        }
        val drainer = OutboxDrainer(dao, sender)

        val report = drainer.drain()

        assertEquals(3, report.processed)
        assertEquals(2, report.succeeded)
        assertEquals(1, report.remaining)
        val remaining = dao.peekBatch(10)
        assertEquals(1, remaining.size)
        assertEquals("evt-1", remaining.single().entityId)
        assertEquals(
            1,
            remaining.single().attemptCount,
            "Non-syncable row should have its attempt count bumped, not be deleted",
        )
    }

    @Test
    fun `drain records errors when sender throws but keeps the row`() = runTest {
        val dao = FakePendingWriteDao()
        val writer = OutboxWriter(dao, json)
        writer.enqueueEventCreate(TestDataFactory.createEvent(id = "evt-x"))

        val sender = ScriptedSender {
            SendResult.Throw(RuntimeException("network down"))
        }
        val drainer = OutboxDrainer(dao, sender)

        val report = drainer.drain()

        assertEquals(1, report.processed)
        assertEquals(0, report.succeeded)
        assertEquals(1, report.remaining)
        val row = dao.peekBatch(10).single()
        assertEquals(1, row.attemptCount)
        assertEquals("network down", row.lastError)
    }

    @Test
    fun `NoopOutboxSender never marks rows synced`() = runTest {
        val dao = FakePendingWriteDao()
        val writer = OutboxWriter(dao, json)
        writer.enqueueEventCreate(TestDataFactory.createEvent(id = "evt-noop"))
        val drainer = OutboxDrainer(dao, NoopOutboxSender())

        val report = drainer.drain()

        assertEquals(0, report.succeeded)
        assertEquals(1, report.remaining)
    }
}

/**
 * In-memory [PendingWriteDao]. The real Room DAO is exercised by macro / iOS
 * builds; here we only need behaviour, not SQL semantics.
 */
private class FakePendingWriteDao : PendingWriteDao {
    private var nextId = 1L
    private val rows = mutableListOf<PendingWriteEntity>()
    private val flow = MutableStateFlow<List<PendingWriteEntity>>(emptyList())

    override suspend fun insert(pending: PendingWriteEntity): Long {
        val assigned = pending.copy(id = nextId++)
        rows.add(assigned)
        flow.value = rows.toList()
        return assigned.id
    }

    override suspend fun peekBatch(limit: Int): List<PendingWriteEntity> =
        rows.sortedBy { it.id }.take(limit)

    override fun observeAll(): Flow<List<PendingWriteEntity>> = flow

    override suspend fun count(): Int = rows.size

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
        flow.value = rows.toList()
    }

    override suspend fun recordAttempt(id: Long, error: String?) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) {
            rows[idx] = rows[idx].copy(
                attemptCount = rows[idx].attemptCount + 1,
                lastError = error,
            )
            flow.value = rows.toList()
        }
    }

    override suspend fun deleteAll() {
        rows.clear()
        flow.value = emptyList()
    }
}

private sealed interface SendResult {
    data object Success : SendResult
    data object NotSyncable : SendResult
    data class Throw(val cause: Throwable) : SendResult
}

private class ScriptedSender(
    private val script: (PendingWriteEntity) -> SendResult,
) : OutboxSender {
    override suspend fun send(row: PendingWriteEntity): Boolean =
        when (val result = script(row)) {
            SendResult.Success -> true
            SendResult.NotSyncable -> false
            is SendResult.Throw -> throw result.cause
        }
}
