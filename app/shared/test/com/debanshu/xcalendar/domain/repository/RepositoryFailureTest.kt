package com.debanshu.xcalendar.domain.repository

import com.debanshu.xcalendar.data.store.HolidayKey
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.usecase.event.CreateEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.UpdateEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.DeleteEventUseCase
import com.debanshu.xcalendar.domain.util.runCatchingToDomainResult
import com.debanshu.xcalendar.domain.util.runSuspendCatchingToDomainResult
import com.debanshu.xcalendar.test.FakeEventRepository
import com.debanshu.xcalendar.test.TestDataFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreWriteResponse
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RepositoryFailureTest {
    @Test
    fun storeWriteFailuresPropagateMessagesAndOriginalExceptions() {
        val failure = IllegalStateException("Local write failed")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            StoreWriteResponse.Error.Exception(failure).requireWriteSuccess()
        })
        val message = assertFailsWith<RepositoryException> {
            StoreWriteResponse.Error.Message("Local write failed").requireWriteSuccess()
        }
        assertEquals("Local write failed", message.message)
    }

    @Test
    fun storeWriteCancellationIsRethrownWithoutBecomingASaveError() {
        val cancellation = CancellationException("Write cancelled")
        assertSame(cancellation, assertFailsWith<CancellationException> {
            StoreWriteResponse.Error.Exception(cancellation).requireWriteSuccess()
        })
    }

    @Test
    fun failedHolidayRefreshReturnsAnErrorInsteadOfWaitingForever() = runTest {
        val store = StoreBuilder.from(Fetcher.of<HolidayKey, List<Holiday>> { error("offline") }).build()
        val repository = HolidayRepository(store)
        val error = assertFailsWith<RepositoryException> {
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { repository.updateHolidays("IN", 2026) }
            }
        }
        assertTrue(error.message.orEmpty().contains("offline"))
    }

    @Test
    fun repositoryDoesNotTurnCancellationIntoFailure() = runTest {
        val repository = object : BaseRepository() {
            suspend fun cancel(): Unit = safeCallOrThrow("cancel") { throw CancellationException("cancelled") }
        }
        assertFailsWith<CancellationException> { repository.cancel() }
    }

    @Test
    fun resultHelpersDoNotSwallowCancellation() = runTest {
        assertFailsWith<CancellationException> {
            runCatchingToDomainResult<Unit> { throw CancellationException("cancelled") }
        }
        assertFailsWith<CancellationException> {
            runSuspendCatchingToDomainResult<Unit> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun eventOperationsPropagateCancellation() = runTest {
        val repository = object : IEventRepository by FakeEventRepository() {
            override suspend fun addEvent(event: Event) { throw CancellationException("cancelled") }
            override suspend fun updateEvent(event: Event) { throw CancellationException("cancelled") }
            override suspend fun deleteEvent(event: Event) { throw CancellationException("cancelled") }
        }
        val event = TestDataFactory.createEvent()
        assertFailsWith<CancellationException> { CreateEventUseCase(repository)(event) }
        assertFailsWith<CancellationException> { UpdateEventUseCase(repository)(event) }
        assertFailsWith<CancellationException> { DeleteEventUseCase(repository)(event) }
    }
}
