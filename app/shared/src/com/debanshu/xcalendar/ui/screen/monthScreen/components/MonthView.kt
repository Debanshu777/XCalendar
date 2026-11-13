package com.debanshu.xcalendar.ui.screen.monthScreen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Dp
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.debanshu.xcalendar.ui.utils.indexEventsByDate
import com.debanshu.xcalendar.ui.utils.monthGridDates
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

@Composable
internal fun MonthView(
    modifier: Modifier,
    month: YearMonth,
    today: LocalDate,
    events: ImmutableList<Event>,
    holidays: ImmutableList<Holiday>,
    previewCapacity: MonthCellPreviewCapacity,
    dateBadgeSize: Dp,
    dateTextMeasurer: TextMeasurer,
    onDayClick: (LocalDate) -> Unit,
) {
    val dates = remember(month) { monthGridDates(month) }
    val timeZone = TimeZone.currentSystemDefault()
    val eventsByDate = remember(dates, events, timeZone) {
        indexEventsByDate(events, dates.first(), dates.last(), timeZone)
    }
    val holidaysByDate = remember(holidays, timeZone) {
        holidays.groupBy { it.date.toLocalDateTime(timeZone).date }
            .mapValues { it.value.toImmutableList() }
    }

    // All cells are visible in a fixed grid. Shared viewport/font metrics are
    // calculated once above the pager in MonthScreen.
    Column(modifier.fillMaxSize().background(XCalendarTheme.colorScheme.surfaceContainerLow)) {
        repeat(6) { week ->
            Row(Modifier.weight(1f)) {
                repeat(7) { day ->
                    val index = week * 7 + day
                    val date = dates[index]
                    key(date) {
                        DayCell(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            date = date,
                            today = today,
                            events = eventsByDate[date] ?: persistentListOf(),
                            holidays = holidaysByDate[date] ?: persistentListOf(),
                            isCurrentMonth = date.month == month.month && date.year == month.year,
                            previewCapacity = previewCapacity,
                            dateBadgeSize = dateBadgeSize,
                            dateTextMeasurer = dateTextMeasurer,
                            onDayClick = onDayClick,
                            isTopLeft = index == 0,
                            isTopRight = index == 6,
                            isBottomLeft = index == 35,
                            isBottomRight = index == 41,
                        )
                    }
                }
            }
        }
    }
}
