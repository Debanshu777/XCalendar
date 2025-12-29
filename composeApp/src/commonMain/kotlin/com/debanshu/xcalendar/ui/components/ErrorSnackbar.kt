package com.debanshu.xcalendar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import xcalendar.composeapp.generated.resources.Res
import xcalendar.composeapp.generated.resources.ic_close

@Composable
fun ErrorSnackbar(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long = 4000L
) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        message?.let {
            LaunchedEffect(message) {
                delay(autoDismissMillis)
                onDismiss()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(XCalendarTheme.colorScheme.errorContainer)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = it,
                        style = XCalendarTheme.typography.bodyMedium,
                        color = XCalendarTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = "Dismiss",
                        tint = XCalendarTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onDismiss() }
                    )
                }
            }
        }
    }
}


