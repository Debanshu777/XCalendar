package com.debanshu.xcalendar.ui.screen.scheduleScreen.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.debanshu.xcalendar.common.toSentenceCase
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.ui.theme.XCalendarTheme
import com.skydoves.landscapist.coil3.CoilImage
import kotlinx.datetime.Month

private fun getMonthImageUrl(month: Month): String {
    val baseUrl = "https://raw.githubusercontent.com/Debanshu777/XCalendar/main/assets/"

    return when (month) {
        Month.JANUARY -> "${baseUrl}January.jpg"
        Month.FEBRUARY -> "${baseUrl}February.jpg"
        Month.MARCH -> "${baseUrl}March.jpg"
        Month.APRIL -> "${baseUrl}April.jpg"
        Month.MAY -> "${baseUrl}May.jpg"
        Month.JUNE -> "${baseUrl}June.jpg"
        Month.JULY -> "${baseUrl}July.jpg"
        Month.AUGUST -> "${baseUrl}August.jpg"
        Month.SEPTEMBER -> "${baseUrl}September.jpg"
        Month.OCTOBER -> "${baseUrl}October.jpg"
        Month.NOVEMBER -> "${baseUrl}November.jpg"
        Month.DECEMBER -> "${baseUrl}December.jpg"
    }
}

@Composable
fun MonthHeader(yearMonth: YearMonth) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        CoilImage(
            imageModel = { getMonthImageUrl(yearMonth.month) },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, start = 64.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = "${yearMonth.month.name.toSentenceCase()} ${yearMonth.year}",
                style = XCalendarTheme.typography.headlineSmall,
                color = XCalendarTheme.colorScheme.inverseOnSurface
            )
        }
    }
}