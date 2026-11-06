package com.debanshu.xcalendar.data.store

/**
 * Key for fetching holidays from the Store.
 * Uniquely identifies a holidays request by country and year.
 */
data class HolidayKey(
    val countryCode: String,
    val year: Int
)

/**
 * Key for fetching events from the Store.
 * Uniquely identifies an events request by user, time range.
 */
data class EventKey(
    val userId: String,
    val startTime: Long,
    val endTime: Long
)

/**
 * Key for individual event operations (CRUD).
 */
data class SingleEventKey(
    val eventId: String
)
