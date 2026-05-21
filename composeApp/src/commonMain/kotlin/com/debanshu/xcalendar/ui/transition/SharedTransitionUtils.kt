package com.debanshu.xcalendar.ui.transition

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.debanshu.xcalendar.ui.theme.LocalSharedTransitionScope
import kotlinx.datetime.LocalDate

/**
 * Shared date element modifier. Only applies shared element behavior during
 * active transitions to avoid the allocation overhead of 126+ composed calls.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedDateElement(
    date: LocalDate,
    type: SharedElementType,
    isVisible: Boolean,
): Modifier = composed {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    if (!sharedTransitionScope.isTransitionActive) return@composed Modifier
    val key = remember(date, type) { DateSharedElementKey(date, type) }
    with(sharedTransitionScope) {
        Modifier.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = isVisible,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedEventElement(
    eventId: String,
    type: SharedElementType,
    isVisible: Boolean,
): Modifier = composed {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    if (!sharedTransitionScope.isTransitionActive) return@composed Modifier
    val key = remember(eventId, type) { EventSharedElementKey(eventId, type) }
    with(sharedTransitionScope) {
        Modifier.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = isVisible,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedTimeColumn(isVisible: Boolean): Modifier = composed {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    if (!sharedTransitionScope.isTransitionActive) return@composed Modifier
    val key = remember { TimeColumnSharedElementKey() }
    with(sharedTransitionScope) {
        Modifier.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = isVisible,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedDayColumn(
    date: LocalDate,
    isVisible: Boolean,
): Modifier = composed {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    if (!sharedTransitionScope.isTransitionActive) return@composed Modifier
    val key = remember(date) { DateSharedElementKey(date, SharedElementType.DayColumn) }
    with(sharedTransitionScope) {
        Modifier.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = isVisible,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun WithSharedTransitionScope(content: @Composable SharedTransitionScope.() -> Unit) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    content(sharedTransitionScope)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun isSharedTransitionActive(): Boolean {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    return sharedTransitionScope.isTransitionActive
}
