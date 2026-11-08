package com.debanshu.xcalendar.data.remoteDataSource.model.holiday


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Date(
    @SerialName("datetime")
    val datetime: Datetime,
    @SerialName("iso")
    val iso: String,
    @SerialName("timezone")
    val timezone: Timezone? = null
)