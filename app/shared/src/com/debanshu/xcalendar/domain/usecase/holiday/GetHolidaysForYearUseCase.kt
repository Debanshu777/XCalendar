package com.debanshu.xcalendar.domain.usecase.holiday

import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.repository.IHolidayRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class GetHolidaysForYearUseCase(
    private val holidayRepository: IHolidayRepository
) {
    operator fun invoke(countryCode: String, year: Int): Flow<List<Holiday>> =
        holidayRepository.getHolidaysForYear(countryCode, year)
}

