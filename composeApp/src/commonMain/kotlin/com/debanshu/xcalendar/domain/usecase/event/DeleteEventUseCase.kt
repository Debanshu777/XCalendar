package com.debanshu.xcalendar.domain.usecase.event

import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.util.DomainError
import com.debanshu.xcalendar.domain.util.DomainResult
import org.koin.core.annotation.Factory

@Factory
class DeleteEventUseCase(
    private val eventRepository: IEventRepository
) {
    /**
     * Deletes an event.
     * 
     * @param event The event to delete
     * @return DomainResult.Success with Unit if successful, DomainResult.Error otherwise
     */
    suspend operator fun invoke(event: Event): DomainResult<Unit> {
        return try {
            eventRepository.deleteEvent(event)
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            DomainResult.Error(DomainError.Unknown(e.message ?: "Failed to delete event"))
        }
    }
}

