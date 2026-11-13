package com.debanshu.xcalendar.data.localDataSource

import com.debanshu.xcalendar.data.google.GoogleCacheDao
import com.debanshu.xcalendar.data.google.GoogleCacheEntity
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.debanshu.xcalendar.data.localDataSource.model.CalendarEntity
import com.debanshu.xcalendar.data.localDataSource.model.DeletedDemoEventEntity
import com.debanshu.xcalendar.data.localDataSource.model.EventEntity
import com.debanshu.xcalendar.data.localDataSource.model.EventReminderEntity
import com.debanshu.xcalendar.data.localDataSource.model.HolidayEntity
import com.debanshu.xcalendar.data.localDataSource.model.SyncFailureEntity
import com.debanshu.xcalendar.data.localDataSource.model.UserEntity

/**
 * Current database version.
 * Development builds recreate the database on schema changes.
 */
const val DATABASE_VERSION = 3

const val DATABASE_NAME = "xcalendar.db"

@Database(
    entities = [
        GoogleCacheEntity::class,
        DeletedDemoEventEntity::class,
        UserEntity::class,
        CalendarEntity::class,
        EventEntity::class,
        EventReminderEntity::class,
        HolidayEntity::class,
        SyncFailureEntity::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = false,
)
@ConstructedBy(LocalDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    internal abstract fun googleCacheDao(): GoogleCacheDao

    abstract fun getUserEntityDao(): UserDao

    abstract fun getCalendarEntityDao(): CalendarDao

    abstract fun getEventEntityDao(): EventDao

    abstract fun getHolidayEntityDao(): HolidayDao

    abstract fun getSyncFailureDao(): SyncFailureDao


}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LocalDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
