package com.debanshu.xcalendar.data.localDataSource

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.debanshu.xcalendar.data.localDataSource.model.SyncFailureEntity

@Dao
interface SyncFailureDao {
    
    @Query("SELECT * FROM sync_failures WHERE `key` = :key")
    suspend fun getFailure(key: String): SyncFailureEntity?
    
    @Query("SELECT * FROM sync_failures WHERE keyType = :keyType")
    suspend fun getFailuresByType(keyType: String): List<SyncFailureEntity>
    
    @Query("SELECT * FROM sync_failures")
    suspend fun getAllFailures(): List<SyncFailureEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFailure(failure: SyncFailureEntity)
    
    @Query("DELETE FROM sync_failures WHERE `key` = :key")
    suspend fun deleteFailure(key: String)
    
    @Query("DELETE FROM sync_failures WHERE keyType = :keyType")
    suspend fun deleteFailuresByType(keyType: String)
    
    @Query("DELETE FROM sync_failures")
    suspend fun deleteAllFailures()
    
    @Query("UPDATE sync_failures SET failureCount = failureCount + 1, timestamp = :timestamp, lastErrorMessage = :errorMessage WHERE `key` = :key")
    suspend fun incrementFailureCount(key: String, timestamp: Long, errorMessage: String?)
}
