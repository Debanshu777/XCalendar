package com.debanshu.xcalendar.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier

/**
 * Ripple-free clickable. Caller must pass a stable [interactionSource] (typically
 * `remember { MutableInteractionSource() }` at composable scope) so sources are
 * not recreated on every recomposition.
 */
fun Modifier.noRippleClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier =
    this.clickable(
        indication = null,
        interactionSource = interactionSource,
        onClick = onClick,
    )
