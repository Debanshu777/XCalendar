package com.debanshu.xcalendar.ui.screen.monthScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.state.DateStateHolder
import com.debanshu.xcalendar.ui.model.EventsByDate
import com.debanshu.xcalendar.ui.model.HolidaysByDate
import com.debanshu.xcalendar.ui.components.SwipeablePager
import com.debanshu.xcalendar.ui.screen.monthScreen.components.MonthView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

@Composable
fun MonthScreen(
    modifier: Modifier = Modifier,
    dateStateHolder: DateStateHolder,
    eventsByDate: EventsByDate,
    holidaysByDate: HolidaysByDate,
    isVisible: Boolean = true,
    onDateClick: () -> Unit,
) {
    val dateState by dateStateHolder.currentDateState.collectAsStateWithLifecycle()

    // Create stable callbacks to prevent unnecessary recompositions
    val onSpecificDayClicked =
        remember(dateStateHolder, onDateClick) {
            { date: LocalDate ->
                dateStateHolder.updateSelectedDateState(date)
                onDateClick()
            }
        }

    val onMonthChange =
        remember(dateStateHolder) {
            { yearMonth: YearMonth ->
                dateStateHolder.updateSelectedInViewMonthState(yearMonth)
            }
        }

    SwipeablePager(
        modifier = modifier.testTag("SwipeableMonthView"),
        currentReference =
            YearMonth(
                dateState.selectedInViewMonth.year,
                dateState.selectedInViewMonth.month,
            ),
        calculateOffset = { current, base ->
            (current.year - base.year) * 12 + (current.month.number - base.month.number)
        },
        pageToReference = { baseMonth, initialPage, page ->
            val offset = page - initialPage
            baseMonth.plusMonths(offset)
        },
        onReferenceChange = onMonthChange,
    ) { month ->
        MonthView(
            modifier = Modifier.testTag("MonthView_$month"),
            month = month,
            today = dateState.currentDate,
            eventsByDate = eventsByDate,
            holidaysByDate = holidaysByDate,
            isVisible = isVisible,
            onDayClick = onSpecificDayClicked,
        )
    }
}
