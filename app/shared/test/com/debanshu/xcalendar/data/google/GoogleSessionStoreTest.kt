package com.debanshu.xcalendar.data.google

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class GoogleSessionStoreTest {
    @Test
    fun sessionsRoundTripAcrossStoreInstancesAndKeepAccountsSeparate() = runTest {
        val storage = InMemoryGoogleSecureStorage()
        val firstStore = GoogleSessionStore(storage)
        val accountA = savedAccount("account-a", "a@example.com", "client-a", "refresh-a")
        val accountB = savedAccount("account-b", "b@example.com", "client-b", "refresh-b")

        firstStore.upsert(accountA)
        firstStore.upsert(accountB)

        val restartedStore = GoogleSessionStore(storage)
        assertEquals(listOf(accountA, accountB), restartedStore.accounts())
    }

    @Test
    fun upsertReplacesOnlyMatchingAccountAndRemoveDoesNotRestoreIt() = runTest {
        val storage = InMemoryGoogleSecureStorage()
        val store = GoogleSessionStore(storage)
        val accountA = savedAccount("account-a", "a@example.com", "client-a", "refresh-a")
        val replacementA = savedAccount("account-a", "new-a@example.com", "client-a", "rotated-refresh-a")
        val accountB = savedAccount("account-b", "b@example.com", "client-b", "refresh-b")

        store.upsert(accountA)
        store.upsert(accountB)
        store.upsert(replacementA)
        assertEquals(listOf(replacementA, accountB), store.accounts())

        store.remove("account-a")
        assertEquals(listOf(accountB), GoogleSessionStore(storage).accounts())
    }

    @Test
    fun accountStringRepresentationDoesNotExposeRefreshToken() = runTest {
        val account = savedAccount("account-a", "a@example.com", "client-a", "secret-refresh-value")

        assertFalse(account.toString().contains("secret-refresh-value"))
    }

    @Test
    fun malformedVaultValueIsPreservedWhenReadOrUpdated() = runTest {
        val storage = InMemoryGoogleSecureStorage("unreadable-vault-payload")
        val store = GoogleSessionStore(storage)

        assertFailsWith<GoogleCalendarException> { store.accounts() }
        assertFailsWith<GoogleCalendarException> {
            store.upsert(savedAccount("account-a", "a@example.com", "client-a", "refresh-a"))
        }
        assertEquals("unreadable-vault-payload", storage.value)
    }

    private fun savedAccount(sub: String, email: String, clientId: String, refreshToken: String) =
        GoogleSavedAccount(
            profile = GoogleProfile(sub = sub, email = email, name = email.substringBefore('@')),
            clientId = clientId,
            refreshToken = refreshToken,
        )

    private class InMemoryGoogleSecureStorage(initialValue: String? = null) : GoogleSecureStorage {
        var value: String? = initialValue
            private set

        override suspend fun read(): String? = value

        override suspend fun write(value: String) {
            this.value = value
        }
    }
}
