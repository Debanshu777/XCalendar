package com.debanshu.xcalendar.domain.usecase.event

import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.repository.IEventRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class GetEventsForDateRangeUseCase(
    private val eventRepository: IEventRepository
) {
    operator fun invoke(userId: String, startTime: Long, endTime: Long): Flow<List<Event>> =
        eventRepository.getEventsForCalendarsInRange(userId, startTime, endTime)
}

