package com.debanshu.xcalendar.ui.theme

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.materialkolor.rememberDynamicColorScheme

val LocalSharedTransitionScope =
    compositionLocalOf<SharedTransitionScope> {
        throw IllegalStateException("No SharedTransitionScope provided")
    }

/**
 * Main theme composable for the XCalendar app
 *
 * @param darkTheme Whether to use dark theme, defaults to system setting
 * @param content The content to be themed
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XCalendarTheme(
    shapes: Shapes = XCalendarTheme.shapes,
    typography: Typography = XCalendarTheme.typography,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme =
        rememberDynamicColorScheme(
            Color(0xFF4285F4),
            useDarkTheme,
            isAmoled = true,
        )
    CompositionLocalProvider {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = {
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        content()
                    }
                }
            },
            motionScheme = MotionScheme.expressive(),
        )
    }
}

object XCalendarTheme {
    val dimensions: Dimensions
        @Composable @ReadOnlyComposable
        get() = Dimensions

    val colorScheme: ColorScheme
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val typography: Typography
        @Composable @ReadOnlyComposable
        get() = Typography

    val shapes: Shapes
        @Composable @ReadOnlyComposable
        get() = AppShapes

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val motion: MotionScheme
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.motionScheme
}
