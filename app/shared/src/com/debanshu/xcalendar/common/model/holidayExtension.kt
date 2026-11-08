package com.debanshu.xcalendar.common.model

import com.debanshu.xcalendar.common.parseDateTime
import com.debanshu.xcalendar.data.localDataSource.model.HolidayEntity
import com.debanshu.xcalendar.data.remoteDataSource.model.holiday.HolidayItem
import com.debanshu.xcalendar.domain.model.Holiday

fun HolidayItem.asHoliday(): Holiday =
    Holiday(
        id = urlid,
        name = name,
        date = parseDateTime(date.iso),
        countryCode = country.id
    )

fun HolidayItem.asHolidayEntity(): HolidayEntity =
    HolidayEntity(
        id = urlid,
        name = name,
        date = parseDateTime(date.iso),
        countryCode = country.id
    )

fun HolidayEntity.asHoliday(): Holiday =
    Holiday(id, name, date, countryCode)

fun Holiday.asHolidayEntity(): HolidayEntity =
    HolidayEntity(id, name, date, countryCode)
