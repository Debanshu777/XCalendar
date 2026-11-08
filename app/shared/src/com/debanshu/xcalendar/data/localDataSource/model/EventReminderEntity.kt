package com.debanshu.xcalendar.data.localDataSource.model

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "event_reminders",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    primaryKeys = ["eventId", "minutes"]
)
data class EventReminderEntity(
    val eventId: String,
    val minutes: Int
)