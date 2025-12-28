package com.debanshu.xcalendar.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Extension functions for navigation back stack manipulation.
 * Encapsulates common navigation patterns for cleaner composable code.
 */

/**
 * Replaces the current top of the stack with a new destination.
 * Removes the current destination and adds the new one if they're different.
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
 * Navigates to a destination if not already there.
 * Simply adds the destination to the back stack.
 * 
 * @param destination The destination to navigate to
 */
fun <T : NavKey> NavBackStack<T>.navigateTo(destination: T) {
    add(destination)
}

/**
 * Pops the back stack if possible.
 * 
 * @return true if navigation occurred, false if back stack was empty
 */
fun <T : NavKey> NavBackStack<T>.popIfPossible(): Boolean {
    return if (isNotEmpty()) {
        removeLastOrNull()
        true
    } else {
        false
    }
}

