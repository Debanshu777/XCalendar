package com.debanshu.xcalendar.ui.utils

import androidx.compose.ui.graphics.Color
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.jvm.JvmName

// ============================================================================
// Event Extensions
// ============================================================================

/**
 * Filters events by a specific date.
 */
@JvmName("filterEventsByDate")
fun ImmutableList<Event>.filterByDate(date: LocalDate): ImmutableList<Event> {
    return this.filter { event ->
        event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date == date
    }.toImmutableList()
}

/**
 * Groups events by their start date.
 */
@JvmName("groupEventsByDate")
fun ImmutableList<Event>.groupByDate(): ImmutableMap<LocalDate, ImmutableList<Event>> {
    return this
        .groupBy { event ->
            event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
        }
        .mapValues { it.value.toImmutableList() }
        .toImmutableMap()
}

/**
 * Filters events for a date range (inclusive).
 */
@JvmName("filterEventsByDateRange")
fun ImmutableList<Event>.filterByDateRange(
    startDate: LocalDate,
    endDate: LocalDate
): ImmutableList<Event> {
    return this.filter { event ->
        val eventDate = event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
        eventDate >= startDate && eventDate <= endDate
    }.toImmutableList()
}

// ============================================================================
// Holiday Extensions
// ============================================================================

/**
 * Filters holidays by a specific date.
 */
@JvmName("filterHolidaysByDate")
fun ImmutableList<Holiday>.filterByDate(date: LocalDate): ImmutableList<Holiday> {
    return this.filter { holiday ->
        holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date == date
    }.toImmutableList()
}

/**
 * Groups holidays by their date.
 */
@JvmName("groupHolidaysByDate")
fun ImmutableList<Holiday>.groupByDate(): ImmutableMap<LocalDate, ImmutableList<Holiday>> {
    return this
        .groupBy { holiday ->
            holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date
        }
        .mapValues { it.value.toImmutableList() }
        .toImmutableMap()
}

// ============================================================================
// Color Extensions
// ============================================================================

/**
 * Converts an Int color value to Compose Color.
 */
fun Int.toComposeColor(): Color = Color(this)

/**
 * Returns a color with event container alpha (for backgrounds).
 */
fun Color.withEventAlpha(): Color = this.copy(alpha = 0.15f)

/**
 * Returns a color with overlapping event alpha.
 */
fun Color.withOverlappingAlpha(): Color = this.copy(alpha = 0.7f)

/**
 * Returns a color with high visibility alpha.
 */
fun Color.withHighAlpha(): Color = this.copy(alpha = 0.9f)

/**
 * Returns a color with text overlay alpha.
 */
fun Color.withTextAlpha(): Color = this.copy(alpha = 0.7f)
