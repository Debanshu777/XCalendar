package com.debanshu.xcalendar.data.google

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Implementations must use OS-protected storage and fail rather than fall back to plaintext. */
internal interface GoogleSecureStorage {
    suspend fun read(): String?
    suspend fun write(value: String)
}

@Serializable
internal data class GoogleSavedAccount(
    val profile: GoogleProfile,
    val clientId: String,
    val refreshToken: String? = null,
) {
    override fun toString() = "GoogleSavedAccount(credentials=<redacted>)"
}

@Serializable
private data class GoogleSessionArchive(val version: Int = 1, val accounts: List<GoogleSavedAccount> = emptyList())

/** Serializes read/modify/write so independent accounts cannot overwrite one another's tokens. */
internal class GoogleSessionStore(private val secure: GoogleSecureStorage) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun accounts(): List<GoogleSavedAccount> = mutex.withLock { readAccounts() }

    suspend fun upsert(account: GoogleSavedAccount) = mutex.withLock {
        require(account.profile.sub.isNotBlank())
        val existing = readAccounts()
        val updated = if (existing.any { it.profile.sub == account.profile.sub }) {
            existing.map { if (it.profile.sub == account.profile.sub) account else it }
        } else existing + account
        secure.write(json.encodeToString(GoogleSessionArchive(accounts = updated)))
    }

    suspend fun remove(accountId: String) = mutex.withLock {
        val updated = readAccounts().filterNot { it.profile.sub == accountId }
        secure.write(json.encodeToString(GoogleSessionArchive(accounts = updated)))
    }

    private suspend fun readAccounts(): List<GoogleSavedAccount> {
        val value = secure.read() ?: return emptyList()
        val archive = try { json.decodeFromString<GoogleSessionArchive>(value) }
        catch (_: IllegalArgumentException) {
            throw GoogleCalendarException("Saved Google sign-in data could not be read. Your accounts have not been removed.")
        }
        if (archive.version != 1 || archive.accounts.any { it.profile.sub.isBlank() } ||
            archive.accounts.distinctBy { it.profile.sub }.size != archive.accounts.size) {
            throw GoogleCalendarException("Saved Google sign-in data is not supported by this app version.")
        }
        return archive.accounts
    }
}
