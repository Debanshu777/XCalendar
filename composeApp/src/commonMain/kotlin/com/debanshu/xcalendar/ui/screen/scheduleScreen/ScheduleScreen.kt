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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.states.dateState.DateStateHolder
import com.debanshu.xcalendar.domain.states.scheduleState.ScheduleStateHolder
import com.debanshu.xcalendar.ui.screen.scheduleScreen.components.DayWithEvents
import com.debanshu.xcalendar.ui.screen.scheduleScreen.components.MonthHeader
import com.debanshu.xcalendar.ui.screen.scheduleScreen.components.WeekHeader
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    dateStateHolder: DateStateHolder,
    events: ImmutableList<Event>,
    holidays: ImmutableList<Holiday>,
    onEventClick: (Event) -> Unit,
) {
    val dateState by dateStateHolder.currentDateState.collectAsState()
    val currentDate = dateState.currentDate
    val currentYearMonth = YearMonth.from(currentDate)

    val scheduleStateHolder =
        remember(
            currentYearMonth.year,
            currentYearMonth.month,
            events,
            holidays,
        ) {
            ScheduleStateHolder(
                initialMonth = currentYearMonth,
                events = events,
                holidays = holidays,
            )
        }

    // Initialize month in the TopAppBar immediately
    LaunchedEffect(currentYearMonth) {
        dateStateHolder.updateSelectedInViewMonthState(currentYearMonth)
    }

    // Create list state with initial position
    val listState = rememberLazyListState()

    // Track pagination state to prevent multiple simultaneous requests
    val isPaginatingBackward = remember { androidx.compose.runtime.mutableStateOf(false) }
    val isPaginatingForward = remember { androidx.compose.runtime.mutableStateOf(false) }

    // Apply initial scroll position after composition
    LaunchedEffect(scheduleStateHolder.initialScrollIndex) {
        if (scheduleStateHolder.initialScrollIndex > 0) {
            listState.scrollToItem(scheduleStateHolder.initialScrollIndex)
        }
    }

    // Optimized: Single LaunchedEffect with combined logic to reduce overhead
    LaunchedEffect(listState) {
        // Monitor visible month headers for TopAppBar updates
        launch {
            snapshotFlow {
                val firstVisible = listState.firstVisibleItemIndex
                val visibleCount = listState.layoutInfo.visibleItemsInfo.size

                // Find the first visible month header with optimized search
                (firstVisible until firstVisible + visibleCount)
                    .firstOrNull { idx ->
                        idx < scheduleStateHolder.items.size &&
                            scheduleStateHolder.items[idx] is ScheduleItem.MonthHeader
                    }?.let { idx -> scheduleStateHolder.items[idx] as? ScheduleItem.MonthHeader }
            }.filterNotNull()
                .distinctUntilChanged()
                .collect { header ->
                    dateStateHolder.updateSelectedInViewMonthState(header.yearMonth)
                }
        }

        // Handle backward pagination with optimized threshold checking
        launch {
            snapshotFlow {
                !isPaginatingBackward.value &&
                    listState.firstVisibleItemIndex < ScheduleStateHolder.THRESHOLD
            }.distinctUntilChanged()
                .collect { needsMore ->
                    if (needsMore && !isPaginatingBackward.value) {
                        isPaginatingBackward.value = true

                        // Store current visible item info before adding new items
                        val firstVisibleIndex = listState.firstVisibleItemIndex
                        val firstVisibleItemOffset = listState.firstVisibleItemScrollOffset

                        val newItemsCount = scheduleStateHolder.loadMoreBackward()

                        if (newItemsCount > 0) {
                            // Wait for the list to update with new items
                            kotlinx.coroutines.delay(100)

                            try {
                                // Directly scroll to maintain position without animation
                                listState.scrollToItem(
                                    index = firstVisibleIndex + newItemsCount,
                                    scrollOffset = firstVisibleItemOffset,
                                )
                            } catch (_: Exception) {
                                // Silently handle scroll position adjustment failures
                            }
                        }

                        // Reset pagination flag after a delay
                        kotlinx.coroutines.delay(500)
                        isPaginatingBackward.value = false
                    }
                }
        }

        // Handle forward pagination with optimized threshold checking
        launch {
            snapshotFlow {
                val visibleInfo = listState.layoutInfo.visibleItemsInfo
                val lastVisibleIndex = visibleInfo.lastOrNull()?.index ?: 0
                val totalItems = scheduleStateHolder.items.size
                // Trigger when we're within THRESHOLD items of the end
                !isPaginatingForward.value &&
                    lastVisibleIndex >= totalItems - ScheduleStateHolder.THRESHOLD &&
                    totalItems > 0
            }.distinctUntilChanged()
                .collect { needsMore ->
                    if (needsMore && !isPaginatingForward.value) {
                        isPaginatingForward.value = true
                        scheduleStateHolder.loadMoreForward()

                        // Reset pagination flag after a delay
                        kotlinx.coroutines.delay(500)
                        isPaginatingForward.value = false
                    }
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
                            events = item.events,
                            holidays = item.holidays,
                            onEventClick = onEventClick,
                        )
                }
            }
        }
    }
}
