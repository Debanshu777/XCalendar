package com.debanshu.xcalendar.ui.state

import androidx.compose.runtime.mutableStateListOf
import com.debanshu.xcalendar.common.isLeap
import com.debanshu.xcalendar.common.lengthOfMonth
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.model.EventsByDate
import com.debanshu.xcalendar.ui.model.HolidaysByDate
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleItem
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleItem.DayEvents
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleItem.MonthHeader
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleItem.WeekHeader
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Manages the state for the schedule screen with optimized lazy loading.
 *
 * Features:
 * - Dynamic pagination (load more months as user scrolls)
 * - Event and holiday caching for performance
 * - Maintains scroll position during pagination
 *
 * Thread safety:
 * [_items] is a Compose `SnapshotStateList`, which is snapshot-safe but NOT
 * thread-safe. All mutating operations ([loadMoreBackward], [loadMoreForward],
 * [refreshItems]) are serialised through [mutex] so concurrent pagination /
 * refresh calls from `ScheduleScreen` cannot interleave and corrupt the list.
 *
 * @property initialMonth The month to start from (typically current month)
 * @property getEventsByDate Lambda to get current events grouped by date
 * @property getHolidaysByDate Lambda to get current holidays grouped by date
 */
class ScheduleStateHolder(
    private val initialMonth: YearMonth,
    private val getEventsByDate: () -> EventsByDate,
    private val getHolidaysByDate: () -> HolidaysByDate,
) {
    private val _items = mutableStateListOf<ScheduleItem>()
    val items: List<ScheduleItem> = _items

    private val monthRange = ScheduleState(initialMonth, initialRange = 3)
    var initialScrollIndex: Int = 0
        private set

    // Serialises mutation of [_items] and [monthRange]. See class-level thread-safety note.
    private val mutex = Mutex()

    /**
     * Initializes the schedule items asynchronously off the main thread.
     * Should be called once after construction.
     */
    suspend fun initialize() = mutex.withLock {
        val initialItems = withContext(Dispatchers.Default) {
            createScheduleItemsForMonthRange(
                monthRange.getMonths(),
                getEventsByDate(),
                getHolidaysByDate(),
            )
        }

        val scrollIndex = initialItems
            .indexOfFirst { item ->
                item is MonthHeader &&
                    item.yearMonth.year == initialMonth.year &&
                    item.yearMonth.month == initialMonth.month
            }
            .coerceAtLeast(0)

        _items.addAll(initialItems)
        initialScrollIndex = scrollIndex
    }

    /**
     * Loads more items at the beginning of the list.
     * @return Number of new items added
     */
    suspend fun loadMoreBackward(): Int = mutex.withLock {
        monthRange.expandBackward()
        val newMonths = monthRange.getLastAddedMonthsBackward()
        val newItems = withContext(Dispatchers.Default) {
            createScheduleItemsForMonthRange(newMonths, getEventsByDate(), getHolidaysByDate())
        }

        if (newItems.isNotEmpty()) {
            _items.addAll(0, newItems)
            newItems.size
        } else {
            0
        }
    }

    /**
     * Loads more items at the end of the list.
     * @return Number of new items added
     */
    suspend fun loadMoreForward(): Int = mutex.withLock {
        monthRange.expandForward()
        val newMonths = monthRange.getLastAddedMonthsForward()
        val newItems = withContext(Dispatchers.Default) {
            createScheduleItemsForMonthRange(newMonths, getEventsByDate(), getHolidaysByDate())
        }

        if (newItems.isNotEmpty()) {
            _items.addAll(newItems)
            newItems.size
        } else {
            0
        }
    }

    /**
     * Refreshes all items with current events and holidays data.
     * Regenerates the entire list while maintaining pagination state.
     */
    suspend fun refreshItems() = mutex.withLock {
        val refreshedItems = withContext(Dispatchers.Default) {
            createScheduleItemsForMonthRange(
                monthRange.getMonths(),
                getEventsByDate(),
                getHolidaysByDate()
            )
        }

        _items.clear()
        _items.addAll(refreshedItems)
    }

    private fun createScheduleItemsForMonthRange(
        months: List<YearMonth>,
        eventsByDate: EventsByDate,
        holidaysByDate: HolidaysByDate,
    ): List<ScheduleItem> {
        val items = mutableListOf<ScheduleItem>()

        months.forEach { yearMonth ->
            items.add(MonthHeader(yearMonth))

            val daysInMonth = calculateDaysInMonth(yearMonth)
            val weeks = daysInMonth.chunked(7)

            weeks.forEach { week ->
                if (week.isNotEmpty()) {
                    items.add(WeekHeader(week.first(), week.last()))

                    week.forEach { date ->
                        val dayEvents = eventsByDate[date]
                        val dayHolidays = holidaysByDate[date]

                        if (dayEvents.isNotEmpty() || dayHolidays.isNotEmpty()) {
                            items.add(DayEvents(date, dayEvents, dayHolidays))
                        }
                    }
                }
            }
        }

        return items
    }

    private fun calculateDaysInMonth(yearMonth: YearMonth): List<LocalDate> {
        val daysInMonth = yearMonth.month.lengthOfMonth(yearMonth.year.isLeap())
        return (1..daysInMonth).map { day ->
            LocalDate(yearMonth.year, yearMonth.month, day)
        }
    }

    companion object {
        const val THRESHOLD = 10
    }
}
