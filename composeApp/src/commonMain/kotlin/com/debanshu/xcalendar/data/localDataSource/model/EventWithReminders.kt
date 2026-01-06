package com.debanshu.xcalendar.data.localDataSource.model

import androidx.room.Embedded
import androidx.room.Relation

data class EventWithReminders(
    @Embedded val event: EventEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "eventId",
    )
    val reminders: List<EventReminderEntity>,
)
