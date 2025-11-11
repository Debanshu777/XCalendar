package com.debanshu.xcalendar.ui.screen.monthScreen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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

    // Cache month calculations
    val (prevMonth, prevYear, daysInPrevMonth, nextMonth, nextYear) =
        remember(month) {
            val pm = if (month.month.number == 1) Month(12) else Month(month.month.number - 1)
            val py = if (month.month.number == 1) month.year - 1 else month.year
            val dpm = pm.lengthOfMonth(py.isLeap())
            val nm = if (month.month.number == 12) Month(1) else Month(month.month.number + 1)
            val ny = if (month.month.number == 12) month.year + 1 else month.year
            MonthCalculations(pm, py, dpm, nm, ny)
        }

    val gridState = rememberLazyGridState()

    BoxWithConstraints(
        propagateMinConstraints = true,
    ) {
        // Cache item size to avoid recalculation
        val itemSize =
            remember(maxWidth, maxHeight) {
                DpSize(
                    maxWidth.div(7),
                    (maxHeight - 50.dp).div(6),
                )
            }

        LazyVerticalGrid(
            modifier = modifier.background(color = XCalendarTheme.colorScheme.surfaceContainerLow),
            state = gridState,
            columns = GridCells.Fixed(7),
            userScrollEnabled = false,
        ) {
            item(
                key = "weekday_header",
                span = { GridItemSpan(7) },
            ) {
                WeekdayHeader()
            }

            if (firstDayOfWeek > 0 && !skipPreviousPadding) {
                items(
                    count = firstDayOfWeek,
                    key = { index ->
                        val ordinal = daysInPrevMonth - (firstDayOfWeek - index - 1)
                        "prev_${prevYear}_${prevMonth.number}_$ordinal"
                    },
                ) { index ->
                    val ordinal = daysInPrevMonth - (firstDayOfWeek - index - 1)
                    val date = LocalDate(prevYear, prevMonth, ordinal)
                    DayCell(
                        modifier = Modifier,
                        date = date,
                        events = eventsByDate[date] ?: persistentListOf(),
                        holidays = holidaysByDate[date] ?: persistentListOf(),
                        isCurrentMonth = false,
                        onDayClick = onDayClick,
                        itemSize = itemSize,
                        isTopLeft = index == 0,
                        isTopRight = index == 6,
                        isBottomLeft = false,
                        isBottomRight = false,
                    )
                }
            }

            items(
                count = daysInMonth,
                key = { day -> "current_${month.year}_${month.month.number}_${day + 1}" },
            ) { day ->
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
                    itemSize =
                        if (cellIndex >= 35) {
                            itemSize.copy(height = itemSize.height + 70.dp)
                        } else {
                            itemSize
                        },
                    isTopLeft = cellIndex == 0,
                    isTopRight = cellIndex == 6,
                    isBottomLeft = cellIndex == 35,
                    isBottomRight = cellIndex == 41,
                )
            }

            items(
                count = remainingCells,
                key = { day -> "next_${nextYear}_${nextMonth.number}_${day + 1}" },
            ) { day ->
                val date = LocalDate(nextYear, nextMonth, day + 1)
                val cellIndex = totalDaysDisplayed + day

                DayCell(
                    modifier = Modifier,
                    date = date,
                    events = eventsByDate[date] ?: persistentListOf(),
                    holidays = holidaysByDate[date] ?: persistentListOf(),
                    isCurrentMonth = false,
                    onDayClick = onDayClick,
                    itemSize =
                        if (cellIndex >= 35) {
                            itemSize.copy(height = itemSize.height + 70.dp)
                        } else {
                            itemSize
                        },
                    isTopLeft = false,
                    isTopRight = false,
                    isBottomLeft = false,
                    isBottomRight = false,
                )
            }
        }
    }
}

private data class MonthCalculations(
    val prevMonth: Month,
    val prevYear: Int,
    val daysInPrevMonth: Int,
    val nextMonth: Month,
    val nextYear: Int,
)
