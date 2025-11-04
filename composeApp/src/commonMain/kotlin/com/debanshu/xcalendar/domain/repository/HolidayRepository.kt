package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.common.model.asHoliday
import com.debanshu.xcalendar.common.model.asHolidayEntity
import com.debanshu.xcalendar.data.localDataSource.HolidayDao
import com.debanshu.xcalendar.data.remoteDataSource.HolidayApiService
import com.debanshu.xcalendar.data.remoteDataSource.Result
import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.core.annotation.Single
import kotlin.time.ExperimentalTime

@Single
class HolidayRepository(
    private val holidayDao: HolidayDao,
    private val holidayApiService: HolidayApiService,
) {
    suspend fun updateHolidays(
        countryCode: String,
        year: Int,
    ) {
        when (val response = holidayApiService.getHolidays(countryCode, year)) {
            is Result.Error -> {
                println("HEREEEEEEE" + response.error.toString())
            }

            is Result.Success -> {
                val remoteHolidays =
                    response.data.response.holidays
                        .map { it.asHoliday() }
                holidayDao.insertHolidays(remoteHolidays.map { it.asHolidayEntity() })
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun getHolidaysForYear(
        countryCode: String,
        year: Int,
    ): Flow<List<Holiday>> {
        val startDateTime =
            LocalDateTime(
                year = year,
                month = Month.JANUARY,
                day = 1,
                hour = 0,
                minute = 0,
                second = 0,
                nanosecond = 0,
            )
        val endDateTime =
            LocalDateTime(
                year = year,
                month = Month.DECEMBER,
                day = 31,
                hour = 23,
                minute = 59,
                second = 59,
                nanosecond = 999_999_999,
            )
        val timeZone = TimeZone.currentSystemDefault()
        val startInstant = startDateTime.toInstant(timeZone)
        val endInstant = endDateTime.toInstant(timeZone)
        val startDate = startInstant.toEpochMilliseconds()
        val endDate = endInstant.toEpochMilliseconds()

        return holidayDao.getHolidaysInRange(startDate, endDate).map { entities ->
            entities.filter { it.countryCode == countryCode.lowercase() }.map { it.asHoliday() }
        }
    }
}
