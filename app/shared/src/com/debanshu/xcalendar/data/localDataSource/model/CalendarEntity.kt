package com.debanshu.xcalendar.data.localDataSource.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendars",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("id", unique = true),
        Index("userId")
    ]
)
data class CalendarEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    val userId: String,
    val isVisible: Boolean,
    val isPrimary: Boolean
)