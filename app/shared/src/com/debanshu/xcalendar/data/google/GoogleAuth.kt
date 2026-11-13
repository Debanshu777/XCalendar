package com.debanshu.xcalendar.data.google

import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/** Passwords and 2FA stay with Google; only refresh credentials use OS-protected storage. */
internal interface GoogleAuth {
    suspend fun accessToken(interactive: Boolean = false): String
    fun signOut()
    fun bindAccount(email: String) {}
    suspend fun saveSession(profile: GoogleProfile) {}
    suspend fun invalidateAccessToken() {}
}

internal class GoogleAuthFactory(
    val sessions: GoogleSessionStore,
    private val newAuth: (GoogleSavedAccount?) -> GoogleAuth,
) {
    fun create(saved: GoogleSavedAccount? = null): GoogleAuth = newAuth(saved)
}

@Composable
internal expect fun rememberGoogleAuthFactory(): GoogleAuthFactory

internal data class OAuthCode(val code: String, val verifier: String, val redirectUri: String)

@Serializable
private data class OAuthTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val scope: String? = null,
)

internal class BrowserGoogleAuth(
    private val client: HttpClient,
    private val clientId: String,
    saved: GoogleSavedAccount? = null,
    private val sessions: GoogleSessionStore? = null,
    private val authorize: suspend (String) -> OAuthCode,
) : GoogleAuth {
    private var token: String? = null
    private var refreshToken: String? = saved?.takeIf { it.clientId == clientId }?.refreshToken
    private var profile: GoogleProfile? = saved?.profile
    private var expiresAt = 0L
    private val mutex = Mutex()

    override suspend fun accessToken(interactive: Boolean): String = mutex.withLock {
        if (clientId.isBlank()) throw GoogleCalendarException("Google sign-in is not configured for this app yet.")
        if (token != null && Clock.System.now().toEpochMilliseconds() < expiresAt) return@withLock token!!
        val refresh = refreshToken
        val params = Parameters.build {
            append("client_id", clientId)
            if (refresh != null) {
                append("grant_type", "refresh_token"); append("refresh_token", refresh)
            } else {
                if (!interactive) throw GoogleReauthRequiredException()
                val result = authorize(clientId)
                append("grant_type", "authorization_code"); append("code", result.code)
                append("code_verifier", result.verifier); append("redirect_uri", result.redirectUri)
            }
        }
        val response = client.submitForm("https://oauth2.googleapis.com/token", params)
        if (!response.status.isSuccess()) {
            val error = try { response.body<OAuthError>().error }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            if (error in setOf("invalid_grant", "interaction_required", "login_required")) {
                forgetRefreshCredential()
                throw GoogleReauthRequiredException()
            }
            if (error in setOf("invalid_client", "unauthorized_client", "invalid_scope")) {
                throw GoogleCalendarException("Google OAuth configuration was rejected. Check this app's client ID and scopes. Your saved account is retained.")
            }
            // Network errors, rate limits, and server failures must never sign out the user.
            throw GoogleCalendarException("Google sign-in is temporarily unavailable. Your saved account is retained; try again shortly.")
        }
        val tokens = response.body<OAuthTokens>()
        val granted = tokens.scope?.split(' ')
        if (granted != null && !GOOGLE_SCOPES.filter { it.startsWith("https://www.googleapis.com/auth/calendar") }.all { it in granted }) {
            forgetRefreshCredential()
            throw GoogleReauthRequiredException("Reconnect and allow both calendar list and event access to connect Google Calendar.")
        }
        if (tokens.accessToken.isBlank()) throw GoogleCalendarException("Google did not return an access token. Please try again.")
        refreshToken = tokens.refreshToken ?: refresh
        // Persist a rotated refresh token before publishing its access token. If the
        // vault is temporarily locked, the new refresh token remains in memory for retry.
        profile?.let { persist(it) }
        token = tokens.accessToken
        expiresAt = Clock.System.now().toEpochMilliseconds() + (tokens.expiresIn - 60).coerceAtLeast(0) * 1000
        tokens.accessToken
    }

    override fun signOut() { token = null; refreshToken = null; expiresAt = 0 }

    override suspend fun invalidateAccessToken() = mutex.withLock { token = null; expiresAt = 0 }

    override suspend fun saveSession(profile: GoogleProfile) = mutex.withLock {
        if (sessions != null && refreshToken.isNullOrBlank()) {
            throw GoogleReauthRequiredException("Google did not provide permission to stay signed in. Connect again and complete the consent screen.")
        }
        persist(profile)
        this.profile = profile
    }

    private suspend fun persist(profile: GoogleProfile) {
        sessions?.upsert(GoogleSavedAccount(profile, clientId, refreshToken))
    }

    private suspend fun forgetRefreshCredential() {
        signOut()
        profile?.let { persist(it) }
    }
}

@Serializable
private data class OAuthError(val error: String = "")

internal fun authorizationUrl(clientId: String, redirect: String, state: String, challenge: String): String = URLBuilder(
    "https://accounts.google.com/o/oauth2/v2/auth",
).apply {
    parameters.append("client_id", clientId)
    parameters.append("redirect_uri", redirect)
    parameters.append("response_type", "code")
    parameters.append("scope", GOOGLE_SCOPES.joinToString(" "))
    parameters.append("state", state)
    parameters.append("code_challenge", challenge)
    parameters.append("code_challenge_method", "S256")
    parameters.append("access_type", "offline")
    // Only explicit Add/Reconnect actions open this flow. Consent obtains a refresh
    // token even when this account authorized an older, memory-only app version.
    parameters.append("prompt", "consent select_account")
}.buildString()

internal fun checkedAuthorizationCode(callback: String, redirect: String, state: String): String {
    val url = Url(callback)
    val expected = Url(redirect)
    if (url.protocol != expected.protocol || url.host != expected.host || url.port != expected.port || url.encodedPath != expected.encodedPath ||
        url.parameters.getAll("state") != listOf(state)) {
        throw GoogleCalendarException("Google sign-in response could not be verified. Please try again.")
    }
    if (url.parameters["error"] != null) throw GoogleCalendarException("Google sign-in was cancelled or access was denied.")
    return url.parameters.getAll("code")?.singleOrNull()?.takeIf { it.isNotBlank() }
        ?: throw GoogleCalendarException("Google did not return an authorization code.")
}
