package com.debanshu.xcalendar

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.debanshu.xcalendar.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "XCalendar",
        ) {
            App()
        }
    }
}
