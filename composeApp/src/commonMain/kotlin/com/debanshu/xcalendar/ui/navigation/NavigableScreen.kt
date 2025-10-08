package com.debanshu.xcalendar.ui.navigation

sealed class NavigableScreen {
    data object Schedule : NavigableScreen()

    data object Day : NavigableScreen()

    data object ThreeDay : NavigableScreen()

    data object Week : NavigableScreen()

    data object Month : NavigableScreen()
}
