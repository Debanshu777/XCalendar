# XCalendar state machine review

XCalendar needs to remember which calendar view is active, which event the user is editing, and which Google accounts and date ranges are available. The cleanup removes duplicate or unused state while keeping the information each workflow needs to recover from cancellation, retry, or restart.

## 1. Resolve the calendar source before enabling edits

Google session discovery can be pending, ready, or failed. The restored account list determines whether Google or demo data is active. Add and Edit become available when discovery succeeds and the displayed accounts match the active accounts. If discovery fails, the calendar is available for browsing and the account dialog offers a retry. This closes the startup path that could save to the demo calendar while a saved Google account was still loading.

[App and editor entry points](../app/shared/src/com/debanshu/xcalendar/CalendarApp.kt) · [Session state](../app/shared/src/com/debanshu/xcalendar/ui/google/GoogleCalendarViewModel.kt)

## 2. Make Google operations and cache commits consistent

One operation value represents restore, connect, refresh, save, or sign-out. Busy status and cancellation availability follow from that value. A single pending refresh request gives manual refresh priority over a deferred coverage check.

Sign-out stages the cache deletion in a Room transaction before removing the secure session. A cache deletion failure leaves the session available. A secure-storage failure rolls back the cache deletion. Once secure removal succeeds, the app clears the live provider, snapshot, and visibility settings even if Room reports a commit failure. During restore, a successful vault read allows the app to remove cache rows for accounts absent from the vault. Fresh connections discard any old cache for that account before saving the new session.

Sync rechecks the requested month within the cache transaction and rolls back if the month became obsolete while the write was in flight. It checks again before publishing to the UI. Accounts still sync independently, with their previous snapshot available after a fetch failure.

[Operation scheduling](../app/shared/src/com/debanshu/xcalendar/ui/google/GoogleCalendarViewModel.kt) · [Account and cache transitions](../app/shared/src/com/debanshu/xcalendar/data/google/GoogleCalendarRepository.kt)

## 3. Preserve the Google performance changes

The persisted fetch window extends one year in each direction from the viewed month and includes the boundary month grids’ overflow days. Navigation inside the covered window reuses cached results. Automatic refresh reuses a covered snapshot younger than 15 minutes. Manual sync still fetches. Cache metadata changes continue to avoid rebuilding displayed calendar content.

[Window and freshness rules](../app/shared/src/com/debanshu/xcalendar/ui/google/GoogleCalendarCoverage.kt) · [Content projection](../app/shared/src/com/debanshu/xcalendar/ui/google/GoogleCalendarContent.kt)

## 4. Remove duplicate editor and startup state

One nullable editor value represents closed, create, or edit. The selected event still lives in the event ViewModel, so canceling an edit returns to its details. The submission guard and retryable draft stay in place because they protect against duplicate writes and lost edits. The calendar ViewModel retains its observed lists and error, and drops its unused selection, loading, and derived empty state.

[Editor transitions](../app/shared/src/com/debanshu/xcalendar/CalendarApp.kt) · [Local submission guard](../app/shared/src/com/debanshu/xcalendar/ui/viewmodel/EventViewModel.kt) · [Calendar UI state](../app/shared/src/com/debanshu/xcalendar/domain/states/CalendarUiState.kt)

## 5. Simplify navigation and schedule pagination

The back stack preserves its root. The month dropdown uses a single expanded flag. Schedule pagination uses the measured list edges and stable row keys to extend the range and retain its scroll position. This removes pagination locks, fixed delays, and the previous range offsets. The selected date and viewed month remain independent so a month swipe can move the calendar without changing the date used to start a draft.

[Back stack](../app/shared/src/com/debanshu/xcalendar/ui/navigation/NavigationExtensions.kt) · [Month dropdown](../app/shared/src/com/debanshu/xcalendar/ui/components/CalendarTopAppBar.kt) · [Schedule pagination](../app/shared/src/com/debanshu/xcalendar/ui/screen/scheduleScreen/ScheduleScreen.kt) · [Date state](../app/shared/src/com/debanshu/xcalendar/ui/state/DateStateHolder.kt)

## 6. Simplify demo persistence and holiday caching

The old Store5 timestamp tracker recorded fetches without affecting either store’s read decisions. Holiday reads still request refresh and validate nonempty cached results. The demo range store reads and imports seeds without a write updater or sync failure bookkeeper. Individual event writes still use the existing local path.

Room records a locally deleted event ID in the same transaction as the deletion. Future demo imports skip that ID, including after restart. Schema version 3 includes the deletion table. During pre-release development, older schema versions are recreated rather than migrated. The repository delete path still has no UI entry point.

[Demo event store](../app/shared/src/com/debanshu/xcalendar/data/store/EventStore.kt) · [Holiday store](../app/shared/src/com/debanshu/xcalendar/data/store/HolidayStore.kt)

## State the current app still needs

- Google credentials and per-account sync health represent separate facts. An account can require reconnect while its cached calendar is usable. Android also needs to associate a late Activity result with its original sign-in request. [Browser authorization](../app/shared/src/com/debanshu/xcalendar/data/google/GoogleAuth.kt) · [Android authorization](../app/shared/src@android/com/debanshu/xcalendar/data/google/GoogleAuth.android.kt)
- The pager needs its anchor to translate pages back into dates, and the bottom navigation indicator needs its explicit drag, click, and settling phases. [Pager](../app/shared/src/com/debanshu/xcalendar/ui/components/SwipeablePager.kt) · [Indicator](../app/shared/src/com/debanshu/xcalendar/ui/components/CalendarBottomNavigationBar.kt)
- Google calendar visibility remains a user-controlled filter. The visible event list and edit eligibility are computed from the available calendars, events, and permissions. Stable create IDs and edit ETags protect against duplicate creates and conflicting updates. [Calendar content](../app/shared/src/com/debanshu/xcalendar/ui/google/GoogleCalendarContent.kt) · [Google writes](../app/shared/src/com/debanshu/xcalendar/data/google/GoogleCalendarApi.kt)

## Verification

- `./kotlin test -m shared -p jvm -p android -p iosSimulatorArm64` passed. JVM ran 127 tests and Android ran 104. The persistence cases cover obsolete snapshot rollback, recovery after interrupted sign-out, cache reuse after restart, and a deleted seed that stays deleted after restart. The migration-specific setup was subsequently removed for the pre-release database policy.
- The iOS test runner skipped the native Keychain round-trip because it lacks the Keychain entitlement. Live Google sign-in and restart still need verification in a signed app with a test account.
- `./kotlin build -m desktopApp -m androidApp -m iosApp -p jvm -p android -p iosSimulatorArm64` passed.
- On Android, the Month view rendered, Schedule paginated from November 2026 back to October 2025, and canceling the event editor returned to the same visible month. The smoke test used a temporary app ID because the installed copy has a different signature. The temporary app was removed after the crash check.
- The updated iOS Simulator build launched and rendered the Month view.
