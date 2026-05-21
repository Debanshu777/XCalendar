package com.debanshu.xcalendar.platform

import java.util.Locale

actual fun defaultHolidayRegionCode(): String {
    val raw = Locale.getDefault().country
    return raw.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: "US"
}
