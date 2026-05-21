package com.debanshu.xcalendar.platform

import platform.Foundation.NSLocale

actual fun defaultHolidayRegionCode(): String {
    val code = NSLocale.currentLocale.countryCode
    return code.takeIf { it.isNotBlank() }?.uppercase() ?: "US"
}
