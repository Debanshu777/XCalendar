package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.data.localDataSource.HolidayDao
import com.debanshu.xcalendar.data.model.HolidayEntity
import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.core.annotation.Singleton

@Singleton
class HolidayRepository(private val holidayDao: HolidayDao) {
    fun getHolidaysForYear(countryCode: String, year: Int): Flow<List<Holiday>> {
        // Create start date - January 1st of the year at midnight
        val startDateTime = LocalDateTime(
            year = year,
            month = Month.JANUARY,
            dayOfMonth = 1,
            hour = 0,
            minute = 0,
            second = 0,
            nanosecond = 0
        )

        // Create end date - December 31st of the year at 23:59:59
        val endDateTime = LocalDateTime(
            year = year,
            month = Month.DECEMBER,
            dayOfMonth = 31,
            hour = 23,
            minute = 59,
            second = 59,
            nanosecond = 999_999_999
        )

        // Convert to Instant (point in time) with the system's time zone
        val timeZone = TimeZone.currentSystemDefault()
        val startInstant = startDateTime.toInstant(timeZone)
        val endInstant = endDateTime.toInstant(timeZone)

        // Convert to epoch milliseconds for database query
        val startDate = startInstant.toEpochMilliseconds()
        val endDate = endInstant.toEpochMilliseconds()

        // Query the database for holidays in this range
        return holidayDao.getHolidaysInRange(startDate, endDate).map { entities ->
            entities
                .filter { it.countryCode == countryCode }
                .map { it.toHoliday() }
        }
    }

    suspend fun addHolidays(holidays: List<Holiday>) {
        val entities = holidays.map { it.toEntity() }
        holidayDao.insertHolidays(entities)
    }

    private fun HolidayEntity.toHoliday(): Holiday =
        Holiday(id, name, date, countryCode)

    private fun Holiday.toEntity(): HolidayEntity =
        HolidayEntity(id, name, date, countryCode)
}