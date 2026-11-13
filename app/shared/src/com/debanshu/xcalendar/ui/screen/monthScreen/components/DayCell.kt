package com.debanshu.xcalendar.ui.screen.monthScreen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debanshu.xcalendar.common.noRippleClickable
import com.debanshu.xcalendar.common.applyIf
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.components.EventTag
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.debanshu.xcalendar.ui.utils.rememberEventContentColor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DayCell(
    modifier: Modifier,
    date: LocalDate,
    today: LocalDate,
    events: ImmutableList<Event>,
    holidays: ImmutableList<Holiday>,
    isCurrentMonth: Boolean,
    previewCapacity: MonthCellPreviewCapacity,
    dateBadgeSize: androidx.compose.ui.unit.Dp,
    dateTextMeasurer: TextMeasurer,
    onDayClick: (LocalDate) -> Unit,
    isTopLeft: Boolean = false,
    isTopRight: Boolean = false,
    isBottomLeft: Boolean = false,
    isBottomRight: Boolean = false,
) {
    val isToday = date == today
    val preview = monthCellPreview(holidays.size, events.size, previewCapacity)
    val showCompactBusyCue = preview.omitted > 0 && !previewCapacity.overflowFits
    val compactBusyCueColor = XCalendarTheme.colorScheme.primary
    val cornerRadius = 16.dp
    val cellShape = remember(isTopLeft, isTopRight, isBottomLeft, isBottomRight) {
        RoundedCornerShape(
            topStart = if (isTopLeft) cornerRadius else 8.dp,
            topEnd = if (isTopRight) cornerRadius else 8.dp,
            bottomStart = if (isBottomLeft) cornerRadius else 8.dp,
            bottomEnd = if (isBottomRight) cornerRadius else 8.dp,
        )
    }
    val todayShape = if (isToday) MaterialShapes.Cookie9Sided.toShape() else null
    val dateTextStyle = LocalTextStyle.current
        .merge(XCalendarTheme.typography.labelSmall)
        .copy(textAlign = TextAlign.Center)
    val dateTextColor =
        when {
            isToday -> XCalendarTheme.colorScheme.inverseOnSurface
            isCurrentMonth -> XCalendarTheme.colorScheme.onSurface
            else -> XCalendarTheme.colorScheme.onSurfaceVariant
        }

    Column(
        modifier =
            modifier
                .border(
                    width = 2.dp,
                    color = XCalendarTheme.colorScheme.surfaceContainerLow,
                    shape = cellShape,
                )
                .noRippleClickable { onDayClick(date) }
                .clearAndSetSemantics {
                    // One text value preserves ContentCapture updates for any
                    // visible title while avoiding redundant child semantics.
                    text = AnnotatedString(
                        dayCellAccessibilityText(
                            date = date,
                            isToday = isToday,
                            holidayNames = holidays.take(preview.holidays).map { it.name },
                            eventTitles = events.take(preview.events).map { it.title },
                            omittedCount = preview.omitted,
                        ),
                    )
                    stateDescription = dayCellCountsDescription(holidays.size, events.size)
                    role = Role.Button
                    onClick(label = "Open day") {
                        onDayClick(date)
                        true
                    }
                }
                .clip(cellShape)
                .background(XCalendarTheme.colorScheme.surfaceContainerHigh)
                .applyIf(showCompactBusyCue) {
                    drawBehind {
                        drawCircle(
                            color = compactBusyCueColor,
                            radius = 2.dp.toPx(),
                            center = Offset(
                                x = size.width - 8.dp.toPx(),
                                y = size.height - 8.dp.toPx(),
                            ),
                        )
                    }
                }
                .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = XCalendarTheme.dimensions.spacing_4)
                    .size(dateBadgeSize)
                    // Month paging creates/removes an entire 42-cell grid.
                    // Per-cell shared transitions add lookahead and lifecycle
                    // work to that gesture; navigation already fades the scene.
                    .applyIf(todayShape != null) { clip(todayShape!!) }
                    .background(
                        when {
                            isToday -> XCalendarTheme.colorScheme.primary
                            else -> Color.Transparent
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Measure in the draw scope so font resolution, density,
                // font scale, and layout direction stay current. The shared
                // TextMeasurer cache reuses these 31 labels across cells/pages.
                val layout = dateTextMeasurer.measure(
                    text = date.day.toString(),
                    style = dateTextStyle,
                    overflow = TextOverflow.Clip,
                    maxLines = 1,
                    constraints = Constraints(
                        maxWidth = size.width.toInt().coerceAtLeast(0),
                        maxHeight = size.height.toInt().coerceAtLeast(0),
                    ),
                    density = this,
                    layoutDirection = drawContext.layoutDirection,
                )
                drawText(
                    textLayoutResult = layout,
                    color = dateTextColor,
                    topLeft = Offset(
                        x = ((size.width - layout.size.width) / 2f).roundToInt().toFloat(),
                        y = ((size.height - layout.size.height) / 2f).roundToInt().toFloat(),
                    ),
                )
            }
        }

        repeat(preview.holidays) { index ->
            Spacer(modifier = Modifier.height(2.dp))
            val holiday = holidays[index]
            EventTag(
                text = holiday.name,
                color = Color(0xFF007F73),
                textColor = rememberEventContentColor(Color(0xFF007F73), XCalendarTheme.colorScheme.surfaceContainerHigh),
            )
        }

        repeat(preview.events) { index ->
            Spacer(modifier = Modifier.height(2.dp))
            val event = events[index]
            EventTag(
                text = event.title,
                color = Color(event.color),
                textColor = rememberEventContentColor(Color(event.color), XCalendarTheme.colorScheme.surfaceContainerHigh),
            )
        }

        // A very short cell still exposes the full counts and Open day action
        // through semantics; never place a knowingly clipped overflow label.
        if (preview.omitted > 0 && previewCapacity.overflowFits) {
            if (preview.holidays + preview.events > 0) Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "+${preview.omitted}",
                style = XCalendarTheme.typography.labelSmallEmphasized.copy(fontSize = 8.sp),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 2.dp),
            )
        }
    }
}

internal fun dayCellAccessibilityText(
    date: LocalDate,
    isToday: Boolean,
    holidayNames: List<String>,
    eventTitles: List<String>,
    omittedCount: Int,
): String {
    require(omittedCount >= 0)
    return buildString {
        append(date)
        if (isToday) append(". Today")
        holidayNames.forEach { name ->
            append(". Holiday: ")
            append(name)
        }
        eventTitles.forEach { title ->
            append(". Event: ")
            append(title)
        }
        if (omittedCount > 0) append(". $omittedCount more")
    }
}

internal fun dayCellCountsDescription(holidayCount: Int, eventCount: Int): String {
    require(holidayCount >= 0)
    require(eventCount >= 0)
    val holidayLabel = if (holidayCount == 1) "holiday" else "holidays"
    val eventLabel = if (eventCount == 1) "event" else "events"
    return "$holidayCount $holidayLabel, $eventCount $eventLabel"
}
