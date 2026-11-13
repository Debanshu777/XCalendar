package com.debanshu.xcalendar.data.google

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** An atomic, account-scoped server snapshot. Contains calendar data, never OAuth tokens. */
@Entity(tableName = "google_calendar_cache")
internal data class GoogleCacheEntity(@PrimaryKey val accountId: String, val snapshot: String)

@Dao
internal interface GoogleCacheDao {
    @Query("SELECT * FROM google_calendar_cache WHERE accountId = :accountId")
    suspend fun read(accountId: String): GoogleCacheEntity?
    @Upsert
    suspend fun write(entity: GoogleCacheEntity)
    @Query("DELETE FROM google_calendar_cache WHERE accountId = :accountId")
    suspend fun delete(accountId: String)

    @Query("DELETE FROM google_calendar_cache WHERE accountId NOT IN (:accountIds)")
    suspend fun deleteUnowned(accountIds: List<String>)
}
