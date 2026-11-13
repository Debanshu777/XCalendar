package com.debanshu.xcalendar.ui.google

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.data.google.GoogleSnapshot
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
internal fun GoogleAccountDialog(
    snapshots: List<GoogleSnapshot>, state: GoogleUiState,
    hiddenCalendars: Set<String>, syncErrors: Map<String, String>, reauthRequiredAccounts: Set<String>,
    onConnect: () -> Unit, onRefresh: () -> Unit, onSignOut: (String) -> Unit,
    onReconnect: (String) -> Unit, onRestore: () -> Unit,
    onCalendarVisibilityChange: (String, Boolean) -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.saving) onDismiss() },
        title = { Text("Google Calendar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (snapshots.isEmpty()) {
                    Text("Connect your Google account to see your calendars and create or edit events.")
                    Text("You choose whether to grant calendar access. Your demo events stay separate.")
                    Text("Google handles sign-in and two-step verification. XCalendar remembers your accounts securely.")
                } else {
                    Text("Choose which calendars appear together. Events save through the account shown with the calendar you select.")
                    snapshots.forEachIndexed { index, snapshot ->
                        if (index > 0) HorizontalDivider()
                        Text(snapshot.profile.name.ifBlank { snapshot.profile.email }, style = MaterialTheme.typography.titleMedium)
                        Text(snapshot.profile.email)
                        Text(if (snapshot.syncedAt == 0L) "Your calendars haven't synced yet." else
                            "Last synced: ${Instant.fromEpochMilliseconds(snapshot.syncedAt).toLocalDateTime(TimeZone.currentSystemDefault()).toString().replace('T', ' ').substringBefore('.')}")
                        syncErrors[snapshot.profile.sub]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (snapshot.profile.sub in reauthRequiredAccounts) {
                            TextButton(onClick = { onReconnect(snapshot.profile.sub) }, enabled = !state.busy) {
                                Text("Reconnect account")
                            }
                        }
                        snapshot.calendars.forEach { entry ->
                            val id = entry.calendar.localId(snapshot.profile.sub)
                            val visible = id !in hiddenCalendars
                            Row(
                                Modifier.fillMaxWidth().toggleable(
                                    value = visible, role = Role.Checkbox,
                                    onValueChange = { onCalendarVisibilityChange(id, it) },
                                ).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = visible, onCheckedChange = null)
                                Text(entry.calendar.summary + if (entry.calendar.writable) "" else " · Read only",
                                    modifier = Modifier.padding(start = 8.dp).weight(1f))
                            }
                        }
                        TextButton(onClick = { onSignOut(snapshot.profile.sub) }, enabled = !state.busy) {
                            Text("Remove account")
                        }
                    }
                    Text("Syncs the month you're viewing and adjacent months. Changes sync while XCalendar is open; saving requires a connection.")
                    Text("Accounts reconnect automatically after restarting. Removing an account clears its saved sign-in and cached calendars from this device.")
                    TextButton(onClick = onConnect, enabled = !state.busy) { Text("Add Google account") }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.restoring) Text("Restoring saved accounts…")
                if (state.sessionStatus == GoogleSessionStatus.Failed) {
                    TextButton(onClick = onRestore, enabled = !state.busy) { Text("Retry saved sign-in") }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = if (snapshots.isEmpty()) onConnect else onRefresh, enabled = !state.busy) {
            Text(if (snapshots.isEmpty()) "Connect Google Calendar" else "Sync all")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.saving) { Text(if (state.canCancel) "Cancel" else "Close") } },
    )
}
