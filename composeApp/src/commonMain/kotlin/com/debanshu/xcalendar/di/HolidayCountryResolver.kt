package com.debanshu.xcalendar.di

import com.debanshu.xcalendar.platform.defaultHolidayRegionCode

/**
 * ISO 3166-1 alpha-2 region for public-holiday APIs — from the system locale
 * today; user preference override can be added later (audit F24).
 */
class HolidayCountryResolver {
    fun current(): String = defaultHolidayRegionCode()
}
