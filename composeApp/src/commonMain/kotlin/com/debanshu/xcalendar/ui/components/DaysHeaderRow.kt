package com.debanshu.xcalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.ui.theme.LocalSharedTransitionScope
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DaysHeaderRow(
    startDate: LocalDate,
    numDays: Int,
    currentDate: LocalDate,
    holidaysByDate: ImmutableMap<LocalDate, ImmutableList<Holiday>>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    dynamicHeaderHeightState: MutableState<Int>?,
) {
    val sharedElementScope = LocalSharedTransitionScope.current
    val dates =
        List(numDays) { index ->
            startDate.plus(DatePeriod(days = index))
        }

    with(sharedElementScope) {
        Row(
            modifier =
                modifier
                    .sharedBounds(
                        rememberSharedContentState("DaysHeaderRow"),
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                    ).background(XCalendarTheme.colorScheme.surfaceContainerLow)
                    .height(IntrinsicSize.Min)
                    .heightIn(min = 60.dp)
                    .onGloballyPositioned {
                        if (dynamicHeaderHeightState != null) {
                            dynamicHeaderHeightState.value = it.size.height
                        }
                    },
        ) {
            if (numDays > 1) {
                dates.forEach { date ->
                    val isToday = date == currentDate
                    val currentDayHolidays = holidaysByDate[date] ?: persistentListOf()

                    Column(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .padding(top = 8.dp)
                                .clickable { onDayClick(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            modifier =
                                Modifier.sharedElement(
                                    rememberSharedContentState("${date.dayOfWeek.name.take(3)}"),
                                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                ),
                            text = date.dayOfWeek.name.take(3),
                            style = XCalendarTheme.typography.labelSmall,
                        )
                        Box(
                            modifier =
                                Modifier
                                    .sharedBounds(
                                        rememberSharedContentState("BOX_${date.day}"),
                                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                    ).padding(vertical = XCalendarTheme.dimensions.spacing_4)
                                    .clip(MaterialShapes.Cookie9Sided.toShape())
                                    .size(30.dp)
                                    .background(
                                        when {
                                            isToday -> XCalendarTheme.colorScheme.primary
                                            else -> Color.Transparent
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                modifier =
                                    Modifier.sharedElement(
                                        rememberSharedContentState("${date.day}"),
                                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                    ),
                                text = date.day.toString(),
                                style = XCalendarTheme.typography.bodyMedium,
                                color =
                                    when {
                                        isToday -> XCalendarTheme.colorScheme.inverseOnSurface
                                        else -> XCalendarTheme.colorScheme.onSurface
                                    },
                            )
                        }
                        if (currentDayHolidays.isNotEmpty()) {
                            Column {
                                currentDayHolidays.take(2).forEach { holiday ->
                                    EventTag(
                                        modifier =
                                            Modifier
                                                .padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                                                .fillMaxWidth(),
                                        text = holiday.name,
                                        color = Color(0xFF007F73),
                                        textColor = XCalendarTheme.colorScheme.inverseOnSurface,
                                    )
                                }

                                if (currentDayHolidays.size > 2) {
                                    val extraCount = currentDayHolidays.size - 2
                                    Text(
                                        text = "+$extraCount more",
                                        style = XCalendarTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        textAlign = TextAlign.Start,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = XCalendarTheme.colorScheme.onSurfaceVariant,
                                        modifier =
                                            Modifier
                                                .padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                                                .fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val currentDayHolidays = holidaysByDate[dates.first()] ?: emptyList()
                var holidaysExpanded by remember { mutableStateOf(false) }
                Column(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .weight(1f),
                ) {
                    if (currentDayHolidays.isNotEmpty()) {
                        val displayHolidays =
                            if (holidaysExpanded) {
                                currentDayHolidays
                            } else {
                                currentDayHolidays.take(2)
                            }
                        displayHolidays.forEach { holiday ->
                            Text(
                                text = holiday.name,
                                style = XCalendarTheme.typography.labelMedium,
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = XCalendarTheme.colorScheme.inverseOnSurface,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .fillMaxWidth()
                                        .background(
                                            Color(0xFF007F73),
                                            RoundedCornerShape(8.dp),
                                        ).padding(8.dp),
                            )
                        }

                        if (currentDayHolidays.size > 2 && !holidaysExpanded) {
                            val extraCount = currentDayHolidays.size - 2
                            Text(
                                text = "+$extraCount more",
                                style = XCalendarTheme.typography.labelMedium,
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = XCalendarTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .fillMaxWidth()
                                        .clickable { holidaysExpanded = true },
                            )
                        } else if (holidaysExpanded && currentDayHolidays.size > 2) {
                            Text(
                                text = "Show less",
                                style = XCalendarTheme.typography.labelMedium,
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = XCalendarTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .fillMaxWidth()
                                        .clickable { holidaysExpanded = false },
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
