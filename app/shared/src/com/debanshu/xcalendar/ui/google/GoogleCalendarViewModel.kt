package com.debanshu.xcalendar.ui.google

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.data.google.*
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.util.EventValidationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Clock

internal enum class GoogleSessionStatus { Pending, Ready, Failed }

internal enum class GoogleOperation(val canCancel: Boolean) {
    Restore(false), Connect(true), Refresh(true), Save(false), LoadEvent(false), SignOut(false),
}

internal enum class GoogleEditorAction { Save, Reconnect, LoadLatest }

internal data class GoogleEditorState(
    val action: GoogleEditorAction? = null,
    val error: String? = null,
    val hasConflict: Boolean = false,
    val latestEvent: Event? = null,
)

internal data class GoogleUiState(
    val operation: GoogleOperation? = null,
    val sessionStatus: GoogleSessionStatus = GoogleSessionStatus.Pending,
    val error: String? = null,
) {
    val busy: Boolean get() = operation != null
    val saving: Boolean get() = operation == GoogleOperation.Save
    val restoring: Boolean get() = operation == GoogleOperation.Restore
    val canCancel: Boolean get() = operation?.canCancel == true
}

@KoinViewModel
internal class GoogleCalendarViewModel(private val repository: GoogleCalendarRepository) : ViewModel() {
    val snapshots = repository.snapshots
    val syncErrors = repository.syncErrors
    val reauthRequiredAccounts = repository.reauthRequiredAccounts
    private val mutableHiddenCalendars = MutableStateFlow<Set<String>>(emptySet())
    val hiddenCalendars = mutableHiddenCalendars.asStateFlow()
    val content = snapshots.calendarContent().combine(hiddenCalendars) { content, hidden ->
        content?.withHiddenCalendars(hidden)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val mutableState = MutableStateFlow(GoogleUiState())
    val state = mutableState.asStateFlow()
    private val mutableEditorState = MutableStateFlow(GoogleEditorState())
    val editorState = mutableEditorState.asStateFlow()
    private var activeOperation: Job? = null
    private enum class RefreshRequest { CheckCoverage, Force }
    private var pendingRefresh: RefreshRequest? = null
    private var authFactory: GoogleAuthFactory? = null
    private val month = MutableStateFlow(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.let { LocalDate(it.year, it.month, 1) }
    )

    fun selectMonth(year: Int, monthNumber: Int) {
        val next = LocalDate(year, monthNumber, 1)
        if (next == month.value) return
        month.value = next
        ensureVisibleRange()
    }

    private fun ensureVisibleRange() {
        // An in-flight request may replace the cache with a different window.
        // Recheck the latest selected month after it finishes, even if covered now.
        if (state.value.busy) {
            if (pendingRefresh == null) pendingRefresh = RefreshRequest.CheckCoverage
            return
        }
        if (state.value.sessionStatus != GoogleSessionStatus.Ready ||
            !snapshots.value.coversMonthGrid(month.value, TimeZone.currentSystemDefault())
        ) refresh()
    }

    fun restore(factory: GoogleAuthFactory) {
        authFactory = factory
        if (state.value.sessionStatus == GoogleSessionStatus.Ready) return
        operation(GoogleOperation.Restore) {
            restoreSessions(factory)
            if (needsAutomaticRefresh()) syncVisibleRange()
        }
    }

    private suspend fun restoreSessions(factory: GoogleAuthFactory) {
        if (state.value.sessionStatus == GoogleSessionStatus.Ready) return
        try {
            repository.restore(factory)
            mutableState.update { it.copy(sessionStatus = GoogleSessionStatus.Ready) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutableState.update { it.copy(sessionStatus = GoogleSessionStatus.Failed) }
            throw e
        }
    }

    fun connect(factory: GoogleAuthFactory, expectedAccountId: String? = null) = operation(GoogleOperation.Connect) {
        syncVisibleRange(connectAccount(factory, expectedAccountId))
    }

    private suspend fun connectAccount(factory: GoogleAuthFactory, expectedAccountId: String?): String {
        authFactory = factory
        restoreSessions(factory)
        return repository.connect(factory.create(), expectedAccountId)
    }

    fun refresh() {
        if (state.value.sessionStatus != GoogleSessionStatus.Ready) { authFactory?.let(::restore); return }
        if (snapshots.value.isEmpty()) return
        if (state.value.busy) { pendingRefresh = RefreshRequest.Force; return }
        operation(GoogleOperation.Refresh) { syncVisibleRange() }
    }

    private fun needsAutomaticRefresh(): Boolean =
        !snapshots.value.coversMonthGrid(month.value, TimeZone.currentSystemDefault()) ||
            !snapshots.value.isFresh(Clock.System.now().toEpochMilliseconds())

    fun refreshIfNeeded() {
        if (state.value.busy) return
        if (needsAutomaticRefresh()) refresh()
        else if (repository.hasPendingCacheWrites) {
            operation(GoogleOperation.Refresh) { repository.retryPendingCacheWrites() }
        }
    }

    fun cancel() { if (state.value.canCancel) activeOperation?.cancel() }

    private suspend fun syncVisibleRange(accountId: String? = null) {
        repository.retryPendingCacheWrites()
        val zone = TimeZone.currentSystemDefault()
        val requestedMonth = month.value
        val (start, end) = monthSyncWindow(requestedMonth, zone)
        repository.sync(start, end, accountId) {
            val visibleMonth = month.value
            if (visibleMonth == requestedMonth) {
                true
            } else {
                val (visibleStart, visibleEnd) = monthGridBounds(visibleMonth, zone)
                start <= visibleStart && end >= visibleEnd
            }
        }
    }

    fun save(event: Event, editing: Boolean, onSuccess: () -> Unit) =
        operation(GoogleOperation.Save, GoogleEditorAction.Save) {
            val latest = editorState.value.latestEvent?.takeIf { it.id == event.id }
            if (editorState.value.hasConflict && latest == null) throw GoogleEventConflictException()
            repository.save(if (latest == null) event else event.copy(remoteEtag = latest.remoteEtag), editing)
            onSuccess()
        }

    fun reconnectEditor(factory: GoogleAuthFactory, accountId: String) =
        operation(GoogleOperation.Connect, GoogleEditorAction.Reconnect) {
            connectAccount(factory, accountId)
        }

    fun loadLatestEvent(event: Event, onLoaded: (Event) -> Unit) =
        operation(GoogleOperation.LoadEvent, GoogleEditorAction.LoadLatest) {
            val latest = repository.loadLatestEvent(event)
            mutableEditorState.update { it.copy(latestEvent = latest) }
            onLoaded(latest)
        }

    fun clearEditorState() {
        mutableEditorState.value = GoogleEditorState()
    }

    fun signOut(accountId: String, onSuccess: () -> Unit) = operation(GoogleOperation.SignOut) {
        try {
            repository.signOut(accountId)
        } finally {
            if (snapshots.value.none { it.profile.sub == accountId }) {
                mutableHiddenCalendars.update { ids -> ids.filterNot { it.startsWith("google:$accountId:") }.toSet() }
                onSuccess()
            }
        }
    }
    fun setCalendarVisible(calendarId: String, visible: Boolean) {
        mutableHiddenCalendars.update { if (visible) it - calendarId else it + calendarId }
    }
    fun canEdit(event: Event) = repository.canEdit(event)
    fun clearError() { mutableState.update { it.copy(error = null) } }

    private fun operation(
        operation: GoogleOperation,
        editorAction: GoogleEditorAction? = null,
        block: suspend () -> Unit,
    ) {
        if (state.value.busy) return
        mutableState.update { it.copy(operation = operation, error = it.error.takeIf { editorAction != null }) }
        if (editorAction != null) mutableEditorState.update { it.copy(action = editorAction, error = null) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { block() }
            catch (_: TimeoutCancellationException) {
                reportError(editorAction, "Google sign-in timed out. Please try again.")
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = when (e) {
                    is GoogleCalendarException, is EventValidationException -> e.message
                    else -> "Could not reach Google Calendar. Check your connection and try again."
                }
                reportError(editorAction, message, conflict = e is GoogleEventConflictException)
            } finally {
                if (editorAction != null) mutableEditorState.update { it.copy(action = null) }
                activeOperation = null
                val requested = pendingRefresh
                pendingRefresh = null
                mutableState.update { it.copy(operation = null) }
                when (requested) {
                    RefreshRequest.Force -> refresh()
                    RefreshRequest.CheckCoverage -> ensureVisibleRange()
                    null -> Unit
                }
            }
        }
        activeOperation = job
        job.start()
    }

    private fun reportError(editorAction: GoogleEditorAction?, message: String?, conflict: Boolean = false) {
        if (editorAction == null) {
            mutableState.update { it.copy(error = message) }
        } else {
            mutableEditorState.update {
                it.copy(
                    error = message,
                    hasConflict = it.hasConflict || conflict,
                    latestEvent = it.latestEvent.takeUnless { conflict },
                )
            }
        }
    }
}
