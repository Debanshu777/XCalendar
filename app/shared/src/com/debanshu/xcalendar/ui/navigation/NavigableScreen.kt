package com.debanshu.xcalendar.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface NavigableScreen : NavKey {
    @Serializable
    data object Schedule : NavigableScreen

    @Serializable
    data object Day : NavigableScreen

    @Serializable
    data object ThreeDay : NavigableScreen

    @Serializable
    data object Week : NavigableScreen

    @Serializable
    data object Month : NavigableScreen
}
