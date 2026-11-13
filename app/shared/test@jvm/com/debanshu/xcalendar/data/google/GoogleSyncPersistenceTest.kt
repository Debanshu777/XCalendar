package com.debanshu.xcalendar.data.google

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.debanshu.xcalendar.data.localDataSource.AppDatabase
import com.debanshu.xcalendar.domain.model.Event
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.ui.google.GoogleCalendarViewModel
import com.debanshu.xcalendar.ui.google.GoogleSessionStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class GoogleSyncPersistenceTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun queuedRefreshPreservesTheSaveConflictAndTheReviewedDraftCanBeRetried() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val db = database()
        val json = Json { ignoreUnknownKeys = true }
        val sessions = GoogleSessionStore(InMemoryGoogleSecureStorage())
        sessions.upsert(GoogleSavedAccount(GoogleProfile("account"), "client", "refresh"))
        val patchStarted = CompletableDeferred<Unit>()
        val rejectFirstPatch = CompletableDeferred<Unit>()
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        val original = GoogleEvent(
            id = "event", etag = "v1", summary = "Original",
            start = GoogleDateTime(date = "2026-11-01"), end = GoogleDateTime(date = "2026-11-02"),
        )
        val remoteEdit = original.copy(etag = "v2", summary = "Remote edit")
        var remote = original
        val submittedVersions = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            when {
                request.method == HttpMethod.Patch -> {
                    submittedVersions += request.headers[HttpHeaders.IfMatch]
                    if (submittedVersions.size == 1) {
                        patchStarted.complete(Unit)
                        rejectFirstPatch.await()
                        remote = remoteEdit
                        respond("{}", HttpStatusCode.PreconditionFailed, headers)
                    } else {
                        val body = json.decodeFromString<GoogleEventWrite>((request.body as TextContent).text)
                        remote = remote.copy(etag = "v3", summary = body.summary)
                        respond(json.encodeToString(remote), headers = headers)
                    }
                }
                request.url.encodedPath.endsWith("calendarList") ->
                    respond("""{"items":[{"id":"calendar","accessRole":"owner"}]}""", headers = headers)
                request.url.encodedPath.endsWith("/events/event") ->
                    respond(json.encodeToString(remote), headers = headers)
                else -> respond(json.encodeToString(GoogleEventPage(listOf(remote))), headers = headers)
            }
        }) { install(ContentNegotiation) { json(json) } }
        val vm = GoogleCalendarViewModel(GoogleCalendarRepository(GoogleCalendarApi(client), db, json))
        suspend fun awaitIdle() = withContext(Dispatchers.Default) {
            withTimeout(5000) { vm.state.first { !it.busy } }
        }
        try {
            vm.selectMonth(2026, 11)
            vm.restore(GoogleAuthFactory(sessions) { TestAuth("token") })
            awaitIdle()
            val calendar = vm.snapshots.value.single().calendars.single().calendar
            val event = assertNotNull(original.asEvent(calendar, "account"))
            val draft = event.copy(title = "My draft")
            vm.save(draft, editing = true) { error("The first patch should fail") }
            withContext(Dispatchers.Default) { withTimeout(5000) { patchStarted.await() } }
            vm.refresh()
            rejectFirstPatch.complete(Unit)
            withContext(Dispatchers.Default) {
                withTimeout(5000) {
                    vm.snapshots.first { it.single().calendars.single().events.single().etag == "v2" }
                }
            }
            awaitIdle()
            assertTrue(vm.editorState.value.hasConflict)
            assertNotNull(vm.editorState.value.error)
            assertNull(vm.state.value.error)
            assertNull(vm.editorState.value.latestEvent)

            vm.loadLatestEvent(event) {}
            awaitIdle()
            assertEquals("Remote edit", vm.editorState.value.latestEvent?.title)
            val saved = CompletableDeferred<Unit>()
            vm.save(draft, editing = true) { saved.complete(Unit) }
            withContext(Dispatchers.Default) { withTimeout(5000) { saved.await() } }
            awaitIdle()
            assertEquals(listOf<String?>("v1", "v2"), submittedVersions)
            assertEquals("My draft", vm.snapshots.value.single().calendars.single().events.single().summary)
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
            client.close()
            db.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun inFlightSyncIsReusedWhenTheNewMonthIsCovered() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val db = database()
        val json = Json { ignoreUnknownKeys = true }
        val sessions = GoogleSessionStore(InMemoryGoogleSecureStorage())
        sessions.upsert(GoogleSavedAccount(GoogleProfile("account"), "client", "refresh"))
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var fetches = 0
        val client = HttpClient(MockEngine {
            if (++fetches == 1) {
                started.complete(Unit)
                finish.await()
            }
            respond("""{"items":[]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(json) } }
        val vm = GoogleCalendarViewModel(GoogleCalendarRepository(GoogleCalendarApi(client), db, json))
        try {
            vm.selectMonth(2026, 11)
            vm.restore(GoogleAuthFactory(sessions) { TestAuth("token") })
            withContext(Dispatchers.Default) { withTimeout(5000) { started.await() } }
            vm.selectMonth(2026, 12)
            finish.complete(Unit)
            withContext(Dispatchers.Default) {
                withTimeout(5000) { vm.state.first { !it.busy } }
            }
            assertNull(vm.state.value.error)
            assertEquals(1, fetches)
            assertTrue(vm.snapshots.value.single().syncedAt > 0)
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
            client.close()
            db.close()
        }
    }

    @Test
    fun confirmedGoogleEditSurvivesCacheFailureAndRetriesLocally() = runTest {
        val db = database()
        val json = Json { ignoreUnknownKeys = true }
        val profile = GoogleProfile("account")
        val calendar = GoogleCalendar("calendar", accessRole = "owner")
        val original = GoogleEvent(
            id = "event", etag = "v1", summary = "Before",
            start = GoogleDateTime(date = "2026-11-01"), end = GoogleDateTime(date = "2026-11-02"),
        )
        val cached = GoogleSnapshot(profile, listOf(GoogleCalendarData(calendar, listOf(original))))
        val cachedRow = GoogleCacheEntity("account", json.encodeToString(cached))
        val sessions = GoogleSessionStore(InMemoryGoogleSecureStorage())
        sessions.upsert(GoogleSavedAccount(profile, "client", "refresh"))
        var writes = 0
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("v1", request.headers[HttpHeaders.IfMatch])
            writes++
            respond(
                json.encodeToString(original.copy(etag = "v2", summary = "After")),
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { install(ContentNegotiation) { json(json) } }
        try {
            db.googleCacheDao().write(cachedRow)
            val repo = GoogleCalendarRepository(GoogleCalendarApi(client), db, json)
            repo.restore(GoogleAuthFactory(sessions) { TestAuth("token") })
            db.useWriterConnection { connection ->
                connection.usePrepared(
                    "CREATE TRIGGER reject_cache_write BEFORE INSERT ON google_calendar_cache " +
                        "BEGIN SELECT RAISE(ABORT, 'Cache write failed'); END",
                ) { it.step() }
            }
            val event = assertNotNull(original.asEvent(calendar, profile.sub))
            repo.save(event.copy(title = "After"), editing = true)
            val updated = repo.snapshots.value.single()
            assertEquals("v2", updated.calendars.single().events.single().etag)
            assertEquals(cachedRow, db.googleCacheDao().read(profile.sub))
            assertTrue(repo.hasPendingCacheWrites)

            db.useWriterConnection { connection ->
                connection.usePrepared("DROP TRIGGER reject_cache_write") { it.step() }
            }
            repo.retryPendingCacheWrites()
            assertFalse(repo.hasPendingCacheWrites)
            assertEquals(updated, json.decodeFromString<GoogleSnapshot>(db.googleCacheDao().read(profile.sub)!!.snapshot))
            assertEquals(1, writes)
        } finally {
            client.close()
            db.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun navigatingAcrossCachedYearSkipsFetchButExplicitRefreshStillFetches() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val directory = java.nio.file.Files.createTempDirectory("calendar-restart-test").toFile()
        fun openDatabase() = Room.databaseBuilder<AppDatabase>(directory.resolve("cache.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        var db = openDatabase()
        val json = Json { ignoreUnknownKeys = true }
        val sessions = GoogleSessionStore(InMemoryGoogleSecureStorage())
        sessions.upsert(GoogleSavedAccount(GoogleProfile("account"), "client", "refresh"))
        var fetches = 0
        val client = HttpClient(MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("calendarList")) {
                fetches++
                """{"items":[{"id":"calendar","accessRole":"owner"}]}"""
            } else """{"items":[]}"""
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(json) } }
        var vm = GoogleCalendarViewModel(GoogleCalendarRepository(GoogleCalendarApi(client), db, json))
        suspend fun awaitIdle() = withContext(Dispatchers.Default) {
            withTimeout(5000) { vm.state.first { !it.busy } }
        }
        try {
            assertEquals(GoogleSessionStatus.Pending, vm.state.value.sessionStatus)
            vm.selectMonth(2026, 12)
            vm.restore(GoogleAuthFactory(sessions) { TestAuth("token") })
            awaitIdle()
            assertEquals(GoogleSessionStatus.Ready, vm.state.value.sessionStatus)
            assertNull(vm.state.value.error)
            assertEquals(1, fetches)
            val persisted = vm.snapshots.value
            vm.viewModelScope.cancel()
            db.close()
            db = openDatabase()
            // Reopen the on-disk database with fresh repository/ViewModel instances.
            vm = GoogleCalendarViewModel(GoogleCalendarRepository(GoogleCalendarApi(client), db, json))
            vm.selectMonth(2026, 12)
            vm.restore(GoogleAuthFactory(sessions) { TestAuth("token") })
            awaitIdle()
            assertEquals(persisted, vm.snapshots.value)
            assertEquals(1, fetches)
            vm.refreshIfNeeded()
            awaitIdle()
            assertEquals(1, fetches)
            vm.selectMonth(2026, 8)
            vm.selectMonth(2026, 12)
            vm.selectMonth(2025, 12)
            vm.selectMonth(2027, 12)
            vm.selectMonth(2026, 12)
            awaitIdle()
            assertEquals(1, fetches)
            vm.selectMonth(2028, 1)
            awaitIdle()
            assertEquals(2, fetches)
            vm.refresh()
            awaitIdle()
            assertEquals(3, fetches)
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
            client.close()
            db.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun obsoleteMonthSyncDoesNotReplaceVisibleOrPersistedEvents() = runTest {
        val db = database()
        val json = Json { ignoreUnknownKeys = true }
        val profile = GoogleProfile("account")
        val cached = GoogleSnapshot(profile, rangeStart = 1, rangeEnd = 100)
        val sessions = GoogleSessionStore(InMemoryGoogleSecureStorage())
        sessions.upsert(GoogleSavedAccount(profile, "client", "refresh"))
        db.googleCacheDao().write(GoogleCacheEntity("account", json.encodeToString(cached)))
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClient(MockEngine { request ->
            respond(if (request.url.encodedPath.endsWith("calendarList"))
                """{"items":[{"id":"calendar","accessRole":"owner"}]}"""
            else """{"items":[{"id":"b"},{"id":"a"}]}""", headers = headers)
        }) { install(ContentNegotiation) { json(json) } }
        try {
            val repo = GoogleCalendarRepository(GoogleCalendarApi(client), db, json)
            repo.restore(GoogleAuthFactory(sessions) { TestAuth("token") })
            repo.sync(200, 300, acceptResult = { false })
            assertEquals(cached, repo.snapshots.value.single())
            // The request becomes obsolete after the write, before transaction commit.
            var checks = 0
            repo.sync(200, 300, acceptResult = { checks++ < 2 })
            assertEquals(cached, repo.snapshots.value.single())
            val persisted = assertNotNull(db.googleCacheDao().read("account"))
            assertEquals(cached, json.decodeFromString<GoogleSnapshot>(persisted.snapshot))
            repo.sync(400, 500)
            assertEquals(400L, repo.snapshots.value.single().rangeStart)
            assertEquals(listOf("a", "b"), repo.snapshots.value.single().calendars.single().events.map { it.id })
        } finally { client.close(); db.close() }
    }

    @Test
    fun restoreRebuildsBothAccountsFromVaultAndCacheAndRemovedAccountStaysRemoved() = runTest {
        val db = database()
        val storage = InMemoryGoogleSecureStorage()
        val sessions = GoogleSessionStore(storage)
        val json = Json { ignoreUnknownKeys = true }
        val profileA = GoogleProfile("account-a", "a@example.com", "A")
        val profileB = GoogleProfile("account-b", "b@example.com", "B")
        val cachedA = GoogleSnapshot(profileA, listOf(GoogleCalendarData(GoogleCalendar("cal-a"), emptyList())), syncedAt = 10)
        val cachedB = GoogleSnapshot(profileB, listOf(GoogleCalendarData(GoogleCalendar("cal-b"), emptyList())), syncedAt = 20)
        db.googleCacheDao().write(GoogleCacheEntity("account-a", json.encodeToString(cachedA)))
        db.googleCacheDao().write(GoogleCacheEntity("account-b", json.encodeToString(cachedB)))
        sessions.upsert(GoogleSavedAccount(profileA, "client-id", "refresh-a"))
        sessions.upsert(GoogleSavedAccount(profileB, "client-id", "refresh-b"))
        val createdFrom = mutableListOf<String?>()
        val factory = GoogleAuthFactory(sessions) { saved ->
            createdFrom += saved?.profile?.sub
            TestAuth(saved?.refreshToken ?: "missing-token")
        }
        val client = HttpClient(MockEngine { error("Restore and sign-out must not make network requests") })
        try {
            val firstProcess = GoogleCalendarRepository(GoogleCalendarApi(client), db, json)
            firstProcess.restore(factory)
            assertEquals(listOf("account-a", "account-b"), firstProcess.snapshots.value.map { it.profile.sub })
            assertEquals(listOf<String?>("account-a", "account-b"), createdFrom)
            assertEquals(cachedA.calendars, firstProcess.snapshots.value[0].calendars)
            assertEquals(cachedB.calendars, firstProcess.snapshots.value[1].calendars)

            firstProcess.signOut("account-a")
            assertNull(db.googleCacheDao().read("account-a"))

            // Simulate the process ending after vault removal and before cache commit.
            val orphan = GoogleCacheEntity("account-a", json.encodeToString(cachedA))
            db.googleCacheDao().write(orphan)
            val restarted = GoogleCalendarRepository(GoogleCalendarApi(client), db, json)
            storage.failReads = true
            assertFailsWith<IllegalStateException> { restarted.restore(factory) }
            assertEquals(orphan, db.googleCacheDao().read("account-a"))
            storage.failReads = false
            restarted.restore(factory)
            assertNull(db.googleCacheDao().read("account-a"))
            assertEquals(listOf("account-b"), restarted.snapshots.value.map { it.profile.sub })
            assertEquals(cachedB, restarted.snapshots.value.single())
            assertEquals("account-b", sessions.accounts().single().profile.sub)
        } finally {
            client.close()
            db.close()
        }
    }

    @Test
    fun vaultWriteFailureDoesNotPublishConnectOrPretendSignOutSucceeded() = runTest {
        val db = database()
        val storage = InMemoryGoogleSecureStorage()
        val sessions = GoogleSessionStore(storage)
        val json = Json { ignoreUnknownKeys = true }
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("userinfo")) {
                respond("""{"sub":"account-a","email":"a@example.com"}""", headers = headers)
            } else error("Unexpected request ${request.url}")
        }) { install(ContentNegotiation) { json(json) } }
        try {
            val repo = GoogleCalendarRepository(GoogleCalendarApi(client), db, json)
            val factory = GoogleAuthFactory(sessions) { saved -> TestAuth(saved?.refreshToken ?: "restored") }
            repo.restore(factory)

            storage.failWrites = true
            val connectAuth = PersistingTestAuth("connect-token", sessions, "refresh-a")
            assertFailsWith<IllegalStateException> { repo.connect(connectAuth) }
            assertTrue(repo.snapshots.value.isEmpty())
            assertTrue(connectAuth.signedOut)

            storage.failWrites = false
            val cache = GoogleCacheEntity("account-a", json.encodeToString(GoogleSnapshot(GoogleProfile("account-a", "a@example.com"))))
            db.googleCacheDao().write(cache)
            val auth = PersistingTestAuth("connect-token", sessions, "refresh-a")
            assertEquals("account-a", repo.connect(auth))
            assertEquals("refresh-a", sessions.accounts().single().refreshToken)
            assertNull(db.googleCacheDao().read("account-a"))
            db.googleCacheDao().write(cache)

            storage.failWrites = true
            assertFailsWith<IllegalStateException> { repo.signOut("account-a") }
            assertEquals(listOf("account-a"), repo.snapshots.value.map { it.profile.sub })
            assertEquals(cache, db.googleCacheDao().read("account-a"))
            assertFalse(auth.signedOut)

            storage.failWrites = false
            db.useWriterConnection { connection ->
                connection.usePrepared(
                    "CREATE TRIGGER reject_cache_delete BEFORE DELETE ON google_calendar_cache " +
                        "BEGIN SELECT RAISE(ABORT, 'Cache deletion failed'); END",
                ) { statement -> statement.step() }
            }
            assertFails { repo.signOut("account-a") }
            assertEquals("account-a", sessions.accounts().single().profile.sub)
            assertEquals(listOf("account-a"), repo.snapshots.value.map { it.profile.sub })
            assertEquals(cache, db.googleCacheDao().read("account-a"))
            assertFalse(auth.signedOut)
        } finally {
            client.close()
            db.close()
        }
    }

    @Test
    fun unauthorizedRequestsInvalidateAndRetryOnceWhileOtherFailuresDoNotRetry() = runTest {
        val db = database()
        val json = Json { ignoreUnknownKeys = true }
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        var mode = "success"
        var calendarListRequests = 0
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("userinfo") ->
                    respond("""{"sub":"account-a","email":"a@example.com"}""", headers = headers)
                request.url.encodedPath.endsWith("calendarList") -> {
                    calendarListRequests++
                    when (mode) {
                        "once401" -> if (calendarListRequests == 1) respond("{}", HttpStatusCode.Unauthorized, headers)
                            else respond("""{"items":[]}""", headers = headers)
                        "serverError" -> respond("{}", HttpStatusCode.ServiceUnavailable, headers)
                        "always401" -> respond("{}", HttpStatusCode.Unauthorized, headers)
                        else -> respond("""{"items":[]}""", headers = headers)
                    }
                }
                else -> error("Unexpected request ${request.method} ${request.url}")
            }
        }) { install(ContentNegotiation) { json(json) } }
        try {
            val repo = GoogleCalendarRepository(GoogleCalendarApi(client), db, json)
            val auth = RetryTestAuth()
            assertEquals("account-a", repo.connect(auth))

            mode = "once401"
            calendarListRequests = 0
            repo.sync(0, 2_000)
            assertEquals(2, calendarListRequests)
            assertEquals(1, auth.invalidations)
            assertFalse("account-a" in repo.reauthRequiredAccounts.value)

            mode = "serverError"
            calendarListRequests = 0
            assertFailsWith<GoogleCalendarException> { repo.sync(0, 2_000) }
            assertEquals(1, calendarListRequests)
            assertEquals(1, auth.invalidations)

            mode = "always401"
            calendarListRequests = 0
            assertFailsWith<GoogleCalendarException> { repo.sync(0, 2_000) }
            assertEquals(2, calendarListRequests)
            assertEquals(2, auth.invalidations)
            assertEquals(setOf("account-a"), repo.reauthRequiredAccounts.value)
        } finally {
            client.close()
            db.close()
        }
    }

    @Test
    fun refreshIsAtomicRemovesDeletedEventsAndSignOutPurgesOnlyTheActiveAccount() = runTest {
        val db = database()
        val json = Json { ignoreUnknownKeys = true }
        var failLastCalendar = false
        var deleted = false
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("userinfo") -> respond("""{"sub":"account","email":"test@example.com"}""", headers = headers)
                request.url.encodedPath.endsWith("calendarList") -> respond("""{"items":[{"id":"first","accessRole":"owner"},{"id":"second"}]}""", headers = headers)
                request.url.encodedPath.contains("/second/") && failLastCalendar -> respond("{}", HttpStatusCode.ServiceUnavailable, headers)
                request.url.encodedPath.contains("/first/") && !deleted -> respond("""{"items":[{"id":"event","etag":"v1","start":{"date":"2026-11-01"},"end":{"date":"2026-11-02"}}]}""", headers = headers)
                else -> respond("""{"items":[]}""", headers = headers)
            }
        }) { install(ContentNegotiation) { json(json) } }
        val auth = TestAuth("single-token")
        try {
            db.googleCacheDao().write(GoogleCacheEntity("unrelated", "untouched"))
            val repo = GoogleCalendarRepository(GoogleCalendarApi(client), db, json)
            assertEquals("account", repo.connect(auth))
            repo.sync(0, 2_000)
            val initial = repo.snapshots.value.single()
            val cache = db.googleCacheDao().read("account")
            assertEquals(1, initial.calendars.first().events.size)
            val event = assertNotNull(initial.calendars.first().events.first().asEvent(initial.calendars.first().calendar, "account"))
            assertFailsWith<GoogleCalendarException> { repo.save(event.copy(remoteEtag = "older"), editing = true) }
            assertFailsWith<GoogleCalendarException> {
                repo.save(
                    event.copy(
                        id = "01234567-89ab-cdef-0123-456789abcdef",
                        calendarId = initial.calendars.last().calendar.localId("account"),
                    ),
                    editing = false,
                )
            }
            assertEquals(cache, db.googleCacheDao().read("account"))

            failLastCalendar = true
            deleted = true
            assertFailsWith<GoogleCalendarException> { repo.sync(0, 2_000) }
            assertEquals(initial, repo.snapshots.value.single())
            assertEquals(cache, db.googleCacheDao().read("account"))

            failLastCalendar = false
            repo.sync(0, 2_000)
            assertTrue(repo.snapshots.value.single().calendars.first().events.isEmpty())
            repo.signOut("account")
            assertTrue(auth.signedOut)
            assertTrue(repo.snapshots.value.isEmpty())
            assertNull(db.googleCacheDao().read("account"))
            assertEquals("untouched", db.googleCacheDao().read("unrelated")?.snapshot)
        } finally {
            client.close()
            db.close()
        }
    }

    @Test
    fun accountsWithTheSameRemoteIdsKeepTheirTokensCachesAndEventsIsolated() = runTest {
        val db = database()
        val json = Json { ignoreUnknownKeys = true }
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        val writes = mutableListOf<Pair<String, HttpMethod>>()
        var aWasUpdated = false
        val client = HttpClient(MockEngine { request ->
            val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
            val account = when (token) {
                "a-token", "a-reconnected-token" -> "account-a"
                "b-token" -> "account-b"
                else -> null
            }
            when {
                request.url.encodedPath.endsWith("userinfo") && token == "broken-token" ->
                    respond("{}", HttpStatusCode.ServiceUnavailable, headers)
                request.url.encodedPath.endsWith("userinfo") ->
                    respond("""{"sub":"$account","email":"$account@example.com"}""", headers = headers)
                request.url.encodedPath.endsWith("calendarList") ->
                    respond("""{"items":[{"id":"shared-calendar","accessRole":"owner"}]}""", headers = headers)
                request.method == HttpMethod.Post -> {
                    writes += requireNotNull(token) to request.method
                    val submitted = (request.body as TextContent).text
                    assertContains(submitted, "0123456789abcdef0123456789abcdef")
                    respond("""{"id":"0123456789abcdef0123456789abcdef","etag":"created-v1","summary":"Created on B","start":{"date":"2026-11-01"},"end":{"date":"2026-11-02"}}""", headers = headers)
                }
                request.method == HttpMethod.Patch -> {
                    writes += requireNotNull(token) to request.method
                    aWasUpdated = true
                    respond("""{"id":"same-event","etag":"updated-v2","summary":"Updated on A","start":{"date":"2026-11-01"},"end":{"date":"2026-11-02"}}""", headers = headers)
                }
                request.url.encodedPath.endsWith("/events") -> {
                    val title = if (account == "account-a") {
                        if (aWasUpdated) "Updated on A" else "A event"
                    } else "B event"
                    val etag = if (account == "account-a") "a-v1" else "b-v1"
                    respond("""{"items":[{"id":"same-event","etag":"$etag","summary":"$title","start":{"date":"2026-11-01"},"end":{"date":"2026-11-02"}}]}""", headers = headers)
                }
                else -> error("Unexpected request ${request.method} ${request.url}")
            }
        }) { install(ContentNegotiation) { json(json) } }
        val authA = TestAuth("a-token")
        val authB = TestAuth("b-token")
        try {
            val repo = GoogleCalendarRepository(GoogleCalendarApi(client), db, json)
            assertEquals("account-a", repo.connect(authA))
            assertEquals("account-b", repo.connect(authB))
            repo.sync(0, 2_000)

            val snapshots = repo.snapshots.value.associateBy { it.profile.sub }
            val a = assertNotNull(snapshots["account-a"])
            val b = assertNotNull(snapshots["account-b"])
            assertEquals(2, snapshots.size)
            assertEquals("shared-calendar", a.calendars.single().calendar.id)
            assertEquals("shared-calendar", b.calendars.single().calendar.id)
            assertNotEquals(a.calendars.single().calendar.localId("account-a"), b.calendars.single().calendar.localId("account-b"))
            assertEquals("same-event", a.calendars.single().events.single().id)
            assertEquals("same-event", b.calendars.single().events.single().id)
            assertNotEquals(
                a.calendars.single().events.single().localId(a.calendars.single().calendar, "account-a"),
                b.calendars.single().events.single().localId(b.calendars.single().calendar, "account-b"),
            )

            // A failed second login must not replace or clear already connected sessions.
            val failedAuth = TestAuth("broken-token")
            assertFailsWith<GoogleCalendarException> { repo.connect(failedAuth) }
            assertTrue(failedAuth.signedOut)
            assertEquals(setOf("account-a", "account-b"), repo.snapshots.value.map { it.profile.sub }.toSet())

            // Choosing B while reconnecting A cannot replace either existing session.
            val wrongAccount = TestAuth("b-token")
            assertFailsWith<GoogleReauthRequiredException> {
                repo.connect(wrongAccount, expectedAccountId = "account-a")
            }
            assertTrue(wrongAccount.signedOut)
            assertFalse(authA.signedOut)
            assertFalse(authB.signedOut)
            assertEquals(snapshots, repo.snapshots.value.associateBy { it.profile.sub })

            // Reconnecting the same account replaces its provider without duplicating or clearing its data.
            val replacementAuthA = TestAuth("a-reconnected-token")
            assertEquals("account-a", repo.connect(replacementAuthA, expectedAccountId = "account-a"))
            assertTrue(authA.signedOut)
            assertEquals(2, repo.snapshots.value.size)
            assertEquals("A event", repo.snapshots.value.single { it.profile.sub == "account-a" }.calendars.single().events.single().summary)
            repo.sync(0, 2_000, accountId = "account-a")

            val aEvent = assertNotNull(a.calendars.single().events.single().asEvent(a.calendars.single().calendar, "account-a"))
            repo.save(aEvent.copy(title = "Updated on A"), editing = true)
            assertEquals("a-reconnected-token" to HttpMethod.Patch, writes.last())
            val bCalendar = b.calendars.single().calendar
            val newBEvent = Event(
                id = "01234567-89ab-cdef-0123-456789abcdef",
                calendarId = bCalendar.localId("account-b"), calendarName = bCalendar.summary,
                title = "Created on B", startTime = 1_793_520_000_000, endTime = 1_793_606_400_000,
                isAllDay = true, color = bCalendar.color(),
            )
            repo.save(newBEvent, editing = false)
            assertEquals(listOf<Pair<String?, HttpMethod>>("a-reconnected-token" to HttpMethod.Patch, "b-token" to HttpMethod.Post), writes)

            val bCache = assertNotNull(db.googleCacheDao().read("account-b"))
            repo.signOut("account-b")
            assertTrue(authB.signedOut)
            assertFalse(replacementAuthA.signedOut)
            assertEquals(listOf("account-a"), repo.snapshots.value.map { it.profile.sub })
            assertNull(db.googleCacheDao().read("account-b"))
            assertNotNull(db.googleCacheDao().read("account-a"))
            assertTrue(Json.decodeFromString<GoogleSnapshot>(bCache.snapshot).calendars.single().events.any { it.summary == "B event" })
            assertEquals("Updated on A", repo.snapshots.value.single().calendars.single().events.single().summary)
            repo.sync(0, 2_000, accountId = "account-a")
            assertEquals("Updated on A", repo.snapshots.value.single().calendars.single().events.single().summary)
        } finally {
            client.close()
            db.close()
        }
    }

    @Test
    fun syncAllCommitsSuccessfulAccountsAndRetainsFailedAccountSnapshot() = runTest {
        val db = database()
        val json = Json { ignoreUnknownKeys = true }
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        var revision = 1
        var failB = false
        val client = HttpClient(MockEngine { request ->
            val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
            val account = if (token == "a-token") "account-a" else "account-b"
            when {
                request.url.encodedPath.endsWith("userinfo") ->
                    respond("""{"sub":"$account","email":"$account@example.com"}""", headers = headers)
                request.url.encodedPath.endsWith("calendarList") ->
                    respond("""{"items":[{"id":"calendar-$account","accessRole":"owner"}]}""", headers = headers)
                request.url.encodedPath.endsWith("/events") && account == "account-b" && failB ->
                    respond("{}", HttpStatusCode.ServiceUnavailable, headers)
                request.url.encodedPath.endsWith("/events") -> {
                    val title = "${if (account == "account-a") "A" else "B"}-$revision"
                    respond("""{"items":[{"id":"event-$account","etag":"v$revision","summary":"$title","start":{"date":"2026-11-01"},"end":{"date":"2026-11-02"}}]}""", headers = headers)
                }
                else -> error("Unexpected request ${request.method} ${request.url}")
            }
        }) { install(ContentNegotiation) { json(json) } }
        try {
            val repo = GoogleCalendarRepository(GoogleCalendarApi(client), db, json)
            val authB = TestAuth("b-token")
            repo.connect(authB)
            // Put the failing account first to verify later accounts still refresh.
            repo.connect(TestAuth("a-token"))
            repo.sync(0, 2_000)
            val originalB = repo.snapshots.value.single { it.profile.sub == "account-b" }
            val originalBCache = db.googleCacheDao().read("account-b")

            revision = 2
            failB = true
            assertFailsWith<GoogleCalendarException> { repo.sync(0, 2_000) }

            val after = repo.snapshots.value.associateBy { it.profile.sub }
            assertEquals("A-2", after.getValue("account-a").calendars.single().events.single().summary)
            assertEquals(originalB, after.getValue("account-b"))
            assertEquals(originalBCache, db.googleCacheDao().read("account-b"))
            assertContains(repo.syncErrors.value.getValue("account-b"), "unavailable")
            assertFalse("account-a" in repo.syncErrors.value)

            failB = false
            repo.sync(0, 2_000)
            assertEquals("B-2", repo.snapshots.value.single { it.profile.sub == "account-b" }.calendars.single().events.single().summary)
            assertTrue(repo.syncErrors.value.isEmpty())

            failB = true
            assertFailsWith<GoogleCalendarException> { repo.sync(0, 2_000) }
            repo.signOut("account-b")
            assertTrue(authB.signedOut)
            assertTrue(repo.syncErrors.value.isEmpty())
            assertNull(db.googleCacheDao().read("account-b"))
            assertEquals(listOf("account-a"), repo.snapshots.value.map { it.profile.sub })
        } finally {
            client.close()
            db.close()
        }
    }

    private fun database() = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private class TestAuth(private val token: String) : GoogleAuth {
        var signedOut = false
        override suspend fun accessToken(interactive: Boolean) = token
        override fun signOut() { signedOut = true }
    }

    private class InMemoryGoogleSecureStorage : GoogleSecureStorage {
        private var value: String? = null
        var failReads = false
        var failWrites = false

        override suspend fun read(): String? {
            if (failReads) throw IllegalStateException("Vault read failed")
            return value
        }
        override suspend fun write(value: String) {
            if (failWrites) throw IllegalStateException("Vault write failed")
            this.value = value
        }
    }

    private class PersistingTestAuth(
        private val token: String,
        private val sessions: GoogleSessionStore,
        private val refreshToken: String,
    ) : GoogleAuth {
        var signedOut = false
        override suspend fun accessToken(interactive: Boolean) = token
        override fun signOut() { signedOut = true }
        override suspend fun saveSession(profile: GoogleProfile) {
            sessions.upsert(GoogleSavedAccount(profile, "client-id", refreshToken))
        }
    }

    private class RetryTestAuth : GoogleAuth {
        var invalidations = 0
        override suspend fun accessToken(interactive: Boolean) = if (invalidations == 0) "old-token" else "fresh-token"
        override fun signOut() = Unit
        override suspend fun invalidateAccessToken() { invalidations++ }
    }
}
