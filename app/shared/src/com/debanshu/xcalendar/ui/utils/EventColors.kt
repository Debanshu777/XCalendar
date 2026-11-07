package com.debanshu.xcalendar.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** Choose the stronger text contrast after painting [background] over its opaque [surface]. */
internal fun eventContentColor(background: Color, surface: Color): Color {
    val luminance = background.compositeOver(surface).luminance()
    val blackContrast = (luminance + 0.05f) / 0.05f
    val whiteContrast = 1.05f / (luminance + 0.05f)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

@Composable
internal fun rememberEventContentColor(background: Color, surface: Color): Color =
    remember(background, surface) { eventContentColor(background, surface) }
