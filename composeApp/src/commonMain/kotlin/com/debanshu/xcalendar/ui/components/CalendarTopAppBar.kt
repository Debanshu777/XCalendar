package com.debanshu.xcalendar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debanshu.xcalendar.common.isLeap
import com.debanshu.xcalendar.common.lengthOfMonth
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.common.noRippleClickable
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.common.toSentenceCase
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.states.dateState.DateState
import com.debanshu.xcalendar.ui.TopBarCalendarView
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.skydoves.landscapist.coil3.CoilImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.CaretDown
import compose.icons.fontawesomeicons.solid.Search
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalendarTopAppBar(
    dateState: DateState,
    monthDropdownState: TopBarCalendarView,
    onMenuClick: () -> Unit,
    onSelectToday: () -> Unit,
    onToggleMonthDropdown: (TopBarCalendarView) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    events: List<Event>,
    holidays: List<Holiday>,
) {
    val currentYear = dateState.currentDate.year

    val showYear = dateState.selectedInViewMonth.year != currentYear

    val rotationAngle = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        rotationAngle.animateTo(
            targetValue = 360f,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
        )
    }

    val rotationDegree by animateFloatAsState(
        targetValue =
            if (monthDropdownState != TopBarCalendarView.NoView) {
                180f
            } else {
                0f
            },
        animationSpec =
            tween(
                durationMillis = 300,
                easing = EaseInCubic,
            ),
        label = "rotation",
    )

    val monthTitle =
        if (showYear) {
            "${dateState.selectedInViewMonth.month.name.toSentenceCase()} ${dateState.selectedInViewMonth.year}"
        } else {
            dateState.selectedInViewMonth.month.name
                .toSentenceCase()
        }

    Column(
        modifier =
            Modifier
                .background(
                    color = XCalendarTheme.colorScheme.surfaceContainerLow,
                ).animateContentSize(),
    ) {
        TopAppBar(
            colors =
                TopAppBarColors(
                    containerColor = XCalendarTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = XCalendarTheme.colorScheme.surfaceContainerLow,
                    navigationIconContentColor = XCalendarTheme.colorScheme.onSurfaceVariant,
                    titleContentColor = XCalendarTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = XCalendarTheme.colorScheme.onSurfaceVariant,
                    subtitleContentColor = XCalendarTheme.colorScheme.onSurfaceVariant,
                ),
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = FontAwesomeIcons.Solid.Bars,
                        contentDescription = "Menu",
                    )
                }
            },
            title = {
                Row(
                    modifier =
                        Modifier.noRippleClickable {
                            val toggleView =
                                if (monthDropdownState != TopBarCalendarView.NoView) {
                                    TopBarCalendarView.NoView
                                } else {
                                    TopBarCalendarView.Month
                                }
                            onToggleMonthDropdown(toggleView)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = monthTitle,
                        transitionSpec = {
                            slideInVertically(
                                initialOffsetY = { -it },
                                animationSpec = tween(durationMillis = 300),
                            ) togetherWith
                                slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(durationMillis = 300),
                                )
                        },
                        label = "monthTitleAnimation",
                    ) { animatedMonthTitle ->
                        Text(
                            text = animatedMonthTitle,
                            color = XCalendarTheme.colorScheme.onSurface,
                        )
                    }
                    Icon(
                        modifier = Modifier.size(16.dp).rotate(rotationDegree),
                        imageVector = FontAwesomeIcons.Solid.CaretDown,
                        contentDescription = "Toggle Month Dropdown",
                    )
                }
            },
            actions = {
                IconButton(onClick = { /* Handle search */ }) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = FontAwesomeIcons.Solid.Search,
                        contentDescription = "Search",
                    )
                }
                IconButton(onClick = onSelectToday) {
                    Box(
                        modifier =
                            Modifier
                                .graphicsLayer { rotationZ = rotationAngle.value }
                                .clip(MaterialShapes.Cookie9Sided.toShape())
                                .size(24.dp)
                                .background(XCalendarTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dateState.currentDate.day.toString(),
                            style = XCalendarTheme.typography.bodySmall,
                            color = XCalendarTheme.colorScheme.onSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.graphicsLayer { rotationZ = -rotationAngle.value },
                        )
                    }
                }
                CoilImage(
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape),
                    imageModel = {
                        "https://t4.ftcdn.net/jpg/00/04/09/63/360_F_4096398_nMeewldssGd7guDmvmEDXqPJUmkDWyqA.jpg"
                    },
                )
            },
        )

        when (monthDropdownState) {
            TopBarCalendarView.Month -> {
                TopBarMonthView(
                    month =
                        YearMonth(
                            dateState.selectedInViewMonth.year,
                            dateState
                                .selectedInViewMonth.month,
                        ),
                    events = events,
                    holidays = holidays,
                    onDayClick = onDayClick,
                )
            }

            else -> {}
        }
    }
}

@Composable
private fun TopBarMonthView(
    month: YearMonth,
    events: List<Event>,
    holidays: List<Holiday>,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstDayOfMonth = LocalDate(month.year, month.month, 1)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.ordinal + 1
    val daysInMonth = month.month.lengthOfMonth(month.year.isLeap())
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
    ) {
        item(span = { GridItemSpan(7) }) {
            TopAppBarWeekdayHeader()
        }

        items(firstDayOfWeek) {
            TopAppBarEmptyPagingDayCell()
        }

        items(daysInMonth) { day ->
            val date = LocalDate(month.year, month.month, day + 1)
            TopAppBarDayCell(
                date = date,
                events =
                    events.filter { event ->
                        event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date == date
                    },
                holidays =
                    holidays.filter { holiday ->
                        holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date == date
                    },
                onDayClick = onDayClick,
            )
        }
    }
}

@Composable
private fun TopAppBarWeekdayHeader() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")

        daysOfWeek.forEach { day ->
            Text(
                text = day,
                style = XCalendarTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = XCalendarTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun TopAppBarDayCell(
    date: LocalDate,
    events: List<Event>,
    holidays: List<Holiday>,
    onDayClick: (LocalDate) -> Unit,
) {
    val today =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    val isToday = date == today
    Column(
        modifier = Modifier.aspectRatio(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.day.toString(),
            style = XCalendarTheme.typography.bodySmall,
            color =
                when {
                    isToday -> XCalendarTheme.colorScheme.inverseOnSurface
                    else -> XCalendarTheme.colorScheme.onSurfaceVariant
                },
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .background(
                        when {
                            isToday -> XCalendarTheme.colorScheme.primary
                            else -> Color.Transparent
                        },
                        CircleShape,
                    ).padding(4.dp)
                    .clickable { onDayClick(date) },
        )

        // Holidays
        holidays.firstOrNull()?.let { holiday ->
            Text(
                text = holiday.name,
                style = XCalendarTheme.typography.bodyMedium,
                color = Color(0xFF2196F3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp,
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth()
                        .background(Color(0xFF2196F3).copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                        .padding(2.dp),
            )
        }

        val maxEventsToDisplay = 3
        val displayedEvents = events.take(maxEventsToDisplay)

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
        ) {
            displayedEvents.forEach { event ->
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .background(
                                Color(event.color),
                                CircleShape,
                            ).padding(1.dp),
                )
                Spacer(modifier = Modifier.width(2.dp))
            }

            if (events.size > maxEventsToDisplay) {
                Text(
                    text = "+${events.size - maxEventsToDisplay}",
                    fontSize = 10.sp,
                    color = XCalendarTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun TopAppBarEmptyPagingDayCell() {
    Box(
        modifier = Modifier.padding(4.dp),
    )
}
