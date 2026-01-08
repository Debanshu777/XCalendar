package com.debanshu.xcalendar.ui.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.common.noRippleClickable
import com.debanshu.xcalendar.domain.model.Calendar
import com.debanshu.xcalendar.domain.model.User
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.skydoves.landscapist.coil3.CoilImage
import kotlinx.collections.immutable.ImmutableList

/**
 * Section for selecting a calendar with user avatar and calendar list.
 */
@Composable
internal fun CalendarSelectionSection(
    user: User,
    calendars: ImmutableList<Calendar>,
    selectedCalendarId: String,
    onCalendarSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentSelectedId by remember { mutableStateOf(selectedCalendarId) }

    Column(modifier = modifier) {
        // User row with avatar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 16.dp),
        ) {
            CoilImage(
                imageModel = { user.photoUrl },
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = user.email,
                style = XCalendarTheme.typography.bodySmall,
                color = XCalendarTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        // Calendar list
        CalendarList(
            calendars = calendars,
            selectedCalendarId = currentSelectedId,
            onCalendarSelected = { id ->
                currentSelectedId = id
                onCalendarSelected(id)
            },
        )
    }
}

/**
 * Horizontal scrollable list of calendars.
 */
@Composable
private fun CalendarList(
    calendars: ImmutableList<Calendar>,
    selectedCalendarId: String,
    onCalendarSelected: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(calendars) { index, calendar ->
            val isSelected = selectedCalendarId == calendar.id

            Row(
                modifier =
                    Modifier
                        .padding(
                            start = if (index == 0) 50.dp else 0.dp,
                            end = if (index == calendars.size - 1) 16.dp else 0.dp,
                        ).border(
                            0.5.dp,
                            XCalendarTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        ).background(
                            color =
                                if (!isSelected) {
                                    XCalendarTheme.colorScheme.surfaceContainerLow
                                } else {
                                    XCalendarTheme.colorScheme.primary
                                },
                            RoundedCornerShape(8.dp),
                        ).padding(8.dp)
                        .noRippleClickable { onCalendarSelected(calendar.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Color indicator
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(calendar.color)),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = calendar.name,
                    style = XCalendarTheme.typography.bodySmall,
                    color =
                        if (!isSelected) {
                            XCalendarTheme.colorScheme.onSurfaceVariant
                        } else {
                            XCalendarTheme.colorScheme.onPrimary
                        },
                )
            }
        }
    }
}
