package com.debanshu.xcalendar.ui.screen.monthScreen.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A swipeable month view component that allows infinite horizontal paging through months
 * while preserving the selected day between swipes.
 *
 * Optimized with sliding window approach to minimize recomposition:
 * - Only generates new month data when actually needed
 * - Maintains a window of 3 months (previous, current, next)
 * - Updates the window efficiently when swiping
 * - Caches events and holidays to avoid repeated calculations
 * - Optimized state management for smooth animations
 */
@Composable
fun SwipeableMonthView(
    modifier: Modifier = Modifier,
    currentMonth: YearMonth,
    events: () -> List<Event>,
    holidays: () -> List<Holiday>,
    onSpecificDayClicked: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    var swipeState by remember { mutableStateOf(SwipeState()) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    val screenWidth by remember {
        derivedStateOf { screenSize.width.toFloat() }
    }

    // Optimized sliding window with persistent state - only created once
    val monthWindow by remember {
        mutableStateOf(
            MonthWindow(
                previous = MonthViewData(currentMonth.plusMonths(-1), events, holidays),
                current = MonthViewData(currentMonth, events, holidays),
                next = MonthViewData(currentMonth.plusMonths(1), events, holidays),
            ),
        )
    }

    // Update window only when month actually changes (not on every recomposition)
    val currentMonthKey = remember(currentMonth) { currentMonth.toString() }
    remember(currentMonthKey) {
        // Only update if the month actually changed
        if (monthWindow.current.month != currentMonth) {
            monthWindow.updateToMonth(currentMonth, events, holidays)
        }
    }

    // Optimized animation with better state management
    val animatedOffset by animateFloatAsState(
        targetValue = swipeState.targetOffsetX,
        animationSpec = tween(durationMillis = 250), // Slightly faster animation
        finishedListener = { finalValue ->
            if (swipeState.isAnimating) {
                when {
                    finalValue > 0 -> {
                        // Swiped to previous month
                        val newCurrentMonth = currentMonth.plusMonths(-1)
                        monthWindow.updateForPreviousMonth(newCurrentMonth, events, holidays)
                        onMonthChange(newCurrentMonth)
                    }
                    finalValue < 0 -> {
                        // Swiped to next month
                        val newCurrentMonth = currentMonth.plusMonths(1)
                        monthWindow.updateForNextMonth(newCurrentMonth, events, holidays)
                        onMonthChange(newCurrentMonth)
                    }
                }
                swipeState = SwipeState() // Reset to initial state
            }
        },
        label = "month_swipe_animation",
    )

    val effectiveOffset = if (swipeState.isAnimating) animatedOffset else swipeState.offsetX

    Surface(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { newSize ->
                    if (screenSize != newSize) {
                        screenSize = newSize
                    }
                }.pointerInput(screenWidth) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = screenWidth * 0.25f // Slightly more sensitive
                            swipeState =
                                if (abs(swipeState.offsetX) > threshold) {
                                    swipeState.copy(
                                        isAnimating = true,
                                        targetOffsetX = if (swipeState.offsetX > 0) screenWidth else -screenWidth,
                                    )
                                } else {
                                    swipeState.copy(isAnimating = true, targetOffsetX = 0f)
                                }
                        },
                        onDragCancel = {
                            swipeState = swipeState.copy(isAnimating = true, targetOffsetX = 0f)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            // Optimize drag handling to reduce state updates
                            if (!swipeState.isAnimating) {
                                val newOffsetX = swipeState.offsetX + dragAmount
                                swipeState =
                                    swipeState.copy(
                                        offsetX = newOffsetX,
                                        targetOffsetX = newOffsetX,
                                    )
                                change.consume()
                            }
                        },
                    )
                },
    ) {
        // Current month view
        MonthViewContainer(
            monthViewData = monthWindow.current,
            onDayClick = onSpecificDayClicked,
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(effectiveOffset.roundToInt(), 0) },
        )

        // Previous month view
        MonthViewContainer(
            monthViewData = monthWindow.previous,
            onDayClick = onSpecificDayClicked,
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            -screenWidth.roundToInt() + effectiveOffset.roundToInt(),
                            0,
                        )
                    },
        )

        // Next month view
        MonthViewContainer(
            monthViewData = monthWindow.next,
            onDayClick = onSpecificDayClicked,
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            screenWidth.roundToInt() + effectiveOffset.roundToInt(),
                            0,
                        )
                    },
        )
    }
}

@Composable
private fun MonthViewContainer(
    monthViewData: MonthViewData,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Cache events and holidays for this specific month to avoid repeated calculations
    val events =
        remember(monthViewData.month) {
            monthViewData.events()
        }
    val holidays =
        remember(monthViewData.month) {
            monthViewData.holidays()
        }

    MonthView(
        modifier = modifier.testTag("MonthView_${monthViewData.month}"),
        month = monthViewData.month,
        events = { events },
        holidays = { holidays },
        onDayClick = onDayClick,
    )
}

@Stable
data class SwipeState(
    val offsetX: Float = 0f,
    val isAnimating: Boolean = false,
    val targetOffsetX: Float = 0f,
)

@Stable
private data class MonthViewData(
    val month: YearMonth,
    val events: () -> List<Event>,
    val holidays: () -> List<Holiday>,
)

/**
 * Optimized sliding window that maintains 3 months of data
 * and efficiently updates when swiping to minimize recomposition
 */
@Stable
private class MonthWindow(
    previous: MonthViewData,
    current: MonthViewData,
    next: MonthViewData,
) {
    var previous by mutableStateOf(previous)
    var current by mutableStateOf(current)
    var next by mutableStateOf(next)

    /**
     * Updates the window when swiping to the previous month
     * - Current becomes next
     * - Previous becomes current
     * - Generate new previous month
     */
    fun updateForPreviousMonth(
        newCurrentMonth: YearMonth,
        events: () -> List<Event>,
        holidays: () -> List<Holiday>,
    ) {
        next = current
        current = previous
        previous = MonthViewData(newCurrentMonth.plusMonths(-1), events, holidays)
    }

    /**
     * Updates the window when swiping to the next month
     * - Previous becomes current
     * - Current becomes next
     * - Generate new next month
     */
    fun updateForNextMonth(
        newCurrentMonth: YearMonth,
        events: () -> List<Event>,
        holidays: () -> List<Holiday>,
    ) {
        previous = current
        current = next
        next = MonthViewData(newCurrentMonth.plusMonths(1), events, holidays)
    }

    /**
     * Updates the window to a specific month (for external month changes)
     * This is used when the month is changed from outside (not by swiping)
     */
    fun updateToMonth(
        targetMonth: YearMonth,
        events: () -> List<Event>,
        holidays: () -> List<Holiday>,
    ) {
        current = MonthViewData(targetMonth, events, holidays)
        previous = MonthViewData(targetMonth.plusMonths(-1), events, holidays)
        next = MonthViewData(targetMonth.plusMonths(1), events, holidays)
    }
}
