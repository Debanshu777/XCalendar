package com.debanshu.xcalendar.ui.screen.monthScreen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.debanshu.xcalendar.common.isLeap
import com.debanshu.xcalendar.common.lengthOfMonth
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number

@Composable
fun MonthView(
    modifier: Modifier,
    month: YearMonth,
    events: ImmutableList<Event>,
    holidays: ImmutableList<Holiday>,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstDayOfMonth = LocalDate(month.year, month.month, 1)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.ordinal + 1
    val daysInMonth = month.month.lengthOfMonth(month.year.isLeap())

    val skipPreviousPadding = firstDayOfWeek >= 7
    val totalDaysDisplayed = if (skipPreviousPadding) daysInMonth else firstDayOfWeek + daysInMonth
    val remainingCells = 42 - totalDaysDisplayed

    val eventsByDate =
        remember(month.year, month.month, events) {
            events
                .groupBy { event ->
                    event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
                }.mapValues {
                    it.value.toImmutableList()
                }.toImmutableMap()
        }

    val holidaysByDate =
        remember(month.year, month.month, holidays) {
            holidays
                .groupBy { holiday ->
                    holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date
                }.mapValues {
                    it.value.toImmutableList()
                }.toImmutableMap()
        }

    LazyVerticalGrid(
        modifier =
            modifier
                .fillMaxSize()
                .background(color = XCalendarTheme.colorScheme.surfaceContainerLow),
        columns = GridCells.Fixed(7),
        userScrollEnabled = false,
    ) {
        item(span = { GridItemSpan(7) }) {
            WeekdayHeader()
        }
        if (firstDayOfWeek > 0 && !skipPreviousPadding) {
            items(firstDayOfWeek) { index ->
                val prevMonth =
                    if (month.month.number == 1) Month(12) else Month(month.month.number - 1)
                val prevYear = if (month.month.number == 1) month.year - 1 else month.year
                val daysInPrevMonth = prevMonth.lengthOfMonth(prevYear.isLeap())
                val day = daysInPrevMonth - (firstDayOfWeek - index - 1)
                val date = LocalDate(prevYear, prevMonth, day)

                DayCell(
                    modifier = Modifier,
                    date = date,
                    events = eventsByDate[date] ?: persistentListOf(),
                    holidays = holidaysByDate[date] ?: persistentListOf(),
                    isCurrentMonth = false,
                    onDayClick = onDayClick,
                    isTopLeft = index == 0,
                    isTopRight = index == 6,
                    isBottomLeft = index == 35,
                    isBottomRight = index == 41,
                )
            }
        }

        items(daysInMonth) { day ->
            val date = LocalDate(month.year, month.month, day + 1)
            val currentMonthStartIndex = if (skipPreviousPadding) 0 else firstDayOfWeek
            val cellIndex = currentMonthStartIndex + day

            DayCell(
                modifier = Modifier,
                date = date,
                events = eventsByDate[date] ?: persistentListOf(),
                holidays = holidaysByDate[date] ?: persistentListOf(),
                isCurrentMonth = true,
                onDayClick = onDayClick,
                isTopLeft = cellIndex == 0,
                isTopRight = cellIndex == 6,
                isBottomLeft = cellIndex == 35,
                isBottomRight = cellIndex == 41,
            )
        }

        items(remainingCells) { day ->
            val nextMonth =
                if (month.month.number == 12) Month(1) else Month(month.month.number + 1)
            val nextYear = if (month.month.number == 12) month.year + 1 else month.year
            val date = LocalDate(nextYear, nextMonth, day + 1)
            val cellIndex = totalDaysDisplayed + day

            DayCell(
                modifier = Modifier,
                date = date,
                events = eventsByDate[date] ?: persistentListOf(),
                holidays = holidaysByDate[date] ?: persistentListOf(),
                isCurrentMonth = false,
                onDayClick = onDayClick,
                isTopLeft = cellIndex == 0,
                isTopRight = cellIndex == 6,
                isBottomLeft = cellIndex == 35,
                isBottomRight = cellIndex == 41,
            )
        }
    }
}
