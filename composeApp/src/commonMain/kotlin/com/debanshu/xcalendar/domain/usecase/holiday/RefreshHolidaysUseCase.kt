package com.debanshu.xcalendar.domain.usecase.holiday

import com.debanshu.xcalendar.domain.repository.IHolidayRepository
import org.koin.core.annotation.Factory

@Factory
class RefreshHolidaysUseCase(
    private val holidayRepository: IHolidayRepository
) {
    suspend operator fun invoke(countryCode: String, year: Int) {
        holidayRepository.updateHolidays(countryCode, year)
    }
}

