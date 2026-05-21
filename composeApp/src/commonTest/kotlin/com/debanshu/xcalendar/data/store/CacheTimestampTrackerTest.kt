package com.debanshu.xcalendar.data.store

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 4 — F5 validator wiring; Phase 9 — per-instance trackers.
 *
 * Exercises [CacheTimestampTracker] + [StoreEventValidator] / [StoreHolidayValidator]
 * with an injected monotonic clock so TTL expiry is deterministic.
 */
class CacheTimestampTrackerTest {

    private var now: Long = 0L
    private lateinit var tracker: CacheTimestampTracker
    private lateinit var eventValidator: StoreEventValidator
    private lateinit var holidayValidator: StoreHolidayValidator

    private fun freshGraph() {
        tracker = CacheTimestampTracker()
        eventValidator = StoreEventValidator(tracker)
        holidayValidator = StoreHolidayValidator(tracker)
    }

    @AfterTest
    fun resetGlobalState() {
        if (::tracker.isInitialized) {
            tracker.clearAll()
            tracker.resetClockForTest()
        }
    }

    private fun installFakeClock() {
        tracker.setClockForTest { now }
    }

    @Test
    fun `isExpired returns true when key has never been fetched`() {
        freshGraph()
        installFakeClock()
        assertTrue(
            tracker.isExpired("never-fetched", maxAgeMillis = 1000L),
            "Unknown key should be treated as expired so first read forces a fetch",
        )
    }

    @Test
    fun `isExpired returns false within TTL window`() {
        freshGraph()
        installFakeClock()
        now = 0L
        tracker.recordFetch("k")

        now = 500L
        assertFalse(
            tracker.isExpired("k", maxAgeMillis = 1000L),
            "Within TTL should report fresh",
        )
    }

    @Test
    fun `isExpired returns true once clock advances past TTL`() {
        freshGraph()
        installFakeClock()
        now = 0L
        tracker.recordFetch("k")

        now = 1001L
        assertTrue(
            tracker.isExpired("k", maxAgeMillis = 1000L),
            "Past TTL should report stale",
        )
    }

    @Test
    fun `recordFetch resets staleness for that key`() {
        freshGraph()
        installFakeClock()
        now = 0L
        tracker.recordFetch("k")
        now = 5000L
        assertTrue(tracker.isExpired("k", maxAgeMillis = 1000L))

        tracker.recordFetch("k") // refresh at t=5000
        now = 5500L
        assertFalse(
            tracker.isExpired("k", maxAgeMillis = 1000L),
            "Re-recording fetch should restore freshness",
        )
    }

    @Test
    fun `clearByPrefix removes only matching keys`() {
        freshGraph()
        installFakeClock()
        tracker.recordFetch("event:userA:1:2")
        tracker.recordFetch("event:userA:3:4")
        tracker.recordFetch("event:userB:1:2")
        tracker.recordFetch("holiday:IN:2026")

        tracker.clearByPrefix("event:userA:")

        val snap = tracker.snapshot()
        assertEquals(2, snap.size, "Only userA event keys should have been dropped, got: $snap")
        assertTrue(snap.containsKey("event:userB:1:2"))
        assertTrue(snap.containsKey("holiday:IN:2026"))
    }

    @Test
    fun `separate tracker instances do not share TTL state`() {
        val a = CacheTimestampTracker()
        val b = CacheTimestampTracker()
        a.recordFetch("k")
        assertTrue(b.isExpired("k", maxAgeMillis = 1000L))
    }

    @Test
    fun `StoreEventValidator isStale honors injected clock through CacheTimestampTracker`() {
        freshGraph()
        installFakeClock()
        val key = EventKey(userId = "u1", startTime = 1_000L, endTime = 2_000L)

        assertTrue(eventValidator.isStale(key), "Brand new key should be stale")

        eventValidator.recordFetch(key)
        assertFalse(eventValidator.isStale(key), "Just-fetched key should be fresh")

        // Advance past the events TTL (1h).
        now = CacheDuration.hoursToMillis(CacheDuration.EVENTS_CACHE_HOURS) + 1
        assertTrue(eventValidator.isStale(key), "Key should be stale after EVENTS TTL")
    }

    @Test
    fun `StoreHolidayValidator isStale honors 24h TTL`() {
        freshGraph()
        installFakeClock()
        val key = HolidayKey(countryCode = "IN", year = 2026)

        assertTrue(holidayValidator.isStale(key))

        holidayValidator.recordFetch(key)
        // 1h after fetch — should still be fresh (24h TTL).
        now = CacheDuration.hoursToMillis(1)
        assertFalse(holidayValidator.isStale(key))

        // Past 24h → stale.
        now = CacheDuration.hoursToMillis(CacheDuration.HOLIDAYS_CACHE_HOURS) + 1
        assertTrue(holidayValidator.isStale(key))
    }

    @Test
    fun `StoreEventValidator invalidate forces next read to fetch`() {
        freshGraph()
        installFakeClock()
        val key = EventKey(userId = "u1", startTime = 1L, endTime = 2L)
        eventValidator.recordFetch(key)
        assertFalse(eventValidator.isStale(key))

        eventValidator.invalidate(key)
        assertTrue(
            eventValidator.isStale(key),
            "invalidate() should drop the timestamp so the next read fetches",
        )
    }

    @Test
    fun `StoreEventValidator invalidateForUser drops all keys for that user only`() {
        freshGraph()
        installFakeClock()
        val a1 = EventKey(userId = "userA", startTime = 1L, endTime = 2L)
        val a2 = EventKey(userId = "userA", startTime = 3L, endTime = 4L)
        val b1 = EventKey(userId = "userB", startTime = 1L, endTime = 2L)

        listOf(a1, a2, b1).forEach { eventValidator.recordFetch(it) }
        assertFalse(eventValidator.isStale(a1))
        assertFalse(eventValidator.isStale(a2))
        assertFalse(eventValidator.isStale(b1))

        eventValidator.invalidateForUser("userA")

        assertTrue(eventValidator.isStale(a1), "userA range 1 should be evicted")
        assertTrue(eventValidator.isStale(a2), "userA range 2 should be evicted")
        assertFalse(eventValidator.isStale(b1), "userB should not have been touched")
    }
}
