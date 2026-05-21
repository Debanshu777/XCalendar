package com.debanshu.xcalendar.platform

/**
 * Region used for holiday API requests (ISO 3166-1 alpha-2, uppercased).
 * Backed by the host OS locale until user prefs exist (audit F24).
 */
expect fun defaultHolidayRegionCode(): String
