package com.debanshu.xcalendar.common

import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass

enum class DeviceConfiguration {
    MOBILE_PORTRAIT,
    MOBILE_LANDSCAPE,
    TABLET_PORTRAIT,
    TABLET_LANDSCAPE,
    DESKTOP,
    ;

    companion object {
        fun fromWindowSizeClass(windowSizeClass: WindowSizeClass): DeviceConfiguration {
            val widthClass = windowSizeClass.windowWidthSizeClass
            val heightClass = windowSizeClass.windowHeightSizeClass

            return when {
                widthClass == WindowWidthSizeClass.COMPACT &&
                    heightClass == WindowHeightSizeClass.MEDIUM -> MOBILE_PORTRAIT
                widthClass == WindowWidthSizeClass.COMPACT &&
                    heightClass == WindowHeightSizeClass.EXPANDED -> MOBILE_PORTRAIT
                widthClass == WindowWidthSizeClass.EXPANDED &&
                    heightClass == WindowHeightSizeClass.COMPACT -> MOBILE_LANDSCAPE
                widthClass == WindowWidthSizeClass.MEDIUM &&
                    heightClass == WindowHeightSizeClass.EXPANDED -> TABLET_PORTRAIT
                widthClass == WindowWidthSizeClass.EXPANDED &&
                    heightClass == WindowHeightSizeClass.MEDIUM -> TABLET_LANDSCAPE
                else -> DESKTOP
            }
        }
    }
}
