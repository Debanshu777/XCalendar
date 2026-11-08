package com.debanshu.xcalendar.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier

/**
 * A generic swipeable pager component that provides infinite horizontal scrolling
 * with automatic state management and external navigation support.
 *
 * This component abstracts the common paging logic used across different calendar views,
 * allowing for code reuse while maintaining type safety and flexibility.
 *
 * Uses a callback approach for currentReference to ensure it always receives the latest state.
 *
 * @param T The type of reference (e.g., YearMonth, LocalDate)
 * @param modifier The modifier to apply to the pager
 * @param currentReference Lambda providing the current reference value being displayed
 * @param calculateOffset Function to calculate the page offset from the base reference
 * @param pageToReference Function to convert a page index to a reference value
 * @param onReferenceChange Callback when the reference changes due to user swipe
 * @param content The composable content to display for each page
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T> SwipeablePager(
    modifier: Modifier = Modifier,
    currentReference: T,
    calculateOffset: (current: T, base: T) -> Int,
    pageToReference: (baseReference: T, initialPage: Int, page: Int) -> T,
    onReferenceChange: (T) -> Unit,
    content: @Composable (reference: T) -> Unit,
) {
    val totalPages = 10000
    val initialPage = totalPages / 2

    // Preserve an anchor while swiping; rebase only for external dates that
    // cannot be represented by this page stride (for example a three-day view).
    var baseReference by remember { mutableStateOf(currentReference) }

    val pagerState =
        rememberPagerState(
            initialPage = initialPage,
            pageCount = { totalPages },
        )

    val latestPageToReference by rememberUpdatedState(pageToReference)
    val pageConverter: (Int) -> T =
        remember(baseReference, initialPage) {
            { page ->
                latestPageToReference(baseReference, initialPage, page)
            }
        }

    val latestReference by rememberUpdatedState(currentReference)
    val latestOnReferenceChange by rememberUpdatedState(onReferenceChange)
    val latestPageConverter by rememberUpdatedState(pageConverter)

    // Track current displayed reference and notify changes
    // Use settledPage to avoid conflicts during scroll animations
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { page ->
                val newReference = latestPageConverter(page)
                if (newReference != latestReference) {
                    latestOnReferenceChange(newReference)
                }
            }
    }

    // Handle external reference changes (e.g., "Select Today" button)
    LaunchedEffect(currentReference) {
        val targetOffset = calculateOffset(currentReference, baseReference)
        val targetPage = initialPage + targetOffset

        if (targetPage !in 0 until totalPages || pageConverter(targetPage) != currentReference) {
            baseReference = currentReference
            pagerState.scrollToPage(initialPage)
            return@LaunchedEffect
        }

        // Only scroll if the settled page doesn't match the target
        // This prevents interference with ongoing user swipes
        if (pagerState.settledPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    HorizontalPager(
        modifier = modifier,
        state = pagerState,
    ) { page ->
        val reference = pageConverter(page)
        content(reference)
    }
}
