package com.debanshu.xcalendar.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.debanshu.xcalendar.common.model.asEntity
import com.debanshu.xcalendar.data.localDataSource.AppDatabase
import com.debanshu.xcalendar.data.localDataSource.model.CalendarEntity
import com.debanshu.xcalendar.data.localDataSource.model.EventReminderEntity
import com.debanshu.xcalendar.data.localDataSource.model.EventWithReminders
import com.debanshu.xcalendar.data.localDataSource.model.UserEntity
import com.debanshu.xcalendar.data.store.SingleEventBookkeeperFactory
import com.debanshu.xcalendar.data.store.SingleEventKey
import com.debanshu.xcalendar.data.store.SingleEventStoreFactory
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.test.TestDataFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import org.mobilenativefoundation.store.core5.ExperimentalStoreApi
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

@OptIn(ExperimentalStoreApi::class)
class EventPersistenceTest {
    private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            db.getUserEntityDao().insertUser(UserEntity("user", "User", "user@example.com", ""))
            db.getCalendarEntityDao().upsertCalendar(listOf(CalendarEntity("calendar", "Calendar", 0, "user", true, true)))
            block(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun rangeIncludesEventsCrossingEitherBoundaryButExcludesAdjacentEvents() = runTest {
        withDatabase { db ->
            val dao = db.getEventEntityDao()
            val ranges = listOf("before" to (50L to 100L), "left" to (50L to 150L),
                "inside" to (120L to 180L), "right" to (150L to 250L),
                "spanning" to (50L to 250L), "after" to (200L to 250L))
            ranges.forEach { (id, times) ->
                dao.upsertEvent(TestDataFactory.createEvent(id = id, calendarId = "calendar", startTime = times.first, endTime = times.second).asEntity())
            }
            val expected = setOf("left", "inside", "right", "spanning")
            assertEquals(expected, dao.getEventsBetweenDates("user", 100, 200).first().map { it.id }.toSet())
            assertEquals(expected, dao.getEventsWithRemindersBetweenDates("user", 100, 200).first().map { it.event.id }.toSet())
        }
    }

    @Test
    fun singleEventReadsPreserveRemindersAfterCacheMiss() = runTest {
        withDatabase { db ->
            val dao = db.getEventEntityDao()
            val event = TestDataFactory.createEvent(calendarId = "calendar", reminderMinutes = listOf(10, 30))
            dao.insertEventWithReminders(event.asEntity(), event.reminderMinutes.map { EventReminderEntity(event.id, it) })
            val store = SingleEventStoreFactory.create(dao, SingleEventBookkeeperFactory.create(db.getSyncFailureDao()))
            val loaded = store.stream<Unit>(StoreReadRequest.cached(SingleEventKey(event.id), refresh = false))
                .filterIsInstance<StoreReadResponse.Data<Event>>().first().value
            assertEquals(listOf(10, 30), loaded.reminderMinutes.sorted())
        }
    }

    @Test
    fun eventObservationUpdatesWhenRemindersChange() = runTest {
        withDatabase { db ->
            val dao = db.getEventEntityDao()
            val event = TestDataFactory.createEvent(calendarId = "calendar").asEntity()
            dao.insertEventWithReminders(event, listOf(EventReminderEntity(event.id, 10)))
            withContext(Dispatchers.Default) {
                withTimeout(2_000) {
                    coroutineScope {
                        val observed = dao.observeEventWithReminders(event.id).produceIn(this)
                        try {
                            assertEquals(listOf(10), observed.receive()!!.reminders.map { it.minutes })
                            dao.insertEventWithReminders(event, listOf(EventReminderEntity(event.id, 30)))
                            assertEquals(listOf(30), observed.receive()!!.reminders.map { it.minutes })
                        } finally {
                            observed.cancel()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun invalidFetchedBatchRollsBackEarlierRows() = runTest {
        withDatabase { db ->
            val dao = db.getEventEntityDao()
            val valid = TestDataFactory.createEvent(id = "valid", calendarId = "calendar").asEntity()
            val invalid = valid.copy(id = "invalid", calendarId = "missing-calendar")
            assertFails {
                dao.insertEventsWithReminders(listOf(EventWithReminders(valid, emptyList()), EventWithReminders(invalid, emptyList())))
            }
            assertEquals(null, dao.getEventById("valid"))
        }
    }
}
