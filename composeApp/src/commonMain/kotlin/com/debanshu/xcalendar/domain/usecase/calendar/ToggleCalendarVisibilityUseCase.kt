package com.debanshu.xcalendar.domain.usecase.calendar

import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.repository.ICalendarRepository
import org.koin.core.annotation.Factory

@Factory
class ToggleCalendarVisibilityUseCase(
    private val calendarRepository: ICalendarRepository
) {
    suspend operator fun invoke(calendar: Calendar): Calendar {
        val updatedCalendar = calendar.copy(isVisible = !calendar.isVisible)
        calendarRepository.upsertCalendar(listOf(updatedCalendar))
        return updatedCalendar
    }
}

