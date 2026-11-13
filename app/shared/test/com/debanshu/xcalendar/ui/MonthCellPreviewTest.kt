package com.debanshu.xcalendar.ui

import com.debanshu.xcalendar.ui.screen.monthScreen.components.MonthCellPreviewCapacity
import com.debanshu.xcalendar.ui.screen.monthScreen.components.monthCellPreview
import com.debanshu.xcalendar.ui.screen.monthScreen.components.monthCellPreviewCapacity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthCellPreviewTest {
    @Test
    fun emptyListsProduceNoPreviewsOrOmissions() {
        assertEquals(
            expected = 0,
            actual = monthCellPreview(
                holidayCount = 0,
                eventCount = 0,
                capacity = MonthCellPreviewCapacity(withoutOverflow = 3, withOverflow = 2),
            ).let { it.holidays + it.events + it.omitted },
        )
    }

    @Test
    fun overflowLabelIsSuppressedWhenItCannotFit() {
        assertEquals(false, monthCellPreviewCapacity(7, 10, 8, 2).overflowFits)
        assertEquals(true, monthCellPreviewCapacity(8, 10, 8, 2).overflowFits)
    }
    @Test
    fun tinyCellHasNoPreviewSlotsWhenEvenOneTagCannotFit() {
        assertEquals(
            MonthCellPreviewCapacity(withoutOverflow = 0, withOverflow = 0),
            monthCellPreviewCapacity(
                availableHeightPx = 9,
                tagHeightPx = 10,
                overflowHeightPx = 8,
                gapPx = 2,
            ),
        )
        assertEquals(
            expected = 2,
            actual = monthCellPreview(
                holidayCount = 1,
                eventCount = 1,
                capacity = MonthCellPreviewCapacity(withoutOverflow = 0, withOverflow = 0),
            ).omitted,
        )
    }

    @Test
    fun previewUsesOverflowSpaceOnlyWhenEntriesAreOmitted() {
        val capacity = monthCellPreviewCapacity(
            availableHeightPx = 24,
            tagHeightPx = 10,
            overflowHeightPx = 8,
            gapPx = 2,
        )
        assertEquals(MonthCellPreviewCapacity(withoutOverflow = 2, withOverflow = 1), capacity)

        // One event uses a normal tag slot without sacrificing it to a badge.
        assertEquals(1, monthCellPreview(0, 1, capacity).events)
        assertEquals(0, monthCellPreview(0, 1, capacity).omitted)
    }

    @Test
    fun overflowCountsOmittedHolidaysAndEventsWithHolidayFirstOrdering() {
        val preview = monthCellPreview(
            holidayCount = 2,
            eventCount = 3,
            capacity = MonthCellPreviewCapacity(withoutOverflow = 2, withOverflow = 1),
        )

        assertEquals(1, preview.holidays)
        assertEquals(0, preview.events)
        assertEquals(4, preview.omitted)
    }

    @Test
    fun holidayOnlyOverflowReportsAllUnshownHolidays() {
        val preview = monthCellPreview(
            holidayCount = 4,
            eventCount = 0,
            capacity = MonthCellPreviewCapacity(withoutOverflow = 3, withOverflow = 1),
        )

        assertEquals(1, preview.holidays)
        assertEquals(0, preview.events)
        assertEquals(3, preview.omitted)
    }

    @Test
    fun ampleHeightStillCapsCombinedPreviewsAtThree() {
        val preview = monthCellPreview(
            holidayCount = 2,
            eventCount = 5,
            capacity = MonthCellPreviewCapacity(withoutOverflow = 8, withOverflow = 8),
        )

        assertEquals(2, preview.holidays)
        assertEquals(1, preview.events)
        assertEquals(4, preview.omitted)
    }

    @Test
    fun capacityChangesAtExactMeasuredPixelBoundaries() {
        val exactTagFit = monthCellPreviewCapacity(
            availableHeightPx = 12,
            tagHeightPx = 10,
            overflowHeightPx = 8,
            gapPx = 2,
        )
        val onePixelShort = monthCellPreviewCapacity(
            availableHeightPx = 11,
            tagHeightPx = 10,
            overflowHeightPx = 8,
            gapPx = 2,
        )
        assertEquals(1, exactTagFit.withoutOverflow)
        assertEquals(0, onePixelShort.withoutOverflow)

        assertTrue(monthCellPreviewCapacity(8, 10, 8, 2).overflowFits)
        assertEquals(false, monthCellPreviewCapacity(7, 10, 8, 2).overflowFits)
    }

    @Test
    fun shrinkingCapacityPreservesTheTotalAcrossVisibleAndOmittedItems() {
        val total = 5
        val larger = monthCellPreview(
            holidayCount = 2,
            eventCount = 3,
            capacity = MonthCellPreviewCapacity(withoutOverflow = 3, withOverflow = 2),
        )
        val smaller = monthCellPreview(
            holidayCount = 2,
            eventCount = 3,
            capacity = MonthCellPreviewCapacity(withoutOverflow = 1, withOverflow = 1),
        )

        assertEquals(total, larger.holidays + larger.events + larger.omitted)
        assertEquals(total, smaller.holidays + smaller.events + smaller.omitted)
        assertTrue(smaller.holidays + smaller.events < larger.holidays + larger.events)
    }
}
