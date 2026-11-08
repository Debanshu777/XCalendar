package com.debanshu.xcalendar.data.remoteDataSource.model.holiday


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Datetime(
    @SerialName("day")
    val day: Int,
    @SerialName("hour")
    val hour: Int? = 0,
    @SerialName("minute")
    val minute: Int? = 0,
    @SerialName("month")
    val month: Int,
    @SerialName("second")
    val second: Int? = 0,
    @SerialName("year")
    val year: Int
)