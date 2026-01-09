package com.debanshu.xcalendar.data.localDataSource

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import com.debanshu.xcalendar.data.localDataSource.model.CalendarEntity
import com.debanshu.xcalendar.data.localDataSource.model.EventEntity
import com.debanshu.xcalendar.data.localDataSource.model.EventReminderEntity
import com.debanshu.xcalendar.data.localDataSource.model.HolidayEntity
import com.debanshu.xcalendar.data.localDataSource.model.SyncFailureEntity
import com.debanshu.xcalendar.data.localDataSource.model.UserEntity

/**
 * Current database version.
 * Increment this when making schema changes and add a migration.
 */
const val DATABASE_VERSION = 1

const val DATABASE_NAME = "xcalendar.db"

@Database(
    entities = [
        UserEntity::class,
        CalendarEntity::class,
        EventEntity::class,
        EventReminderEntity::class,
        HolidayEntity::class,
        SyncFailureEntity::class,
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

    companion object {
        /**
         * Array of all migrations.
         * Add new migrations here when incrementing DATABASE_VERSION.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // Add migrations here as needed, e.g.:
            // MIGRATION_1_2,
        )
    }
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LocalDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
