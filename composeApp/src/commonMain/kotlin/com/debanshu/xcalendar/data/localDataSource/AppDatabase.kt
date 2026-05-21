package com.debanshu.xcalendar.data.localDataSource

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.debanshu.xcalendar.data.localDataSource.model.CalendarEntity
import com.debanshu.xcalendar.data.localDataSource.model.EventEntity
import com.debanshu.xcalendar.data.localDataSource.model.EventReminderEntity
import com.debanshu.xcalendar.data.localDataSource.model.HolidayEntity
import com.debanshu.xcalendar.data.localDataSource.model.PendingWriteEntity
import com.debanshu.xcalendar.data.localDataSource.model.SyncFailureEntity
import com.debanshu.xcalendar.data.localDataSource.model.UserEntity

/**
 * Current database version.
 * Increment this when making schema changes and add a migration.
 *
 * Migration policy (audit F30):
 * 1. Bump [DATABASE_VERSION].
 * 2. Add a new `MIGRATION_<old>_<new>` to [AppDatabase.MIGRATIONS] — every
 *    bump MUST have a matching migration object, no `fallbackToDestructive…`
 *    in production.
 * 3. Update the per-target unit tests that assert
 *    `MIGRATIONS.size == DATABASE_VERSION - 1` so a future bump without a
 *    migration fails CI.
 */
const val DATABASE_VERSION = 2

const val DATABASE_NAME = "xcalendar.db"

@Database(
    entities = [
        UserEntity::class,
        CalendarEntity::class,
        EventEntity::class,
        EventReminderEntity::class,
        HolidayEntity::class,
        SyncFailureEntity::class,
        PendingWriteEntity::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true,
)
@ConstructedBy(LocalDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getUserEntityDao(): UserDao

    abstract fun getCalendarEntityDao(): CalendarDao

    abstract fun getEventEntityDao(): EventDao

    abstract fun getHolidayEntityDao(): HolidayDao

    abstract fun getSyncFailureDao(): SyncFailureDao

    abstract fun getPendingWriteDao(): PendingWriteDao

    companion object {
        /**
         * v1 → v2: introduces the `pending_writes` outbox table (audit F29).
         * No data backfill — the outbox is empty on first launch after the
         * upgrade; pre-existing local-only writes from the v1 era cannot be
         * recovered because they were never recorded.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_writes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `payloadJson` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `attemptCount` INTEGER NOT NULL DEFAULT 0,
                        `lastError` TEXT
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Array of all migrations.
         * Add new migrations here when incrementing [DATABASE_VERSION].
         * Order is irrelevant; Room dispatches by `(startVersion, endVersion)`.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
        )
    }
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LocalDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
