package com.debanshu.xcalendar.ui

import com.debanshu.xcalendar.ui.transition.EventSharedElementKey
import com.debanshu.xcalendar.ui.transition.SharedElementType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EventSharedElementKeyTest {
    @Test
    fun spanningEventSegmentsHaveDistinctKeysWhileSameDateMatchesAcrossViews() {
        val firstDate = LocalDate(2026, 11, 8)
        val nextDate = LocalDate(2026, 11, 9)
        val dayCard = EventSharedElementKey("overnight", firstDate, SharedElementType.EventCard)
        val weekFirstSegment = EventSharedElementKey("overnight", firstDate, SharedElementType.EventCard)
        val weekSecondSegment = EventSharedElementKey("overnight", nextDate, SharedElementType.EventCard)
        val scheduleSecondSegment = EventSharedElementKey("overnight", nextDate, SharedElementType.EventCard)

        assertEquals(dayCard, weekFirstSegment)
        assertNotEquals(weekFirstSegment, weekSecondSegment)
        assertEquals(weekSecondSegment, scheduleSecondSegment)
        assertEquals(2, setOf(weekFirstSegment, weekSecondSegment).size)
    }
}
