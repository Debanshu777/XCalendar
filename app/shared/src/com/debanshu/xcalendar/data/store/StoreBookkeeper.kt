package com.debanshu.xcalendar.data.store

import kotlinx.coroutines.CancellationException
import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.data.localDataSource.SyncFailureDao
import com.debanshu.xcalendar.data.localDataSource.model.SyncFailureEntity
import org.mobilenativefoundation.store.store5.Bookkeeper

private const val SINGLE_EVENT_KEY = "SINGLE_EVENT_KEY"

/**
 * Creates a Bookkeeper for SingleEventKey that tracks failed sync operations.
 */
object SingleEventBookkeeperFactory {
    fun create(syncFailureDao: SyncFailureDao): Bookkeeper<SingleEventKey> =
        Bookkeeper.by(
            getLastFailedSync = { key ->
                val failure = syncFailureDao.getFailure(key.eventId)
                failure?.timestamp
            },
            setLastFailedSync = { key, timestamp ->
                try {
                    val existing = syncFailureDao.getFailure(key.eventId)
                    if (existing != null) {
                        syncFailureDao.incrementFailureCount(key.eventId, timestamp, null)
                    } else {
                        syncFailureDao.insertFailure(
                            SyncFailureEntity(
                                key = key.eventId,
                                keyType = SINGLE_EVENT_KEY,
                                timestamp = timestamp,
                                failureCount = 1,
                                lastErrorMessage = null,
                            ),
                        )
                    }
                    AppLogger.d { "Recorded sync failure for SingleEventKey: ${key.eventId}" }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e { "Failed to record sync failure: ${e.message}" }
                    false
                }
            },
            clear = { key ->
                try {
                    syncFailureDao.deleteFailure(key.eventId)
                    AppLogger.d { "Cleared sync failure for SingleEventKey: ${key.eventId}" }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e { "Failed to clear sync failure: ${e.message}" }
                    false
                }
            },
            clearAll = {
                try {
                    syncFailureDao.deleteFailuresByType(SINGLE_EVENT_KEY)
                    AppLogger.d { "Cleared all SingleEventKey sync failures" }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e { "Failed to clear all sync failures: ${e.message}" }
                    false
                }
            },
        )
}
