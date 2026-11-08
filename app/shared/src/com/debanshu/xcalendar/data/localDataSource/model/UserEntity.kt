package com.debanshu.xcalendar.data.localDataSource.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index("id", unique = true),
        Index("email", unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val photoUrl: String
)