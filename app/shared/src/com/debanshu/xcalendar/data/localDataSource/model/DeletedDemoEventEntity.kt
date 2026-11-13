package com.debanshu.xcalendar.data.localDataSource.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_demo_events")
data class DeletedDemoEventEntity(@PrimaryKey val eventId: String)
