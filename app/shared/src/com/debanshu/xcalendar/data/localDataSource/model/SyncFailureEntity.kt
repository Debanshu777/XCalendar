package com.debanshu.xcalendar.data.localDataSource.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for tracking failed sync operations.
 * Used by the Bookkeeper to persist sync failures for later retry.
 */
@Entity(tableName = "sync_failures")
data class SyncFailureEntity(
    @PrimaryKey
    val key: String,
    val keyType: String, // "event" or "event_item" or "holiday"
    val timestamp: Long,
    val failureCount: Int = 1,
    val lastErrorMessage: String? = null
)
