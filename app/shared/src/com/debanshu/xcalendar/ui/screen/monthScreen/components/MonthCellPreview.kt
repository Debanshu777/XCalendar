package com.debanshu.xcalendar.ui.screen.monthScreen.components

/** Vertical capacity for day-cell previews, expressed in measured pixels. */
internal data class MonthCellPreviewCapacity(
    val withoutOverflow: Int,
    val withOverflow: Int,
    val overflowFits: Boolean = true,
)

/**
 * Calculates how many one-line tags fit in the space below a day number.
 * The overflow label is included only in [withOverflow], so cells with a small
 * number of entries can use that space for one more preview.
 */
internal fun monthCellPreviewCapacity(
    availableHeightPx: Int,
    tagHeightPx: Int,
    overflowHeightPx: Int,
    gapPx: Int,
    maxPreviews: Int = 3,
): MonthCellPreviewCapacity {
    require(tagHeightPx > 0)
    require(overflowHeightPx > 0)
    require(gapPx >= 0)
    require(maxPreviews >= 0)

    val withoutOverflow = (0..maxPreviews).lastOrNull { count ->
        count * (tagHeightPx + gapPx) <= availableHeightPx
    } ?: 0
    val withOverflow = (0..maxPreviews).lastOrNull { count ->
        count * (tagHeightPx + gapPx) + (if (count > 0) gapPx else 0) + overflowHeightPx <= availableHeightPx
    } ?: 0
    return MonthCellPreviewCapacity(withoutOverflow, withOverflow, availableHeightPx >= overflowHeightPx)
}

/** Returns holiday-first preview counts and the number omitted from both lists. */
internal data class MonthCellPreview(
    val holidays: Int,
    val events: Int,
    val omitted: Int,
)

internal fun monthCellPreview(
    holidayCount: Int,
    eventCount: Int,
    capacity: MonthCellPreviewCapacity,
    maxPreviews: Int = 3,
): MonthCellPreview {
    require(holidayCount >= 0)
    require(eventCount >= 0)
    require(maxPreviews >= 0)

    val total = holidayCount + eventCount
    val noOverflowLimit = minOf(capacity.withoutOverflow, maxPreviews)
    val visibleCount = when {
        total <= noOverflowLimit -> total
        capacity.withOverflow > 0 -> minOf(total, capacity.withOverflow, maxPreviews)
        else -> 0
    }
    val visibleHolidays = minOf(holidayCount, visibleCount)
    val visibleEvents = minOf(eventCount, visibleCount - visibleHolidays)
    return MonthCellPreview(
        holidays = visibleHolidays,
        events = visibleEvents,
        omitted = total - visibleCount,
    )
}
