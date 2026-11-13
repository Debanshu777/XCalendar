package com.debanshu.xcalendar.ui

import com.debanshu.xcalendar.ui.screen.monthScreen.components.dayCellAccessibilityText
import com.debanshu.xcalendar.ui.screen.monthScreen.components.dayCellCountsDescription
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DayCellAccessibilityTest {
    @Test
    fun aggregateTextContainsFullDateVisibleTitlesAndOmittedCount() {
        assertEquals(
            "2026-11-13. Holiday: Veterans Day observed. Event: Long event title beyond the visible ellipsis. 2 more",
            dayCellAccessibilityText(
                date = LocalDate(2026, 11, 13),
                isToday = false,
                holidayNames = listOf("Veterans Day observed"),
                eventTitles = listOf("Long event title beyond the visible ellipsis"),
                omittedCount = 2,
            ),
        )
    }

    @Test
    fun aggregateTextIncludesTodayAndCompactModeOmissions() {
        assertEquals(
            "2026-11-13. Today. 5 more",
            dayCellAccessibilityText(
                date = LocalDate(2026, 11, 13),
                isToday = true,
                holidayNames = emptyList(),
                eventTitles = emptyList(),
                omittedCount = 5,
            ),
        )
    }

    @Test
    fun countDescriptionRetainsTotalCountsWithCorrectSingularForms() {
        assertEquals("1 holiday, 2 events", dayCellCountsDescription(1, 2))
        assertEquals("0 holidays, 1 event", dayCellCountsDescription(0, 1))
    }
}
