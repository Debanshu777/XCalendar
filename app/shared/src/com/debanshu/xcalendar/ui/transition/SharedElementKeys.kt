package com.debanshu.xcalendar.ui.transition

import kotlinx.datetime.LocalDate

/**
 * Enum representing the different types of shared elements in the calendar app.
 * Used to create unique keys for shared element transitions.
 */
enum class SharedElementType {
    /** Date number/indicator (the circular badge with the day number) */
    DateCell,

    /** Full day header including weekday name and date */
    DayHeader,

    /** Day column in the calendar grid (for Week/ThreeDay/Day transitions) */
    DayColumn,

    /** Event card/tag displayed in calendar views */
    EventCard,

    /** Event container/background bounds */
    EventBackground,

    /** Time column in day/week/three-day views */
    TimeColumn,

    /** Event title text */
    EventTitle,

    /** Event color indicator */
    EventColorIndicator,
}

/**
 * Shared element key for date-related elements.
 * Used for transitioning date cells between MonthScreen and DayScreen,
 * or between any calendar views.
 *
 * @param date The date this element represents
 * @param type The type of shared element
 */
data class DateSharedElementKey(
    val date: LocalDate,
    val type: SharedElementType,
)

/**
 * Shared element key for event-related elements.
 * Used for matching the same rendered event segment between calendar views.
 *
 * @param eventId The unique identifier of the event
 * @param date The date of this rendered segment, including overnight/multi-day events
 * @param type The type of shared element
 */
data class EventSharedElementKey(
    val eventId: String,
    val date: LocalDate,
    val type: SharedElementType,
)

/**
 * Shared element key for the time column.
 * Used for transitioning the time column between Day/Week/ThreeDay views.
 *
 * @param type The type of shared element (should be TimeColumn)
 */
data class TimeColumnSharedElementKey(
    val type: SharedElementType = SharedElementType.TimeColumn,
)

