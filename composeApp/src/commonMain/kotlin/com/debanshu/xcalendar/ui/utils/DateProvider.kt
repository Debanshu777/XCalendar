package com.debanshu.xcalendar.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Provides today's date as a remembered value to avoid repeated calculations.
 *
 * This is a stable reference for the current composable scope, avoiding
 * Clock.System.now() calls scattered throughout the codebase.
 *
 * Note: The date is remembered for the composition lifetime.
 * If you need real-time updates (e.g., at midnight), consider using
 * a LaunchedEffect with a timer instead.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun rememberToday(): LocalDate =
    remember {
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    }

/**
 * Returns today's date without composable context.
 * Use this in non-composable code like ViewModels or StateHolders.
 */
@OptIn(ExperimentalTime::class)
fun today(): LocalDate =
    Clock.System
        .now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date

/**
 * Extension to check if a LocalDate is today.
 */
@OptIn(ExperimentalTime::class)
fun LocalDate.isToday(): Boolean {
    val today =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    return this == today
}
