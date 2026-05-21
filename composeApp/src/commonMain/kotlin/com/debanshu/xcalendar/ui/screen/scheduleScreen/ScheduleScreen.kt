package com.debanshu.xcalendar.ui.screen.scheduleScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.state.DateStateHolder
import com.debanshu.xcalendar.ui.model.EventsByDate
import com.debanshu.xcalendar.ui.model.HolidaysByDate
import com.debanshu.xcalendar.ui.state.ScheduleStateHolder
import com.debanshu.xcalendar.ui.screen.scheduleScreen.components.DayWithEvents
import com.debanshu.xcalendar.ui.screen.scheduleScreen.components.MonthHeader
import com.debanshu.xcalendar.ui.screen.scheduleScreen.components.WeekHeader
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Snapshot of the LazyColumn viewport relevant to schedule pagination + header
 * tracking. Kept as a single record so `snapshotFlow { … }` returns one stream
 * we can debounce + dedupe in one place (replaces three parallel collectors).
 */
private data class ViewportState(
    val firstVisibleIndex: Int,
    val lastVisibleIndex: Int,
    val totalItems: Int,
    val firstVisibleMonthHeaderId: String?,
)

@OptIn(FlowPreview::class)
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    dateStateHolder: DateStateHolder,
    eventsByDate: EventsByDate,
    holidaysByDate: HolidaysByDate,
    isVisible: Boolean = true,
    onEventClick: (Event) -> Unit,
) {
    val dateState by dateStateHolder.currentDateState.collectAsStateWithLifecycle()
    val currentDate = dateState.currentDate
    val currentYearMonth = YearMonth.from(currentDate)

    // Use rememberUpdatedState to allow events and holidays to update without recreating the state holder
    val currentEventsByDate by rememberUpdatedState(eventsByDate)
    val currentHolidaysByDate by rememberUpdatedState(holidaysByDate)

    val scheduleStateHolder =
        remember(
            currentYearMonth.year,
            currentYearMonth.month,
        ) {
            ScheduleStateHolder(
                initialMonth = currentYearMonth,
                getEventsByDate = { currentEventsByDate },
                getHolidaysByDate = { currentHolidaysByDate },
            )
        }

    // Initialize month in the TopAppBar immediately
    LaunchedEffect(currentYearMonth) {
        dateStateHolder.updateSelectedInViewMonthState(currentYearMonth)
    }

    // Initialize the state holder asynchronously off-main
    LaunchedEffect(scheduleStateHolder) {
        scheduleStateHolder.initialize()
    }

    // Refresh items when events or holidays change, without recreating the state holder.
    // refreshItems is suspend (Mutex-guarded — see ScheduleStateHolder for the
    // thread-safety contract).
    LaunchedEffect(eventsByDate, holidaysByDate) {
        scheduleStateHolder.refreshItems()
    }

    val listState = rememberLazyListState()

    // Apply initial scroll position after composition
    LaunchedEffect(scheduleStateHolder.initialScrollIndex) {
        if (scheduleStateHolder.initialScrollIndex > 0) {
            listState.scrollToItem(scheduleStateHolder.initialScrollIndex)
        }
    }

    // Cache month headers by ID - rebuilt only when items list changes (pagination),
    // not on every viewport debounce tick (F12 optimization).
    val monthHeaderById by remember(scheduleStateHolder.items.size) {
        derivedStateOf<Map<String, ScheduleItem.MonthHeader>> {
            scheduleStateHolder.items.asSequence()
                .filterIsInstance<ScheduleItem.MonthHeader>()
                .associateBy { it.uniqueId }
        }
    }

    // Single viewport stream — replaces the three parallel snapshotFlow
    // collectors that used delay(100)/delay(500) magic numbers and could race
    // against the Mutex-guarded loaders in ScheduleStateHolder.
    //
    // We debounce viewport changes (150 ms) so fast fling scrolls don't fan
    // out to pagination + header-update work on every frame. Pagination
    // mutates the list under the Mutex, so concurrent forward/backward
    // attempts are now safe — we no longer need the boolean re-entrancy
    // guards or trailing reset-delay.
    LaunchedEffect(listState, scheduleStateHolder) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val first = listState.firstVisibleItemIndex
            val visible = layout.visibleItemsInfo
            val last = visible.lastOrNull()?.index ?: first
            val total = scheduleStateHolder.items.size

            val headerItem = visible
                .firstOrNull { info ->
                    val idx = info.index
                    idx < total && scheduleStateHolder.items[idx] is ScheduleItem.MonthHeader
                }
                ?.let { scheduleStateHolder.items[it.index] as? ScheduleItem.MonthHeader }

            ViewportState(
                firstVisibleIndex = first,
                lastVisibleIndex = last,
                totalItems = total,
                firstVisibleMonthHeaderId = headerItem?.uniqueId,
            )
        }
            .distinctUntilChanged()
            .debounce(VIEWPORT_DEBOUNCE_MS)
            .collect { state ->
                // 1) TopAppBar month follows the first visible month header.
                state.firstVisibleMonthHeaderId?.let { id ->
                    monthHeaderById[id]?.let { header ->
                        dateStateHolder.updateSelectedInViewMonthState(header.yearMonth)
                    }
                }

                // 2) Backward pagination — top edge approaching.
                if (state.firstVisibleIndex < ScheduleStateHolder.THRESHOLD) {
                    val anchorIndex = state.firstVisibleIndex
                    val anchorOffset = listState.firstVisibleItemScrollOffset
                    val added = scheduleStateHolder.loadMoreBackward()
                    if (added > 0) {
                        // Preserve visual position: indices shifted by [added].
                        runCatching {
                            listState.scrollToItem(
                                index = anchorIndex + added,
                                scrollOffset = anchorOffset,
                            )
                        }
                    }
                }

                // 3) Forward pagination — bottom edge approaching.
                if (state.totalItems > 0 &&
                    state.lastVisibleIndex >= state.totalItems - ScheduleStateHolder.THRESHOLD
                ) {
                    scheduleStateHolder.loadMoreForward()
                }
            }
    }

    // Show loading indicator if items are not ready yet
    if (scheduleStateHolder.items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            state = listState,
            modifier =
                modifier
                    .fillMaxSize()
                    .background(XCalendarTheme.colorScheme.surfaceContainerLow),
        ) {
            itemsIndexed(
                items = scheduleStateHolder.items,
                key = { _, item -> item.uniqueId },
                contentType = { _, item ->
                    when (item) {
                        is ScheduleItem.MonthHeader -> "month_header"
                        is ScheduleItem.WeekHeader -> "week_header"
                        is ScheduleItem.DayEvents -> "day_events"
                    }
                },
            ) { _, item ->
                when (item) {
                    is ScheduleItem.MonthHeader -> MonthHeader(item.yearMonth)
                    is ScheduleItem.WeekHeader -> WeekHeader(item.startDate, item.endDate)
                    is ScheduleItem.DayEvents ->
                        DayWithEvents(
                            date = item.date,
                            today = currentDate,
                            events = item.events,
                            holidays = item.holidays,
                            isVisible = isVisible,
                            onEventClick = onEventClick,
                        )
                }
            }
        }
    }
}

private const val VIEWPORT_DEBOUNCE_MS = 150L
