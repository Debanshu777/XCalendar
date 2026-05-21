package com.debanshu.xcalendar.data.localDataSource

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.debanshu.xcalendar.data.localDataSource.model.PendingWriteEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the offline-write outbox (audit F29).
 *
 * The outbox is FIFO: newest rows have the largest [PendingWriteEntity.id].
 * `peekBatch` returns up to [limit] oldest rows so a worker can drain them
 * in order. `deleteById` removes a row once the server has accepted the op.
 */
@Dao
interface PendingWriteDao {

    @Insert
    suspend fun insert(pending: PendingWriteEntity): Long

    @Query("SELECT * FROM pending_writes ORDER BY id ASC LIMIT :limit")
    suspend fun peekBatch(limit: Int): List<PendingWriteEntity>

    @Query("SELECT * FROM pending_writes ORDER BY id ASC")
    fun observeAll(): Flow<List<PendingWriteEntity>>

    @Query("SELECT COUNT(*) FROM pending_writes")
    suspend fun count(): Int

    @Query("DELETE FROM pending_writes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        UPDATE pending_writes
        SET attemptCount = attemptCount + 1,
            lastError = :error
        WHERE id = :id
        """,
    )
    suspend fun recordAttempt(id: Long, error: String?)

    @Query("DELETE FROM pending_writes")
    suspend fun deleteAll()
}
