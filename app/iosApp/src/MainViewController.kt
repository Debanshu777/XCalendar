package com.debanshu.xcalendar

import androidx.compose.ui.window.ComposeUIViewController
import com.debanshu.xcalendar.di.initKoin

// A view controller may be recreated, and its content may recompose. Initialize
// the application container once, outside either lifecycle.
private val applicationContainer by lazy { initKoin() }

fun MainViewController(): platform.UIKit.UIViewController {
    applicationContainer
    return ComposeUIViewController {
        CalendarApp()
    }
}
