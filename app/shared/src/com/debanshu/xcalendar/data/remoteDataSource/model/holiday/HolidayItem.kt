package com.debanshu.xcalendar.data.remoteDataSource.model.holiday


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HolidayItem(
    @SerialName("canonical_url")
    val canonicalUrl: String,
    @SerialName("country")
    val country: Country,
    @SerialName("date")
    val date: Date,
    @SerialName("description")
    val description: String,
    @SerialName("locations")
    val locations: String,
    @SerialName("name")
    val name: String,
    @SerialName("primary_type")
    val primaryType: String,
    @SerialName("states")
    val states: String,
    @SerialName("type")
    val type: List<String>,
    @SerialName("urlid")
    val urlid: String
)