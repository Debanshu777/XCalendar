package com.debanshu.xcalendar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner size definitions
private val ExtraSmallCornerSize = 4.dp
private val SmallCornerSize = 8.dp
private val MediumCornerSize = 12.dp
private val LargeCornerSize = 16.dp
private val ExtraLargeCornerSize = 24.dp

// Material 3 shape definition with rounded corners
val AppShapes = Shapes(
    // Used for cards, dialogs, and surfaces requiring small corner radius
    extraSmall = RoundedCornerShape(ExtraSmallCornerSize),
    small = RoundedCornerShape(SmallCornerSize),
    medium = RoundedCornerShape(MediumCornerSize),
    large = RoundedCornerShape(LargeCornerSize),
    extraLarge = RoundedCornerShape(ExtraLargeCornerSize)
)

// Calendar specific shapes
val EventShape = RoundedCornerShape(4.dp)
val CalendarDayShape = RoundedCornerShape(50) // Circle for selected days