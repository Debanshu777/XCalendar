package com.debanshu.xcalendar.ui.state

import androidx.compose.runtime.mutableStateListOf
import com.debanshu.xcalendar.common.isLeap
import com.debanshu.xcalendar.common.lengthOfMonth
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleItem
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleItem.DayEvents
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleItem.MonthHeader
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleItem.WeekHeader
import com.debanshu.xcalendar.ui.utils.indexEventsByDate
import kotlinx.collections.immutable.toImmutableList
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
 * @property initialMonth The month to start from (typically current month)
 * @property getEvents Lambda to get current events list
 * @property getHolidays Lambda to get current holidays list
 */
class ScheduleStateHolder(
    initialMonth: YearMonth,
    private val getEvents: () -> List<Event>,
    private val getHolidays: () -> List<Holiday>,
) {
    private val _items = mutableStateListOf<ScheduleItem>()
    val items: List<ScheduleItem> = _items

    private var monthRange = ScheduleState(initialMonth)
    val initialScrollIndex: Int

    // Cache for optimized event and holiday filtering
    private val eventCache = mutableMapOf<LocalDate, List<Event>>()
    private val holidayCache = mutableMapOf<LocalDate, List<Holiday>>()

    init {
        val initialItems = createScheduleItemsForMonthRange(
            monthRange.getMonths(),
            getEvents(),
            getHolidays(),
        )
        _items.addAll(initialItems)

        initialScrollIndex = _items
            .indexOfFirst { item ->
                item is MonthHeader &&
                    item.yearMonth.year == initialMonth.year &&
                    item.yearMonth.month == initialMonth.month
            }
            .coerceAtLeast(0)
    }

    /**
     * Loads more items at the beginning of the list.
     */
    fun loadMoreBackward() {
        val newMonths = monthRange.expandBackward()
        val newItems = createScheduleItemsForMonthRange(newMonths, getEvents(), getHolidays())
        _items.addAll(0, newItems)
    }

    /**
     * Loads more items at the end of the list.
     */
    fun loadMoreForward() {
        val newMonths = monthRange.expandForward()
        val newItems = createScheduleItemsForMonthRange(newMonths, getEvents(), getHolidays())
        _items.addAll(newItems)
    }

    /**
     * Refreshes all items with current events and holidays data.
     * Clears caches and regenerates the entire list while maintaining pagination state.
     */
    fun refreshItems() {
        eventCache.clear()
        holidayCache.clear()

        val refreshedItems = createScheduleItemsForMonthRange(
            monthRange.getMonths(),
            getEvents(),
            getHolidays()
        )

        _items.clear()
        _items.addAll(refreshedItems)
    }

    fun scrollIndexFor(date: LocalDate): Int {
        val month = YearMonth.from(date)
        if (_items.none { it is MonthHeader && it.yearMonth == month }) {
            monthRange = ScheduleState(month)
            refreshItems()
        }
        val dayIndex = _items.indexOfFirst { it is DayEvents && it.date == date }
        return if (dayIndex >= 0) dayIndex else _items.indexOfFirst {
            it is WeekHeader && date in it.startDate..it.endDate
        }.coerceAtLeast(0)
    }

    private fun createScheduleItemsForMonthRange(
        months: List<YearMonth>,
        allEvents: List<Event>,
        allHolidays: List<Holiday>,
    ): List<ScheduleItem> {
        val items = mutableListOf<ScheduleItem>()

        // Pre-calculate date ranges for events and holidays
        if (months.isEmpty()) return items
        val eventDateMap = indexEventsByDate(
            allEvents,
            calculateDaysInMonth(months.first()).first(),
            calculateDaysInMonth(months.last()).last(),
        )

        val holidayDateMap = allHolidays.groupBy { holiday ->
            holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date
        }

        months.forEach { yearMonth ->
            items.add(MonthHeader(yearMonth))

            val daysInMonth = calculateDaysInMonth(yearMonth)
            val weeks = daysInMonth.chunked(7)

            weeks.forEach { week ->
                if (week.isNotEmpty()) {
                    items.add(WeekHeader(week.first(), week.last()))

                    week.forEach { date ->
                        val dayEvents = eventCache.getOrPut(date) {
                            eventDateMap[date] ?: emptyList()
                        }.toImmutableList()

                        val dayHolidays = holidayCache.getOrPut(date) {
                            holidayDateMap[date] ?: emptyList()
                        }.toImmutableList()

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

