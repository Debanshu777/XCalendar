package com.debanshu.xcalendar.data.remoteDataSource.model.holiday


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Timezone(
    @SerialName("offset")
    val offset: String,
    @SerialName("zoneabb")
    val zoneabb: String,
    @SerialName("zonedst")
    val zonedst: Int,
    @SerialName("zoneoffset")
    val zoneoffset: Int,
    @SerialName("zonetotaloffset")
    val zonetotaloffset: Int
)