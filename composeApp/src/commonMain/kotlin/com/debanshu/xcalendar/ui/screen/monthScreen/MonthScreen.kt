package com.debanshu.xcalendar.ui.screen.monthScreen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.states.dateState.DateStateHolder
import com.debanshu.xcalendar.ui.components.SwipeablePager
import com.debanshu.xcalendar.ui.screen.monthScreen.components.MonthView
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

@Composable
fun MonthScreen(
    modifier: Modifier = Modifier,
    dateStateHolder: DateStateHolder,
    events: List<Event>,
    holidays: List<Holiday>,
    onDateClick: () -> Unit,
) {
    val dateState by dateStateHolder.currentDateState.collectAsState()

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
                dateStateHolder.updateSelectedDateState(
                    LocalDate(
                        yearMonth.year,
                        yearMonth.month,
                        yearMonth.getLastDateOrdinal(),
                    ),
                )
            }
        }

    SwipeablePager(
        modifier = modifier.testTag("SwipeableMonthView"),
        currentReference = {
            YearMonth(
                dateState.selectedInViewMonth.year,
                dateState.selectedInViewMonth.month,
            )
        },
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag("MonthView_$month"),
            month = month,
            events = events,
            holidays = holidays,
            onDayClick = onSpecificDayClicked,
        )
    }
}
