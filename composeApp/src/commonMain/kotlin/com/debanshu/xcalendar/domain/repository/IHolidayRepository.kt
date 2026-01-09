package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.coroutines.flow.Flow

interface IHolidayRepository {
    suspend fun updateHolidays(
        countryCode: String,
        year: Int,
    )

    fun getHolidaysForYear(
        countryCode: String,
        year: Int,
    ): Flow<List<Holiday>>
}
