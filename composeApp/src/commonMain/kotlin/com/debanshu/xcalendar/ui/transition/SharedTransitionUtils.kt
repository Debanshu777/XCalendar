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
 * Extension function to apply shared date element modifier.
 * Used for date cells/headers that transition between screens.
 *
 * @param date The date this element represents
 * @param type The type of shared element
 * @param isVisible Whether this element should be visible (based on current screen)
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedDateElement(
    date: LocalDate,
    type: SharedElementType,
    isVisible: Boolean,
): Modifier = composed {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val key = remember(date, type) { DateSharedElementKey(date, type) }

    with(sharedTransitionScope) {
        this@composed.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = isVisible,
        )
    }
}

/**
 * Extension function to apply shared event element modifier.
 * Used for event cards that transition between calendar views and EventDetailsDialog.
 *
 * @param eventId The unique identifier of the event
 * @param type The type of shared element
 * @param isVisible Whether this element should be visible
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedEventElement(
    eventId: String,
    type: SharedElementType,
    isVisible: Boolean,
): Modifier = composed {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val key = remember(eventId, type) { EventSharedElementKey(eventId, type) }

    with(sharedTransitionScope) {
        this@composed.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = isVisible,
        )
    }
}

/**
 * Extension function to apply shared time column modifier.
 * Used for the time column that appears in Day/Week/ThreeDay views.
 *
 * @param isVisible Whether this element should be visible
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedTimeColumn(isVisible: Boolean): Modifier = composed {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val key = remember { TimeColumnSharedElementKey() }

    with(sharedTransitionScope) {
        this@composed.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = isVisible,
        )
    }
}

/**
 * Extension function to apply shared day column modifier.
 * Used for day columns in the calendar grid that transition between
 * Week/ThreeDay/Day views - columns for the same date will animate/expand.
 *
 * @param date The date this column represents
 * @param isVisible Whether this element should be visible
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedDayColumn(
    date: LocalDate,
    isVisible: Boolean,
): Modifier = composed {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val key = remember(date) { DateSharedElementKey(date, SharedElementType.DayColumn) }

    with(sharedTransitionScope) {
        this@composed.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = isVisible,
        )
    }
}

/**
 * Composable scope that provides access to the SharedTransitionScope
 * for applying shared element modifiers within its content.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun WithSharedTransitionScope(content: @Composable SharedTransitionScope.() -> Unit) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    content(sharedTransitionScope)
}

/**
 * Helper composable to check if a shared transition is currently active.
 * Useful for conditional rendering during transitions.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun isSharedTransitionActive(): Boolean {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    return sharedTransitionScope.isTransitionActive
}

