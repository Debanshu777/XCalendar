package com.debanshu.xcalendar.data.localDataSource.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey val id: String,
    val name: String,
    val date: Long,
    val countryCode: String
)