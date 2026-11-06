package com.debanshu.xcalendar.domain.usecase.calendar

import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.repository.ICalendarRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class GetUserCalendarsUseCase(
    private val calendarRepository: ICalendarRepository
) {
    operator fun invoke(userId: String): Flow<List<Calendar>> =
        calendarRepository.getCalendarsForUser(userId)
}

