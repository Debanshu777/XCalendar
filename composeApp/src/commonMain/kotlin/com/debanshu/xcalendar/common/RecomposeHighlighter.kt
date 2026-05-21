package com.debanshu.xcalendar.common

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Debug-only Modifier that draws a colored outline whose color/width changes
 * with each recomposition of the composable it's applied to. Useful for
 * visually spotting unnecessary recompositions during Phase 6/7 perf work.
 *
 * Implementation: a `composed { }` block captures a `mutableLongState` and
 * increments it via `SideEffect` (runs after a successful recomposition).
 * The count drives the stroke color, making thrashing composables visible.
 *
 * IMPORTANT: this Modifier itself uses `composed { }` (anti-pattern flagged
 * in F16 of the architecture audit) — it's tolerated here because it's
 * debug-only and the alternative `Modifier.Node` API is verbose. Do NOT
 * apply this in release builds.
 *
 * Usage:
 * ```kotlin
 * if (BuildConfig.DEBUG) { // or platform-equivalent
 *     Box(Modifier.recomposeHighlighter().fillMaxSize()) { ... }
 * }
 * ```
 */
fun Modifier.recomposeHighlighter(): Modifier = composed {
    val recompositionCount = remember { mutableLongStateOf(0L) }
    SideEffect { recompositionCount.value++ }
    val count = recompositionCount.value
    Modifier.drawWithCache {
        onDrawWithContent {
            drawContent()
            val color = when ((count % 6L).toInt()) {
                0 -> Color.Red
                1 -> Color(0xFFFFA500)
                2 -> Color.Yellow
                3 -> Color.Green
                4 -> Color.Blue
                else -> Color.Magenta
            }.copy(alpha = 0.6f)
            val strokeWidth = (1f + (count % 4L).toFloat())
            drawRect(
                color = color,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                style = Stroke(width = strokeWidth),
            )
        }
    }
}
