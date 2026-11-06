package com.debanshu.xcalendar.data.remoteDataSource.model.calendar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CalendarResponseItem(
    @SerialName("id")
    val id: String,
    @SerialName("isPrimary")
    val isPrimary: Boolean,
    @SerialName("isVisible")
    val isVisible: Boolean,
    @SerialName("name")
    val name: String,
    @SerialName("userId")
    val userId: String
)
