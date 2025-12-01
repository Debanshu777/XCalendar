package com.debanshu.xcalendar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debanshu.xcalendar.common.GlassShaderParams
import com.debanshu.xcalendar.common.applyIf
import com.debanshu.xcalendar.common.createGlassRenderEffect
import com.debanshu.xcalendar.common.noRippleClickable
import com.debanshu.xcalendar.ui.navigation.NavigableScreen
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import xcalendar.composeapp.generated.resources.Res
import xcalendar.composeapp.generated.resources.ic_add
import xcalendar.composeapp.generated.resources.ic_calendar_view_day
import xcalendar.composeapp.generated.resources.ic_calendar_view_month
import xcalendar.composeapp.generated.resources.ic_calendar_view_schedule
import xcalendar.composeapp.generated.resources.ic_calendar_view_three_day
import xcalendar.composeapp.generated.resources.ic_calendar_view_week
import kotlin.math.abs

@Composable
internal fun CalendarBottomNavigationBar(
    modifier: Modifier = Modifier,
    selectedView: NavigableScreen,
    onViewSelect: (NavigableScreen) -> Unit,
    onAddClick: () -> Unit,
) {
    // Define navigation items
    val navItems =
        remember {
            listOf(
                NavItem(NavigableScreen.Schedule, Res.drawable.ic_calendar_view_schedule, "Schedule"),
                NavItem(NavigableScreen.Day, Res.drawable.ic_calendar_view_day, "Day"),
                NavItem(NavigableScreen.ThreeDay, Res.drawable.ic_calendar_view_three_day, "3 Day"),
                NavItem(NavigableScreen.Week, Res.drawable.ic_calendar_view_week, "Week"),
                NavItem(NavigableScreen.Month, Res.drawable.ic_calendar_view_month, "Month"),
            )
        }

    // State management
    val coroutineScope = rememberCoroutineScope()
    val itemMetrics = remember { mutableStateMapOf<Int, ItemMetrics>() }
    val indicatorOffset = remember { Animatable(0f) }
    val indicatorScale = remember { Animatable(1f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var indicatorWidthPx by remember { mutableFloatStateOf(0f) }
    var indicatorInitialized by remember { mutableStateOf(false) }

    // Scale factor for dragging state (25% increase)
    val dragScaleFactor = 1.25f

    // Calculate current selected index - updates on every recomposition when selectedView changes
    // This is derived from the prop, so it reflects the parent's state
    val selectedIndex =
        navItems.indexOfFirst {
            it.screen == selectedView
        }

    // Wrap callback to prevent recomposition capture
    val currentOnViewSelect by rememberUpdatedState(onViewSelect)

    // Cache drag boundaries - computed once when item metrics change
    val dragBounds by remember {
        derivedStateOf {
            if (itemMetrics.size == navItems.size && navItems.isNotEmpty()) {
                val firstLeft = itemMetrics[0]?.left ?: 0f
                val lastLeft = itemMetrics[navItems.size - 1]?.left ?: 0f
                firstLeft to lastLeft
            } else {
                0f to 0f
            }
        }
    }

    // Cache item centers for snapping - computed once when item metrics change
    val itemCenters by remember {
        derivedStateOf {
            itemMetrics.mapValues { (_, metrics) ->
                metrics.left + (metrics.width / 2f)
            }
        }
    }

    // Update indicator position when selected view changes (not during drag)
    LaunchedEffect(selectedIndex, itemMetrics.size, indicatorWidthPx, isDragging) {
        if (
            !isDragging &&
            selectedIndex >= 0 &&
            itemMetrics.containsKey(selectedIndex)
        ) {
            indicatorWidthPx = itemMetrics[selectedIndex]?.width ?: indicatorWidthPx
            val targetPos = itemMetrics[selectedIndex]?.left ?: return@LaunchedEffect
            if (indicatorInitialized && abs(indicatorOffset.value - targetPos) >= 0.5f) {
                indicatorOffset.animateTo(
                    targetValue = targetPos,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessHigh,
                        ),
                )
            } else {
                indicatorOffset.snapTo(targetPos)
                indicatorInitialized = true
            }
        }
    }

    // Initialize indicator position
    LaunchedEffect(itemMetrics.size) {
        if (itemMetrics.size != navItems.size) {
            indicatorInitialized = false
            return@LaunchedEffect
        }
        if (selectedIndex < 0) return@LaunchedEffect
        if (!indicatorInitialized) {
            val initialPos = itemMetrics[selectedIndex]?.left ?: 0f
            indicatorOffset.snapTo(initialPos)
            indicatorInitialized = true
        }
    }

    // Animate scale when dragging state changes
    LaunchedEffect(isDragging) {
        indicatorScale.animateTo(
            targetValue = if (isDragging) dragScaleFactor else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh,
                ),
        )
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Navigation bar container with glass shader effect
        var navBarSize by remember { mutableStateOf(IntSize.Zero) }

        // Create glass effect when dragging - applied to entire nav bar
        val glassEffect =
            remember(navBarSize, isDragging, dragOffset, indicatorWidthPx) {
                if (isDragging && navBarSize.width > 0 && navBarSize.height > 0 && indicatorWidthPx > 0f) {
                    // Calculate normalized center position for the glass shape
                    val centerX = (dragOffset + indicatorWidthPx / 2f) / navBarSize.width
                    val centerY = 0.5f // Vertically centered

                    // Calculate normalized width and height for the glass rectangle
                    // Scale based on indicator scale for smooth animation
                    val glassWidth = (indicatorWidthPx / navBarSize.width) * 1.2f * indicatorScale.value
                    val glassHeight = 1.1f * indicatorScale.value // Extends beyond nav bar height

                    // Corner radius - half of height for pill shape, or smaller for rounded rect
                    val cornerRadius = glassHeight * 0.5f // Pill shape

                    createGlassRenderEffect(
                        width = navBarSize.width.toFloat(),
                        height = navBarSize.height.toFloat(),
                        params =
                            GlassShaderParams.waterDroplet().copy(
                                width = glassWidth,
                                height = glassHeight,
                                centerX = centerX,
                                centerY = centerY,
                                cornerRadius = cornerRadius,
                            ),
                    )
                } else {
                    null
                }
            }

        Box(
            modifier =
                Modifier
                    .height(56.dp)
                    .weight(1f)
                    .onSizeChanged { navBarSize = it }
                    .graphicsLayer {
                        renderEffect = glassEffect
                    },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(30.dp))
                        .background(XCalendarTheme.colorScheme.surfaceContainer)
                        .padding(3.dp)
                        .pointerInput(selectedIndex) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragStartIndex = selectedIndex
                                    dragOffset = indicatorOffset.value
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    val (minBound, maxBound) = dragBounds
                                    dragOffset =
                                        (dragOffset + dragAmount).coerceIn(minBound, maxBound)
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        val indicatorCenter = dragOffset + (indicatorWidthPx / 2f)

                                        val nearestIndex =
                                            itemCenters
                                                .minByOrNull { (_, center) ->
                                                    abs(center - indicatorCenter)
                                                }?.key ?: dragStartIndex

                                        val targetPos =
                                            itemMetrics[nearestIndex]?.left ?: dragOffset

                                        indicatorOffset.animateTo(
                                            targetValue = targetPos,
                                            animationSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessHigh,
                                                ),
                                        )

                                        isDragging = false

                                        if (nearestIndex != dragStartIndex) {
                                            currentOnViewSelect(navItems[nearestIndex].screen)
                                        }
                                    }
                                },
                            )
                        },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navItems.forEachIndexed { index, navItem ->
                    BottomNavItem(
                        modifier =
                            Modifier.onGloballyPositioned { coordinates ->
                                itemMetrics[index] =
                                    ItemMetrics(
                                        left = coordinates.positionInParent().x,
                                        width = coordinates.size.width.toFloat(),
                                    )
                                if (indicatorWidthPx == 0f || index == selectedIndex) {
                                    indicatorWidthPx = coordinates.size.width.toFloat()
                                }
                            },
                        selected = selectedIndex == index,
                        isDragging = isDragging,
                        onClick = {
                            if (index != selectedIndex && !isDragging) {
                                coroutineScope.launch {
                                    val targetPos =
                                        itemMetrics[index]?.left ?: indicatorOffset.value
                                    indicatorOffset.animateTo(
                                        targetValue = targetPos,
                                        animationSpec =
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessHigh,
                                            ),
                                    )
                                    currentOnViewSelect(navItem.screen)
                                }
                            }
                        },
                        icon = navItem.icon,
                        label = navItem.label,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        FloatingActionButton(
            onClick = onAddClick,
            shape = CircleShape,
            containerColor = XCalendarTheme.colorScheme.primary,
            contentColor = XCalendarTheme.colorScheme.onPrimary,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_add),
                contentDescription = "Add Event",
            )
        }
    }
}

private data class NavItem(
    val screen: NavigableScreen,
    val icon: DrawableResource,
    val label: String,
)

private data class ItemMetrics(
    val left: Float,
    val width: Float,
)

@Composable
private fun RowScope.BottomNavItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    icon: DrawableResource,
    label: String,
) {
    Column(
        modifier =
            modifier
                .noRippleClickable(onClick = onClick)
                .weight(1f)
                .fillMaxHeight()
                .applyIf(selected && !isDragging) {
                    background(
                        color = XCalendarTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(30.dp),
                    ).padding(horizontal = 3.dp)
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint =
                if (selected && !isDragging) {
                    XCalendarTheme.colorScheme.primary
                } else {
                    XCalendarTheme.colorScheme.onSurfaceVariant
                },
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = XCalendarTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color =
                if (selected && !isDragging) {
                    XCalendarTheme.colorScheme.primary
                } else {
                    XCalendarTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
