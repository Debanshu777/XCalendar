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
import com.debanshu.xcalendar.data.store.EventKey
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.repository.EventRepository
import com.debanshu.xcalendar.domain.usecase.event.CreateEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.UpdateEventUseCase
import com.debanshu.xcalendar.domain.util.DomainResult
import com.debanshu.xcalendar.test.TestDataFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.mobilenativefoundation.store.core5.ExperimentalStoreApi
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.mobilenativefoundation.store.store5.Converter
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.MutableStoreBuilder
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Updater
import org.mobilenativefoundation.store.store5.UpdaterResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertNull

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
    fun userRefreshPreservesCalendarsAndLocalEventsWithReminders() = runTest {
        withDatabase { db ->
            val eventDao = db.getEventEntityDao()
            val userDao = db.getUserEntityDao()
            val event = TestDataFactory.createEvent(
                id = "locally-created-event", calendarId = "calendar", reminderMinutes = listOf(0, 10, 30),
            )
            eventDao.insertEventWithReminders(
                event.asEntity(), event.reminderMinutes.map { EventReminderEntity(event.id, it) },
            )
            val expectedCalendars = db.getCalendarEntityDao().getCalendarsByUserId("user").first()

            // Launch refreshes the same account even when no fields changed.
            // Both an identical refresh and a profile update must preserve children.
            for (user in listOf(
                UserEntity("user", "User", "user@example.com", ""),
                UserEntity("user", "Updated user", "updated@example.com", "new-photo"),
            )) {
                userDao.insertUser(user)

                assertEquals(user, userDao.getUserById(user.id))
                assertEquals(expectedCalendars, db.getCalendarEntityDao().getCalendarsByUserId(user.id).first())
                val persisted = assertNotNull(eventDao.getEventWithRemindersById(event.id))
                assertEquals(event.asEntity(), persisted.event)
                assertEquals(event.reminderMinutes, persisted.reminders.map { it.minutes }.sorted())
            }
        }
    }

    @Test
    fun localStoreWriteFailuresReachCreateAndUpdateUseCases() = runTest {
        withDatabase { db ->
            val dao = db.getEventEntityDao()
            val singleStore = SingleEventStoreFactory.create(dao, SingleEventBookkeeperFactory.create(db.getSyncFailureDao()))
            // This repository dependency is unused by mutations. Fail immediately
            // if a write unexpectedly tries to fetch or write a calendar range.
            val rangeStore = MutableStoreBuilder.from(
                fetcher = Fetcher.of<EventKey, List<Event>> { error("Unexpected range fetch") },
                sourceOfTruth = SourceOfTruth.of<EventKey, List<Event>, List<Event>>(
                    reader = { flowOf(emptyList()) },
                    writer = { _, _ -> error("Unexpected range write") },
                ),
                converter = Converter.Builder<List<Event>, List<Event>, List<Event>>()
                    .fromNetworkToLocal { it }.fromOutputToLocal { it }.build(),
            ).scope(backgroundScope).build(
                updater = Updater.by<EventKey, List<Event>, Unit>(post = { _, _ -> UpdaterResult.Success.Typed(Unit) }),
            )
            val repository = EventRepository(rangeStore, singleStore, dao)
            val invalid = TestDataFactory.createEvent(id = "failed-create", calendarId = "missing-calendar")
            assertIs<DomainResult.Error>(CreateEventUseCase(repository)(invalid))
            assertNull(dao.getEventById(invalid.id))

            val original = TestDataFactory.createEvent(id = "existing-event", calendarId = "calendar", reminderMinutes = listOf(10, 30))
            dao.insertEventWithReminders(original.asEntity(), original.reminderMinutes.map { EventReminderEntity(original.id, it) })
            assertIs<DomainResult.Error>(UpdateEventUseCase(repository)(original.copy(calendarId = "missing-calendar", reminderMinutes = listOf(60))))
            val persisted = assertNotNull(dao.getEventWithRemindersById(original.id))
            assertEquals(original.asEntity(), persisted.event)
            assertEquals(original.reminderMinutes, persisted.reminders.map { it.minutes }.sorted())
        }
    }

    @Test
    fun repeatedDemoImportPreservesLocalEditsAndImportsNewIds() = runTest {
        withDatabase { db ->
            val dao = db.getEventEntityDao()
            val original = TestDataFactory.createEvent(id = "demo-event", calendarId = "calendar", reminderMinutes = listOf(10))
            val seed = EventWithReminders(original.asEntity(), listOf(EventReminderEntity(original.id, 10)))
            dao.importDemoSeedEvents(listOf(seed))

            val edited = original.copy(title = "My local edit", description = "Local notes", reminderMinutes = listOf(0, 30))
            dao.insertEventWithReminders(edited.asEntity(), edited.reminderMinutes.map { EventReminderEntity(edited.id, it) })
            val newEvent = TestDataFactory.createEvent(id = "new-demo-event", calendarId = "calendar", reminderMinutes = listOf(60))
            val newSeed = EventWithReminders(newEvent.asEntity(), listOf(EventReminderEntity(newEvent.id, 60)))

            // Both the original seed and an upstream change to the same ID must
            // leave local content intact; a newly introduced ID is still seeded.
            dao.importDemoSeedEvents(listOf(seed, newSeed))
            dao.importDemoSeedEvents(listOf(seed.copy(event = seed.event.copy(title = "Changed demo title")), newSeed))

            val persistedEdit = assertNotNull(dao.getEventWithRemindersById(edited.id))
            assertEquals(edited.asEntity(), persistedEdit.event)
            assertEquals(edited.reminderMinutes, persistedEdit.reminders.map { it.minutes }.sorted())
            val persistedNew = assertNotNull(dao.getEventWithRemindersById(newEvent.id))
            assertEquals(newEvent.asEntity(), persistedNew.event)
            assertEquals(newEvent.reminderMinutes, persistedNew.reminders.map { it.minutes })
        }
    }

    @Test
    fun failedDemoImportRollsBackNewIdsAndLeavesLocalEditsUntouched() = runTest {
        withDatabase { db ->
            val dao = db.getEventEntityDao()
            val existing = TestDataFactory.createEvent(id = "edited-event", calendarId = "calendar", title = "Local edit")
            dao.insertEventWithReminders(existing.asEntity(), listOf(EventReminderEntity(existing.id, 30)))
            val valid = TestDataFactory.createEvent(id = "new-event", calendarId = "calendar").asEntity()
            val invalid = valid.copy(id = "invalid-event", calendarId = "missing-calendar")
            assertFails {
                dao.importDemoSeedEvents(listOf(
                    EventWithReminders(existing.asEntity().copy(title = "Demo title"), listOf(EventReminderEntity(existing.id, 10))),
                    EventWithReminders(valid, listOf(EventReminderEntity(valid.id, 60))),
                    EventWithReminders(invalid, emptyList()),
                ))
            }
            assertNull(dao.getEventById(valid.id))
            val persisted = assertNotNull(dao.getEventWithRemindersById(existing.id))
            assertEquals(existing.asEntity(), persisted.event)
            assertEquals(listOf(30), persisted.reminders.map { it.minutes })
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
