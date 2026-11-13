package com.debanshu.xcalendar.data.google

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoogleSecureStorageIosTest {
    @Test
    fun keychainSessionRoundTripsAndIsScopedByService() = runTest {
        val storage = IosGoogleSecureStorage(IosGoogleSecureStorage.ROUNDTRIP_TEST_SERVICE)
        val freshInstance = IosGoogleSecureStorage(IosGoogleSecureStorage.ROUNDTRIP_TEST_SERVICE)
        val unrelatedService = IosGoogleSecureStorage(IosGoogleSecureStorage.ISOLATION_TEST_SERVICE)
        if (!storage.hasTestKeychainEntitlement()) {
            println("Skipped native Keychain round-trip: test runner lacks keychain access entitlement.")
            return@runTest
        }
        storage.clearTestItem()
        try {
            assertNull(storage.read())

            val firstFakeValue = "fake-test-session-alpha"
            storage.write(firstFakeValue)
            assertEquals(firstFakeValue, freshInstance.read())
            assertNull(unrelatedService.read())

            val replacementFakeValue = "fake-test-session-beta"
            freshInstance.write(replacementFakeValue)
            assertEquals(replacementFakeValue, storage.read())
        } finally {
            // This method refuses every service except the dedicated test service.
            storage.clearTestItem()
        }
    }
}
