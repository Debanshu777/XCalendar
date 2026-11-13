package com.debanshu.xcalendar.ui.screen.monthScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.ui.components.SwipeablePager
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.state.DateStateHolder
import com.debanshu.xcalendar.ui.screen.monthScreen.components.MonthCellPreviewCapacity
import com.debanshu.xcalendar.ui.screen.monthScreen.components.MonthView
import com.debanshu.xcalendar.ui.screen.monthScreen.components.WeekdayHeader
import com.debanshu.xcalendar.ui.screen.monthScreen.components.monthCellPreviewCapacity
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MonthScreen(
    modifier: Modifier = Modifier,
    dateStateHolder: DateStateHolder,
    events: ImmutableList<Event>,
    holidays: ImmutableList<Holiday>,
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
                dateStateHolder.updateSelectedInViewMonthState(yearMonth)
            }
        }

    Column(modifier.fillMaxSize().background(XCalendarTheme.colorScheme.surfaceContainerLow)) {
        // The weekday row is shared by all pages. Keeping it outside the pager
        // gives the box below the exact grid viewport height after the header's
        // text has been measured, without a guessed header height or feedback
        // from onSizeChanged.
        WeekdayHeader(dateState.currentDate)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            // One measurer is shared by all month cells; its cache needs room
            // for the 31 distinct day labels across a page.
            val textMeasurer = rememberTextMeasurer(cacheSize = 64)
            val dateStyle = XCalendarTheme.typography.labelSmall
            val previewStyle = XCalendarTheme.typography.labelSmallEmphasized.copy(fontSize = 8.sp)
            val dateTopSpacing = XCalendarTheme.dimensions.spacing_4
            val metrics = remember(
                maxWidth,
                maxHeight,
                density,
                textMeasurer,
                dateStyle,
                previewStyle,
                dateTopSpacing,
            ) {
                val cellWidthPx = with(density) { (maxWidth / 7).roundToPx() }
                val textWidthPx = (cellWidthPx - with(density) { 16.dp.roundToPx() }).coerceAtLeast(1)
                val dateText = textMeasurer.measure("31", style = dateStyle, maxLines = 1)
                val dateBadgePx = maxOf(
                    with(density) { 30.dp.roundToPx() },
                    dateText.size.height + with(density) { 8.dp.roundToPx() },
                )
                // Åg includes ascender/descender metrics; the measured layout
                // honors the inherited lineHeight and font scale. +888 covers
                // the numeric line metrics used by the compact overflow label.
                val tagText = textMeasurer.measure(
                    "Åg",
                    style = previewStyle,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = textWidthPx),
                )
                val overflowText = textMeasurer.measure(
                    "+888",
                    style = previewStyle,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = textWidthPx),
                )
                val availablePreviewHeightPx = with(density) {
                    (maxHeight.roundToPx() / 6 - 4.dp.roundToPx() - dateTopSpacing.roundToPx() -
                        dateBadgePx - 2.dp.roundToPx()).coerceAtLeast(0)
                }
                MonthScreenMetrics(
                    previewCapacity = monthCellPreviewCapacity(
                        availableHeightPx = availablePreviewHeightPx,
                        tagHeightPx = tagText.size.height + with(density) { 4.dp.roundToPx() },
                        overflowHeightPx = overflowText.size.height,
                        gapPx = with(density) { 2.dp.roundToPx() },
                    ),
                    dateBadgeSize = with(density) { dateBadgePx.toDp() },
                )
            }

            SwipeablePager(
                modifier = Modifier.fillMaxSize().testTag("SwipeableMonthView"),
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
                    events = events,
                    holidays = holidays,
                    previewCapacity = metrics.previewCapacity,
                    dateBadgeSize = metrics.dateBadgeSize,
                    dateTextMeasurer = textMeasurer,
                    onDayClick = onSpecificDayClicked,
                )
            }
        }
    }
}

private data class MonthScreenMetrics(
    val previewCapacity: MonthCellPreviewCapacity,
    val dateBadgeSize: androidx.compose.ui.unit.Dp,
)
