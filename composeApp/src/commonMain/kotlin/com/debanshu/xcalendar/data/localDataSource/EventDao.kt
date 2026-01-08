package com.debanshu.xcalendar.data.localDataSource

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.debanshu.xcalendar.data.localDataSource.model.EventEntity
import com.debanshu.xcalendar.data.localDataSource.model.EventReminderEntity
import com.debanshu.xcalendar.data.localDataSource.model.EventWithReminders
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events " +
            "INNER JOIN calendars ON events.calendarId = calendars.id "+
            "WHERE calendars.userId = :userId AND startTime >= :startTime AND endTime <= :endTime")
    fun getEventsBetweenDates(userId: String, startTime: Long, endTime: Long): Flow<List<EventEntity>>

    @Transaction
    @Query("SELECT events.* FROM events " +
            "INNER JOIN calendars ON events.calendarId = calendars.id " +
            "WHERE calendars.userId = :userId AND startTime >= :startTime AND endTime <= :endTime")
    fun getEventsWithRemindersBetweenDates(userId: String, startTime: Long, endTime: Long): Flow<List<EventWithReminders>>

    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getEventById(eventId: String): EventEntity?

    /**
     * Gets a single event with its reminders.
     * Use this instead of getEventById when you need reminder data.
     */
    @Transaction
    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getEventWithRemindersById(eventId: String): EventWithReminders?

    @Upsert
    suspend fun upsertEvent(event: EventEntity): Long

    @Delete
    suspend fun deleteEvent(event: EventEntity)

    @Transaction
    suspend fun insertEventWithReminders(event: EventEntity, reminders: List<EventReminderEntity>) {
        upsertEvent(event)
        // Delete existing reminders first to prevent orphans when reminders change
        deleteEventReminders(event.id)
        reminders.forEach { reminder ->
            insertEventReminder(reminder)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEventReminder(reminder: EventReminderEntity)

    @Query("DELETE FROM event_reminders WHERE eventId = :eventId")
    suspend fun deleteEventReminders(eventId: String)
}