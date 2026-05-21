package com.debanshu.xcalendar.data.localDataSource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 3 / F30 CI guard.
 *
 * If [DATABASE_VERSION] is bumped without adding a matching `Migration`
 * object to [AppDatabase.MIGRATIONS], the next app launch on an upgraded
 * device crashes (`IllegalStateException: A migration from N to N+1 was
 * required but not found`). Catching that at test time is much cheaper
 * than catching it in production.
 *
 * Invariants:
 * - `MIGRATIONS.size == DATABASE_VERSION - 1` (exactly one migration per
 *   bump from v1).
 * - Migrations form an unbroken chain from v1 → DATABASE_VERSION.
 */
class AppDatabaseMigrationContractTest {

    @Test
    fun `migrations array length matches version count`() {
        assertEquals(
            DATABASE_VERSION - 1,
            AppDatabase.MIGRATIONS.size,
            "DATABASE_VERSION=$DATABASE_VERSION but MIGRATIONS has " +
                "${AppDatabase.MIGRATIONS.size} entries. Add a Migration_<n>_<n+1> " +
                "for the latest schema bump.",
        )
    }

    @Test
    fun `migrations cover every adjacent version pair`() {
        val pairs = AppDatabase.MIGRATIONS
            .map { it.startVersion to it.endVersion }
            .toSet()
        for (v in 1 until DATABASE_VERSION) {
            assertTrue(
                (v to v + 1) in pairs,
                "Missing MIGRATION_${v}_${v + 1} in AppDatabase.MIGRATIONS",
            )
        }
    }
}
