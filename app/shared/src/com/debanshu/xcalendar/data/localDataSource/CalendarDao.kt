package com.debanshu.xcalendar.data.localDataSource

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.debanshu.xcalendar.data.localDataSource.model.CalendarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendars WHERE userId = :userId")
    fun getCalendarsByUserId(userId: String): Flow<List<CalendarEntity>>

    @Upsert
    suspend fun upsertCalendar(calendars: List<CalendarEntity>)

    @Delete
    suspend fun deleteCalendar(calendar: CalendarEntity)
}