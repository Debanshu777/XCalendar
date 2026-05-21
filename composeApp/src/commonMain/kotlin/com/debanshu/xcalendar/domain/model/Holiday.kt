package com.debanshu.xcalendar.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Holiday(
    val id: String,
    val name: String,
    val date: Long,
    val countryCode: String,
)
