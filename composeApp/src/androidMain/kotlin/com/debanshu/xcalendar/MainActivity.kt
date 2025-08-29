package com.debanshu.xcalendar

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private var isDarkMode = false
    private val transparentBarStyle =
        SystemBarStyle.auto(
            lightScrim = Color.TRANSPARENT,
            darkScrim = Color.TRANSPARENT,
            detectDarkMode = { isDarkMode },
        )

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (
            isDarkMode != newConfig.isNightModeActive
        ) {
            isDarkMode =
                newConfig.isNightModeActive
            enableEdgeToEdge(transparentBarStyle, transparentBarStyle)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDarkMode = resources.configuration.isNightModeActive
        enableEdgeToEdge(transparentBarStyle, transparentBarStyle)
        setContent {
            App()
        }
    }
}
