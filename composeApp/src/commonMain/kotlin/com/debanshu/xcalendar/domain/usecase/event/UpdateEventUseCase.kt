package com.debanshu.xcalendar.domain.usecase.event

import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.util.DomainError
import com.debanshu.xcalendar.domain.util.DomainResult
import com.debanshu.xcalendar.domain.util.EventValidationException
import com.debanshu.xcalendar.domain.util.EventValidator
import org.koin.core.annotation.Factory

@Factory
class UpdateEventUseCase(
    private val eventRepository: IEventRepository,
) {
    suspend operator fun invoke(event: Event): DomainResult<Unit> {
        return try {
            if (event.id.isBlank()) {
                return DomainResult.Error(
                    DomainError.ValidationError("Event ID cannot be empty for update operation"),
                )
            }
            EventValidator.validate(
                title = event.title,
                startTime = event.startTime,
                endTime = event.endTime,
                calendarId = event.calendarId,
                isAllDay = event.isAllDay,
            )

            eventRepository.updateEvent(event)
            DomainResult.Success(Unit)
        } catch (e: EventValidationException) {
            DomainResult.Error(DomainError.ValidationError(e.message ?: "Validation failed"))
        } catch (e: Exception) {
            DomainResult.Error(DomainError.Unknown(e.message ?: "Failed to update event"))
        }
    }
}
