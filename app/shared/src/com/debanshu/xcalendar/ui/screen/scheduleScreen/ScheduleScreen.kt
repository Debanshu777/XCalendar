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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.state.DateStateHolder
import com.debanshu.xcalendar.ui.state.ScheduleStateHolder
import com.debanshu.xcalendar.ui.screen.scheduleScreen.components.DayWithEvents
import com.debanshu.xcalendar.ui.screen.scheduleScreen.components.MonthHeader
import com.debanshu.xcalendar.ui.screen.scheduleScreen.components.WeekHeader
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    dateStateHolder: DateStateHolder,
    events: ImmutableList<Event>,
    holidays: ImmutableList<Holiday>,
    isVisible: Boolean = true,
    scrollToToday: Boolean = false,
    onTodayScrollRequested: () -> Unit = {},
    onEventClick: (Event) -> Unit,
) {
    val dateState by dateStateHolder.currentDateState.collectAsState()
    val currentDate = dateState.currentDate
    val currentYearMonth = YearMonth.from(currentDate)

    // Use rememberUpdatedState to allow events and holidays to update without recreating the state holder
    val currentEvents by rememberUpdatedState(events)
    val currentHolidays by rememberUpdatedState(holidays)

    val scheduleStateHolder =
        remember {
            ScheduleStateHolder(
                initialMonth = currentYearMonth,
                getEvents = { currentEvents },
                getHolidays = { currentHolidays },
            )
        }

    // Refresh items when events or holidays change, without recreating the state holder
    LaunchedEffect(events, holidays) {
        scheduleStateHolder.refreshItems()
    }

    // Create list state with initial position
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = scheduleStateHolder.initialScrollIndex)

    LaunchedEffect(isVisible, scrollToToday) {
        if (isVisible && scrollToToday) {
            listState.requestScrollToItem(scheduleStateHolder.scrollIndexFor(currentDate))
            onTodayScrollRequested()
        }
    }

    LaunchedEffect(listState, scheduleStateHolder, isVisible) {
        if (!isVisible) return@LaunchedEffect
        launch {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.firstOrNull()?.let { item ->
                    scheduleStateHolder.items.getOrNull(item.index)
                        ?.takeIf { it.uniqueId == item.key }
                        ?.month
                }
            }.filterNotNull().collect(dateStateHolder::updateSelectedInViewMonthState)
        }
        launch {
            // Stable item keys retain the visible row when months are prepended.
            snapshotFlow {
                val layout = listState.layoutInfo
                layout.visibleItemsInfo.firstOrNull()?.index?.let { first ->
                    first < ScheduleStateHolder.THRESHOLD
                } ?: false
            }.collect { nearStart ->
                if (nearStart) scheduleStateHolder.loadMoreBackward()
            }
        }
        launch {
            snapshotFlow {
                val layout = listState.layoutInfo
                layout.visibleItemsInfo.lastOrNull()?.index?.let { last ->
                    last >= layout.totalItemsCount - ScheduleStateHolder.THRESHOLD
                } ?: false
            }.collect { nearEnd ->
                if (nearEnd) scheduleStateHolder.loadMoreForward()
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
                            isVisible = isVisible,
                            onEventClick = onEventClick,
                        )
                }
            }
        }
    }
}
