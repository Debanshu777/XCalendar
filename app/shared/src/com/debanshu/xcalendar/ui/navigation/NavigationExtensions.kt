package com.debanshu.xcalendar.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Extension functions for navigation back stack manipulation.
 * Encapsulates common navigation patterns for cleaner composable code.
 */

/**
 * Replaces the current top of the stack with a new destination.
 * 
 * This is the primary navigation method for switching between calendar views
 * (Month, Week, Day, etc.) where we want to replace rather than stack.
 * 
 * Only performs navigation if the destination differs from current view.
 * 
 * @param destination The new destination to navigate to
 */
fun <T : NavKey> NavBackStack<T>.replaceLast(destination: T) {
    val currentView = lastOrNull()
    if (currentView != destination) {
        if (isNotEmpty()) {
            removeLastOrNull()
        }
        add(destination)
    }
}

/**
 * Pops the back stack if possible.
 * 
 * Useful for handling back navigation consistently.
 * 
 * @return true if a destination was popped, false if back stack was empty
 */
fun <T : NavKey> NavBackStack<T>.popIfPossible(): Boolean {
    return if (isNotEmpty()) {
        removeLastOrNull()
        true
    } else {
        false
    }
}
