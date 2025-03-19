package com.debanshu.xcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debanshu.xcalendar.common.getBottomSystemBarHeight
import com.debanshu.xcalendar.common.getScreenHeight
import com.debanshu.xcalendar.common.getScreenWidth
import com.debanshu.xcalendar.common.getTopSystemBarHeight
import com.debanshu.xcalendar.common.lengthOfMonth
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun MonthView(
    month: YearMonth,
    events: List<Event>,
    holidays: List<Holiday>,
    onDayClick: (LocalDate) -> Unit,
    selectedDay: LocalDate
) {
    val firstDayOfMonth = LocalDate(month.year, month.month, 1)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.ordinal + 1
    val daysInMonth = month.month.lengthOfMonth(month.year.isLeap())
    val totalDays = firstDayOfWeek + daysInMonth
    val remainingCells = 42 - totalDays

    // Calendar grid with fixed height based on number of rows
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(7),
        userScrollEnabled = false
    ) {
        item(span = { GridItemSpan(7) }) {
            WeekdayHeader()
        }
        // Previous month padding days
        items(firstDayOfWeek) { index ->
            val prevMonth =
                if (month.month.ordinal == 1) Month(12) else Month(month.month.ordinal - 1)
            val prevYear = if (month.month.ordinal == 1) month.year - 1 else month.year
            val daysInPrevMonth = prevMonth.lengthOfMonth(prevYear.isLeap())
            val day = daysInPrevMonth - (firstDayOfWeek - index - 1)
            val date = LocalDate(prevYear, prevMonth, day)

            DayCell(
                modifier = Modifier,
                date = date,
                events = events.filter { event ->
                    event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date == date
                },
                holidays = holidays.filter { holiday ->
                    holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date == date
                },
                isCurrentMonth = false,
                isSelected = false,
                onDayClick = onDayClick
            )
        }

        // Current month days
        items(daysInMonth) { day ->
            val date = LocalDate(month.year, month.month, day + 1)
            DayCell(
                modifier = Modifier,
                date = date,
                events = events.filter { event ->
                    event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date == date
                },
                holidays = holidays.filter { holiday ->
                    holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date == date
                },
                isCurrentMonth = true,
                isSelected = date == selectedDay,
                onDayClick = onDayClick
            )
        }

        items(remainingCells) { day ->
            val nextMonth =
                if (month.month.ordinal == 12) Month(1) else Month(month.month.ordinal + 1)
            val nextYear = if (month.month.ordinal == 12) month.year + 1 else month.year
            val date = LocalDate(nextYear, nextMonth, day + 1)

            DayCell(
                modifier = Modifier,
                date = date,
                events = events.filter { event ->
                    event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date == date
                },
                holidays = holidays.filter { holiday ->
                    holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date == date
                },
                isCurrentMonth = false,
                isSelected = false,
                onDayClick = onDayClick
            )
        }
    }
}

@Composable
fun WeekdayHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
    ) {
        val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")

        daysOfWeek.forEach { day ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day,
                    style = MaterialTheme.typography.caption,
                )
            }
        }
    }
}

@Composable
fun DayCell(
    modifier: Modifier,
    date: LocalDate,
    events: List<Event>,
    holidays: List<Holiday>,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    onDayClick: (LocalDate) -> Unit
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val isToday = date == today
    val screenWidth = getScreenWidth()
    val screenHeight =
        getScreenHeight().plus(30.dp) - getTopSystemBarHeight() - getBottomSystemBarHeight()


    Box(
        modifier = modifier
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
            )
            .aspectRatio(screenWidth / screenHeight)
            .background(
                when {
                    isSelected -> MaterialTheme.colors.primary.copy(alpha = 0.2f)
                    else -> MaterialTheme.colors.surface
                }
            )
            .clickable { onDayClick(date) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
        ) {
            // Day number
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(28.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(
                        when {
                            isToday -> MaterialTheme.colors.primary
                            isSelected -> MaterialTheme.colors.primary.copy(alpha = 0.3f)
                            else -> Color.Transparent
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.body2,
                    color = when {
                        isToday -> Color.White
                        isCurrentMonth -> MaterialTheme.colors.onSurface
                        else -> MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                    },
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Events display - limited to 3 events with +more indicator
            val maxEventsToShow = 3
            val displayedEvents = events.take(maxEventsToShow)

            // Holidays at the top
            holidays.firstOrNull()?.let { holiday ->
                EventTag(
                    text = holiday.name,
                    color = Color(0xFF4285F4).copy(alpha = 0.8f),
                    textColor = Color.White
                )
            }

            // Regular events
            displayedEvents.forEach { event ->
                EventTag(
                    text = event.title,
                    color = Color(event.color ?: 0xFFE91E63.toInt()).copy(alpha = 0.8f),
                    textColor = Color.White
                )
            }

            // +more indicator if needed
            if (events.size > maxEventsToShow) {
                Text(
                    text = "+${events.size - maxEventsToShow} more",
                    style = MaterialTheme.typography.caption,
                    fontSize = 8.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 2.dp, top = 1.dp)
                )
            }
        }
    }
}

@Composable
fun EventTag(
    text: String,
    color: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .height(16.dp)
            .background(color, RoundedCornerShape(2.dp))
            .padding(horizontal = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}
