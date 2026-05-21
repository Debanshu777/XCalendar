package com.debanshu.xcalendar.concurrency

import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.common.toLocalDateTime
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.test.TestDataFactory
import com.debanshu.xcalendar.ui.model.EventsByDate
import com.debanshu.xcalendar.ui.model.HolidaysByDate
import com.debanshu.xcalendar.ui.screen.scheduleScreen.ScheduleItem
import com.debanshu.xcalendar.ui.state.ScheduleStateHolder
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 2 / F3 fuzz coverage.
 *
 * Hammers [ScheduleStateHolder] with 100 concurrent pagination calls on
 * [Dispatchers.Default]. Without the [kotlinx.coroutines.sync.Mutex] guard
 * added in F3, the underlying `SnapshotStateList` and per-date caches race
 * and produce duplicate uniqueIds and / or non-monotonic month ordering.
 *
 * Assertions:
 * - Every [ScheduleItem.uniqueId] is unique (no double-insert).
 * - [ScheduleItem.MonthHeader]s appear in strictly increasing month order
 *   (year * 12 + month). This is the strongest available invariant on the
 *   single-list output of the holder.
 *
 * This test deliberately uses real [Dispatchers.Default] (not the test
 * dispatcher) so that JVM thread-scheduling actually interleaves the
 * mutations under stress.
 */
class ScheduleStateHolderConcurrencyTest {

    private fun createEventsByDate(events: List<Event>): EventsByDate =
        EventsByDate(
            events.groupBy { event ->
                event.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
            }.mapValues { (_, eventList) ->
                eventList.toImmutableList()
            }.toImmutableMap(),
        )

    private fun createHolidaysByDate(holidays: List<Holiday>): HolidaysByDate =
        HolidaysByDate(
            holidays.groupBy { holiday ->
                holiday.date.toLocalDateTime(TimeZone.currentSystemDefault()).date
            }.mapValues { (_, holidayList) ->
                holidayList.toImmutableList()
            }.toImmutableMap(),
        )

    @Test
    fun `100 parallel loadMore calls do not corrupt items`() = runTest {
        val initialMonth = YearMonth(2026, 1)
        val events = TestDataFactory.createEvents(count = 200)
        val holidays = TestDataFactory.createHolidays(count = 12)
        val eventsByDate = createEventsByDate(events)
        val holidaysByDate = createHolidaysByDate(holidays)
        val holder = ScheduleStateHolder(
            initialMonth = initialMonth,
            getEventsByDate = { eventsByDate },
            getHolidaysByDate = { holidaysByDate },
        )
        holder.initialize()

        // Fire 50 forward + 50 backward expansions in parallel on the
        // multi-threaded compute pool. Each call internally takes the mutex.
        withContext(Dispatchers.Default) {
            coroutineScope {
                val ops = (0 until 100).map { i ->
                    async {
                        if (i % 2 == 0) holder.loadMoreForward()
                        else holder.loadMoreBackward()
                    }
                }
                ops.awaitAll()
            }
        }

        val items = holder.items.toList()

        // Invariant 1: no duplicate keys (would imply the same range was
        // appended twice, or addAll() interleaved with another addAll).
        val ids = items.map { it.uniqueId }
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue(
            duplicates.isEmpty(),
            "Duplicate uniqueIds after 100 parallel loadMore: $duplicates",
        )
        assertEquals(ids.size, ids.toSet().size, "uniqueId set size mismatch")

        // Invariant 2: month headers strictly monotonic.
        val monthHeaders = items.filterIsInstance<ScheduleItem.MonthHeader>()
        assertTrue(monthHeaders.size > 1, "Need >1 month header to check ordering")
        val ordinals = monthHeaders.map { it.yearMonth.year * 12 + it.yearMonth.month.ordinal }
        ordinals.zipWithNext { a, b ->
            assertTrue(
                a < b,
                "Month headers not strictly increasing: $a then $b in $ordinals",
            )
        }
    }

    @Test
    fun `refresh racing with loadMore preserves invariants`() = runTest {
        val initialMonth = YearMonth(2026, 1)
        var events = TestDataFactory.createEvents(count = 100)
        val holidays = TestDataFactory.createHolidays(count = 12)
        var eventsByDate = createEventsByDate(events)
        val holidaysByDate = createHolidaysByDate(holidays)
        val holder = ScheduleStateHolder(
            initialMonth = initialMonth,
            getEventsByDate = { eventsByDate },
            getHolidaysByDate = { holidaysByDate },
        )
        holder.initialize()

        withContext(Dispatchers.Default) {
            coroutineScope {
                val ops = (0 until 60).map { i ->
                    async {
                        when (i % 3) {
                            0 -> holder.loadMoreForward()
                            1 -> holder.loadMoreBackward()
                            else -> {
                                // Swap the event list mid-flight to stress the
                                // refresh path against pagination.
                                events = TestDataFactory.createEvents(
                                    count = 100,
                                    startFromTimestamp = 1_800_000_000_000L + i,
                                )
                                eventsByDate = createEventsByDate(events)
                                holder.refreshItems()
                            }
                        }
                    }
                }
                ops.awaitAll()
            }
        }

        val items = holder.items.toList()
        val ids = items.map { it.uniqueId }
        assertEquals(
            ids.size,
            ids.toSet().size,
            "Duplicate uniqueIds after refresh-vs-loadMore race",
        )
    }
}
