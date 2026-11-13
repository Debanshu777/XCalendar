package com.debanshu.xcalendar.data.google

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.debanshu.xcalendar.common.AppLogger
import com.debanshu.xcalendar.data.localDataSource.AppDatabase
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.util.EventValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.coroutines.coroutineContext

@Single
internal class GoogleCalendarRepository(
    private val api: GoogleCalendarApi,
    private val database: AppDatabase,
    private val json: Json,
) {
    private val mutex = Mutex()
    private val providers = mutableMapOf<String, GoogleAuth>()
    private var sessionStore: GoogleSessionStore? = null
    private val mutableSnapshots = MutableStateFlow<List<GoogleSnapshot>>(emptyList())
    val snapshots = mutableSnapshots.asStateFlow()
    private val mutableSyncErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val syncErrors = mutableSyncErrors.asStateFlow()
    private val mutableReauthRequiredAccounts = MutableStateFlow<Set<String>>(emptySet())
    val reauthRequiredAccounts = mutableReauthRequiredAccounts.asStateFlow()
    private val pendingCacheWrites = MutableStateFlow<Set<String>>(emptySet())
    val hasPendingCacheWrites: Boolean get() = pendingCacheWrites.value.isNotEmpty()

    suspend fun retryPendingCacheWrites() = mutex.withLock {
        mutableSnapshots.value.filter { it.profile.sub in pendingCacheWrites.value }.forEach {
            persistPublishedSnapshot(it)
        }
    }

    /** Restore identity and cached calendars even offline; the next sync refreshes tokens. */
    suspend fun restore(factory: GoogleAuthFactory) = mutex.withLock {
        val saved = factory.sessions.accounts()
        val cache = database.googleCacheDao()
        // Recover a cache deletion interrupted after its secure session was removed.
        cache.deleteUnowned(saved.map { it.profile.sub })
        val restored = saved.filterNot { providers.containsKey(it.profile.sub) }.map { account ->
            val cached = cache.read(account.profile.sub)?.let { entry ->
                withContext(Dispatchers.Default) {
                    try { json.decodeFromString<GoogleSnapshot>(entry.snapshot) }
                    catch (_: IllegalArgumentException) { null }
                }
            }
            val snapshot = cached?.takeIf { it.profile.sub == account.profile.sub }?.copy(profile = account.profile)
                ?: GoogleSnapshot(account.profile)
            snapshot to factory.create(account)
        }
        sessionStore = factory.sessions
        restored.forEach { (snapshot, provider) ->
            providers[snapshot.profile.sub] = provider
            replaceSnapshot(snapshot)
        }
    }

    suspend fun connect(provider: GoogleAuth, expectedAccountId: String? = null): String = mutex.withLock {
        try {
            // Open Google's sign-in/2FA UI only for an explicit Connect action.
            val profile = try { api.profile(provider.accessToken(interactive = true)) }
            catch (_: GoogleAccessTokenRejectedException) { throw GoogleReauthRequiredException() }
            require(profile.sub.isNotBlank())
            if (expectedAccountId != null && profile.sub != expectedAccountId) {
                throw GoogleReauthRequiredException("Choose the same Google account to reconnect it. Your existing accounts are unchanged.")
            }
            provider.bindAccount(profile.email)
            val existing = mutableSnapshots.value.find { it.profile.sub == profile.sub }
            coroutineContext.ensureActive()
            withContext(NonCancellable) {
                // A fresh connection must start with data from its own session.
                if (existing == null) database.googleCacheDao().delete(profile.sub)
                provider.saveSession(profile)
                val previous = providers.put(profile.sub, provider)
                if (previous !== provider) previous?.signOut()
                replaceSnapshot(existing?.copy(profile = profile) ?: GoogleSnapshot(profile))
                mutableSyncErrors.value -= profile.sub
                mutableReauthRequiredAccounts.value -= profile.sub
            }
            profile.sub
        } catch (e: Exception) {
            if (providers.values.none { it === provider }) provider.signOut()
            throw e
        }
    }

    suspend fun sync(start: Long, end: Long, accountId: String? = null, acceptResult: () -> Boolean = { true }) = mutex.withLock {
        require(start < end)
        val accounts = mutableSnapshots.value.filter { accountId == null || it.profile.sub == accountId }
        val failures = mutableListOf<String>()
        for (current in accounts) {
            val id = current.profile.sub
            try {
                val provider = requireNotNull(providers[id])
                val data = withFreshAccessToken(provider) {
                    val calendars = api.calendars { provider.accessToken() }
                    calendars.map { calendar ->
                        GoogleCalendarData(calendar, api.events({ provider.accessToken() }, calendar.id, start, end))
                    }
                }
                // Every page must succeed for this account. Other accounts can still
                // refresh when one account's connection or permissions fail.
                if (!acceptResult()) return@withLock
                if (!commit(GoogleSnapshot(current.profile, data, Clock.System.now().toEpochMilliseconds(), start, end), acceptResult)) return@withLock
                mutableSyncErrors.value -= id
                mutableReauthRequiredAccounts.value -= id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!acceptResult()) return@withLock
                if (e is GoogleReauthRequiredException) mutableReauthRequiredAccounts.value += id
                val message = if (e is GoogleCalendarException) e.message.orEmpty()
                    else "Could not reach Google Calendar. Check your connection and try again."
                mutableSyncErrors.value += id to message
                failures += "${current.profile.email.ifBlank { current.profile.name.ifBlank { "Google account" } }}: $message"
            }
        }
        if (failures.isNotEmpty()) throw GoogleCalendarException(failures.joinToString("\n"))
    }

    suspend fun save(event: Event, editing: Boolean): Unit = mutex.withLock {
        EventValidator.validate(event.title, event.startTime, event.endTime, event.calendarId, event.isAllDay)
        val current = mutableSnapshots.value.find { snapshot ->
            snapshot.calendars.any { it.calendar.localId(snapshot.profile.sub) == event.calendarId }
        } ?: throw GoogleCalendarException("This calendar is no longer available. Sync and try again.")
        val account = current.profile.sub
        val data = current.calendars.find { it.calendar.localId(account) == event.calendarId }
            ?: throw GoogleCalendarException("This calendar is no longer available. Sync and try again.")
        if (!data.calendar.writable) throw GoogleCalendarException("This calendar is read-only.")
        val original = if (editing) current.calendars.flatMap { entry -> entry.events.map { entry.calendar to it } }
            .find { (calendar, value) -> value.localId(calendar, account) == event.id } else null
        if (editing && original == null) throw GoogleCalendarException("This event is no longer available. Sync and reopen it.")
        if (original != null && original.first.id != data.calendar.id) throw GoogleCalendarException("Moving Google events between calendars is not supported yet.")
        if (original != null && (original.second.eventType != "default" || original.second.etag.isBlank())) {
            throw GoogleCalendarException("Edit this event in Google Calendar.")
        }
        if (original != null && original.second.etag != event.remoteEtag) throw GoogleEventConflictException()
        val saved = withEventAccess(account) { accessToken ->
            if (original != null) {
                api.update(accessToken, data.calendar.id, original.second, GoogleEventWrite.from(event, original.second, data.calendar))
            } else {
                // UUID hex is a subset of Google's base32hex event ID alphabet.
                val remoteId = event.id.replace("-", "").lowercase()
                require(remoteId.length == 32 && remoteId.all { it in '0'..'9' || it in 'a'..'f' })
                api.create(accessToken, data.calendar.id, GoogleEventWrite.from(event, id = remoteId))
            }
        }
        publishEvent(current, data.calendar, saved)
    }

    suspend fun loadLatestEvent(event: Event): Event = mutex.withLock {
        val account = mutableSnapshots.value.find { snapshot ->
            snapshot.calendars.any { it.calendar.localId(snapshot.profile.sub) == event.calendarId }
        } ?: throw GoogleCalendarException("This calendar is no longer available.")
        val accountId = account.profile.sub
        val data = account.calendars.first { it.calendar.localId(accountId) == event.calendarId }
        val original = data.events.find { it.localId(data.calendar, accountId) == event.id }
            ?: throw GoogleCalendarException("This event is no longer available.")
        val latest = withEventAccess(accountId) { api.event(it, data.calendar.id, original.id) }
        val review = latest.takeIf { it.status != "cancelled" && it.eventType == "default" && it.etag.isNotBlank() }
            ?.asEvent(data.calendar, accountId)
            ?: throw GoogleCalendarException("This event is no longer available for editing.")
        publishEvent(account, data.calendar, latest)
        review
    }

    private suspend fun <T> withEventAccess(account: String, request: suspend (String) -> T): T {
        val provider = requireNotNull(providers[account])
        return try {
            withFreshAccessToken(provider) { request(provider.accessToken()) }
        } catch (e: GoogleReauthRequiredException) {
            mutableReauthRequiredAccounts.value += account
            mutableSyncErrors.value += account to e.message.orEmpty()
            throw e
        }
    }

    private suspend fun publishEvent(current: GoogleSnapshot, calendar: GoogleCalendar, event: GoogleEvent) {
        val updated = current.copy(calendars = current.calendars.map { entry ->
            if (entry.calendar.id != calendar.id) entry
            else entry.copy(events = entry.events.filterNot { it.id == event.id } + event)
        })
        pendingCacheWrites.value += current.profile.sub
        replaceSnapshot(updated)
        persistPublishedSnapshot(updated)
    }

    private suspend fun persistPublishedSnapshot(value: GoogleSnapshot) {
        try {
            persistSnapshot(value) { true }
            pendingCacheWrites.value -= value.profile.sub
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppLogger.w { "Could not persist the updated Google Calendar cache. Will retry." }
        }
    }

    fun canEdit(event: Event): Boolean {
        return snapshots.value.any { current ->
            current.calendars.any { entry -> entry.calendar.writable && entry.events.any {
                it.localId(entry.calendar, current.profile.sub) == event.id && it.eventType == "default" && it.etag.isNotBlank()
            } }
        }
    }

    suspend fun signOut(accountId: String) = mutex.withLock {
        withContext(NonCancellable) {
            var sessionRemoved = false
            try {
                database.useWriterConnection { connection ->
                    connection.immediateTransaction {
                        database.googleCacheDao().delete(accountId)
                        // Roll back the cache deletion if secure storage cannot remove the session.
                        sessionStore?.remove(accountId)
                        sessionRemoved = true
                    }
                }
            } finally {
                if (sessionRemoved) {
                    providers.remove(accountId)?.signOut()
                    mutableSnapshots.value = mutableSnapshots.value.filterNot { it.profile.sub == accountId }
                    mutableSyncErrors.value -= accountId
                    mutableReauthRequiredAccounts.value -= accountId
                    pendingCacheWrites.value -= accountId
                }
            }
        }
    }

    private suspend fun <T> withFreshAccessToken(provider: GoogleAuth, request: suspend () -> T): T {
        return try { request() }
        catch (_: GoogleAccessTokenRejectedException) {
            provider.invalidateAccessToken()
            try { request() }
            catch (_: GoogleAccessTokenRejectedException) { throw GoogleReauthRequiredException() }
        }
    }

    private suspend fun commit(value: GoogleSnapshot, acceptResult: () -> Boolean = { true }): Boolean {
        if (!persistSnapshot(value, acceptResult) || !acceptResult()) return false
        pendingCacheWrites.value -= value.profile.sub
        replaceSnapshot(value)
        return true
    }

    private suspend fun persistSnapshot(value: GoogleSnapshot, acceptResult: () -> Boolean): Boolean {
        val encoded = withContext(Dispatchers.Default) { json.encodeToString(value) }
        return database.useWriterConnection { connection ->
            connection.immediateTransaction {
                if (!acceptResult()) return@immediateTransaction false
                database.googleCacheDao().write(GoogleCacheEntity(value.profile.sub, encoded))
                if (!acceptResult()) rollback(false)
                true
            }
        }
    }

    private fun replaceSnapshot(value: GoogleSnapshot) {
        val current = mutableSnapshots.value
        mutableSnapshots.value = if (current.any { it.profile.sub == value.profile.sub }) {
            current.map { if (it.profile.sub == value.profile.sub) value else it }
        } else current + value
    }
}
