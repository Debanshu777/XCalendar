package com.debanshu.xcalendar.domain.model

import androidx.compose.runtime.Stable

@Stable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val photoUrl: String
)