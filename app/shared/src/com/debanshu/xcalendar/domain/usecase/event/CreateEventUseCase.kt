package com.debanshu.xcalendar.domain.usecase.event

import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.util.DomainError
import com.debanshu.xcalendar.domain.util.DomainResult
import com.debanshu.xcalendar.domain.util.EventValidationException
import com.debanshu.xcalendar.domain.util.EventValidator
import org.koin.core.annotation.Factory

@Factory
class CreateEventUseCase(
    private val eventRepository: IEventRepository,
) {
    suspend operator fun invoke(event: Event): DomainResult<Unit> =
        try {
            // Validate event data before saving
            EventValidator.validate(
                title = event.title,
                startTime = event.startTime,
                endTime = event.endTime,
                calendarId = event.calendarId,
                isAllDay = event.isAllDay,
            )

            eventRepository.addEvent(event)
            DomainResult.Success(Unit)
        } catch (e: EventValidationException) {
            DomainResult.Error(DomainError.ValidationError(e.message ?: "Validation failed"))
        } catch (e: Exception) {
            DomainResult.Error(DomainError.Unknown(e.message ?: "Failed to create event"))
        }
}
