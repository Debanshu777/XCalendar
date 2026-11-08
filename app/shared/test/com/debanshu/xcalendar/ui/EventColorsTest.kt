package com.debanshu.xcalendar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.debanshu.xcalendar.ui.utils.eventContentColor
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventColorsTest {
    @Test
    fun saturatedCalendarColorsUseReadableForegroundsIndependentlyOfTheme() {
        for (surface in listOf(Color.White, Color.Black)) {
            assertEquals(Color.White, eventContentColor(Color(0xFF8E24AA), surface))
            assertEquals(Color.Black, eventContentColor(Color(0xFFFB8C00), surface))
        }
    }

    @Test
    fun translucentCardsUseTheirPaintedBackgroundRatherThanTheUnblendedEventColor() {
        val translucentPurple = Color(0xFF8E24AA).copy(alpha = 0.15f)
        assertEquals(Color.Black, eventContentColor(translucentPurple, Color.White))
        assertEquals(Color.White, eventContentColor(translucentPurple, Color.Black))
        assertEquals(Color.Black, eventContentColor(Color.Transparent, Color.White))
        assertEquals(Color.White, eventContentColor(Color.Transparent, Color.Black))
    }

    @Test
    fun fixtureCardsAndMidtonesMeetNormalTextContrastOnLightAndDarkSurfaces() {
        val colors = listOf(
            Color(0xFF1E88E5), Color(0xFF00897B), Color(0xFFFB8C00),
            Color(0xFF8E24AA), Color(0xFFE53935), Color(0xFF757575), Color(0xFF777777),
        )
        val surfaces = listOf(Color(0xFFF5F5F5), Color(0xFF1B1B1F), Color(0xFF2B2B30))
        for (surface in surfaces) for (color in colors) for (alpha in listOf(0.15f, 0.7f, 0.9f, 1f)) {
            val background = color.copy(alpha = alpha)
            val foreground = eventContentColor(background, surface)
            val backgroundLuminance = background.compositeOver(surface).luminance()
            val foregroundLuminance = foreground.luminance()
            val contrast = (max(backgroundLuminance, foregroundLuminance) + 0.05f) /
                (min(backgroundLuminance, foregroundLuminance) + 0.05f)
            assertTrue(contrast >= 4.5f, "Contrast $contrast for $color at alpha $alpha over $surface")
        }
    }
}
