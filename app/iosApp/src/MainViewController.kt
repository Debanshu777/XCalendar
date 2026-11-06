package com.debanshu.xcalendar

import androidx.compose.ui.window.ComposeUIViewController
import com.debanshu.xcalendar.di.initKoin

fun MainViewController() =
    ComposeUIViewController {
        initKoin()
        CalendarApp()
    }
