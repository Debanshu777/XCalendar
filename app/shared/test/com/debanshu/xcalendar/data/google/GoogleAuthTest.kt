package com.debanshu.xcalendar.data.google

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class GoogleAuthTest {
    @Test
    fun factoryCreatesIndependentBrowserSessions() = runTest {
        var exchanges = 0
        val client = HttpClient(MockEngine {
            exchanges++
            respond(
                """{"access_token":"token-$exchanges","expires_in":3600,"scope":"${GOOGLE_SCOPES.joinToString(" ")}"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            val factory = GoogleAuthFactory(GoogleSessionStore(InMemoryGoogleSecureStorage())) { _ ->
                BrowserGoogleAuth(client, "client-id") { OAuthCode("code", "verifier", "http://127.0.0.1:1234/callback") }
            }
            val first = factory.create()
            val second = factory.create()

            assertNotSame(first, second)
            assertEquals("token-1", first.accessToken(interactive = true))
            assertEquals("token-1", first.accessToken())
            assertEquals("token-2", second.accessToken(interactive = true))
            assertEquals(2, exchanges)
        } finally { client.close() }
    }

    @Test
    fun expiredTokensRefreshWithoutOpeningBrowserAndSignOutForgetsThem() = runTest {
        var browserCalls = 0
        var requests = 0
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClient(MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            requests++
            if (requests == 1) {
                assertContains(body, "code_verifier=verifier")
                respond("""{"access_token":"first","refresh_token":"refresh","expires_in":0,"scope":"${GOOGLE_SCOPES.joinToString(" ")}"}""", headers = headers)
            } else {
                assertContains(body, "grant_type=refresh_token")
                assertContains(body, "refresh_token=refresh")
                respond("""{"access_token":"second","expires_in":3600}""", headers = headers)
            }
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            val auth = BrowserGoogleAuth(client, "client-id") {
                browserCalls++
                OAuthCode("code", "verifier", "http://127.0.0.1:1234/callback")
            }
            assertEquals("first", auth.accessToken(true))
            assertEquals("second", auth.accessToken())
            assertEquals("second", auth.accessToken())
            assertEquals(1, browserCalls)
            assertEquals(2, requests)
            auth.signOut()
            assertFailsWith<GoogleCalendarException> { auth.accessToken() }
        } finally { client.close() }
    }

    @Test
    fun partialCalendarConsentDoesNotCreateAUsableSession() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"access_token":"partial","scope":"openid email profile"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }
        try {
            val auth = BrowserGoogleAuth(client, "client-id") { OAuthCode("code", "verifier", "http://127.0.0.1:1234/callback") }
            assertFailsWith<GoogleCalendarException> { auth.accessToken(true) }
            assertFailsWith<GoogleCalendarException> { auth.accessToken() }
        } finally { client.close() }
    }

    @Test
    fun restoredSessionRefreshesSilentlyAndPersistsRotatedCredential() = runTest {
        var browserCalls = 0
        var requests = 0
        val profile = GoogleProfile("account-a", "a@example.com", "A")
        val storage = InMemoryGoogleSecureStorage()
        val sessions = GoogleSessionStore(storage)
        sessions.upsert(GoogleSavedAccount(profile, "client-id", "old-refresh"))
        val client = HttpClient(MockEngine { request ->
            requests++
            assertContains((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(), "refresh_token=old-refresh")
            respond(
                """{"access_token":"restored-access","refresh_token":"rotated-refresh","expires_in":3600}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            val saved = sessions.accounts().single()
            val auth = BrowserGoogleAuth(client, "client-id", saved, sessions) {
                browserCalls++
                error("Restoring a session must not open the browser")
            }

            assertEquals("restored-access", auth.accessToken())
            assertEquals("rotated-refresh", sessions.accounts().single().refreshToken)
            assertEquals(0, browserCalls)
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test
    fun transientRefreshFailureRetainsSavedCredential() = runTest {
        val profile = GoogleProfile("account-a", "a@example.com", "A")
        val storage = InMemoryGoogleSecureStorage()
        val sessions = GoogleSessionStore(storage)
        sessions.upsert(GoogleSavedAccount(profile, "client-id", "refresh-a"))
        val client = HttpClient(MockEngine {
            respond("""{"error":"server_error"}""", HttpStatusCode.ServiceUnavailable,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            val auth = BrowserGoogleAuth(client, "client-id", sessions.accounts().single(), sessions) {
                error("A transient refresh failure must not open the browser")
            }
            assertFailsWith<GoogleCalendarException> { auth.accessToken() }
            assertEquals("refresh-a", sessions.accounts().single().refreshToken)
        } finally { client.close() }
    }

    @Test
    fun invalidGrantClearsOnlyThatAccountsRefreshCredential() = runTest {
        val storage = InMemoryGoogleSecureStorage()
        val sessions = GoogleSessionStore(storage)
        val profileA = GoogleProfile("account-a", "a@example.com", "A")
        val profileB = GoogleProfile("account-b", "b@example.com", "B")
        sessions.upsert(GoogleSavedAccount(profileA, "client-id", "refresh-a"))
        sessions.upsert(GoogleSavedAccount(profileB, "client-id", "refresh-b"))
        val client = HttpClient(MockEngine {
            respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            val auth = BrowserGoogleAuth(client, "client-id", sessions.accounts().first(), sessions) {
                error("An invalid saved refresh token requires explicit reconnect")
            }
            assertFailsWith<GoogleReauthRequiredException> { auth.accessToken() }
            val saved = sessions.accounts().associateBy { it.profile.sub }
            assertNull(saved.getValue("account-a").refreshToken)
            assertEquals(profileA, saved.getValue("account-a").profile)
            assertEquals("refresh-b", saved.getValue("account-b").refreshToken)
        } finally { client.close() }
    }

    @Test
    fun concurrentRestoredRequestsShareOneRefresh() = runTest {
        val profile = GoogleProfile("account-a", "a@example.com", "A")
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond("""{"access_token":"shared-access","expires_in":3600}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            val auth = BrowserGoogleAuth(client, "client-id", GoogleSavedAccount(profile, "client-id", "refresh-a")) {
                error("Restored sessions must refresh without browser authorization")
            }
            val first = async { auth.accessToken() }
            val second = async { auth.accessToken() }
            assertEquals("shared-access", first.await())
            assertEquals("shared-access", second.await())
            assertEquals(1, requests)
        } finally { client.close() }
    }

    private class InMemoryGoogleSecureStorage : GoogleSecureStorage {
        private var value: String? = null
        override suspend fun read(): String? = value
        override suspend fun write(value: String) { this.value = value }
    }
}
