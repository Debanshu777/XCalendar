package com.debanshu.xcalendar.data.google

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
internal actual fun rememberGoogleAuthFactory(): GoogleAuthFactory {
    val context = LocalContext.current
    val activity = remember(context) { context.activity() }
    val sessions = remember(context) { GoogleSessionStore(AndroidGoogleSecureStorage(context.applicationContext)) }
    val controller = remember(activity) { AndroidAuthorizationController() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) controller.cancelled()
        else try { controller.completeResolution(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(result.data)) }
        catch (_: Exception) { controller.failResolution("Google sign-in could not complete. Please try again.") }
    }
    controller.launch = { launcher.launch(IntentSenderRequest.Builder(it).build()) }
    return remember(activity, controller, sessions) {
        GoogleAuthFactory(sessions) { saved -> AndroidGoogleAuth(activity, controller, sessions, saved) }
    }
}

private tailrec fun Context.activity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> error("Google authorization requires an Activity")
}

/** Routes the single Activity Result launcher to the provider that started the resolution. */
private class AndroidAuthorizationController {
    var launch: ((PendingIntent) -> Unit)? = null
    private var activeProvider: AndroidGoogleAuth? = null
    // Keep ownership until the Activity Result arrives, even if its caller is cancelled.
    // Otherwise a late result could be delivered to a newer provider's continuation.
    private var resolutionOwner: AndroidGoogleAuth? = null

    fun begin(provider: AndroidGoogleAuth): Boolean {
        if (activeProvider != null || resolutionOwner != null) return false
        activeProvider = provider
        return true
    }

    fun isActive(provider: AndroidGoogleAuth): Boolean = activeProvider === provider

    fun launch(provider: AndroidGoogleAuth, pendingIntent: PendingIntent) {
        if (activeProvider !== provider || resolutionOwner != null) return
        val launch = launch
        if (launch == null) fail(provider, "Google sign-in could not start.")
        else try {
            resolutionOwner = provider
            launch(pendingIntent)
        } catch (_: Exception) {
            resolutionOwner = null
            fail(provider, "Google sign-in could not start.")
        }
    }

    fun completeDirect(provider: AndroidGoogleAuth, result: AuthorizationResult) {
        if (activeProvider !== provider) return
        activeProvider = null
        provider.complete(result)
    }

    fun completeResolution(result: AuthorizationResult) {
        val provider = resolutionOwner ?: return
        resolutionOwner = null
        if (activeProvider === provider) activeProvider = null
        provider.complete(result)
    }

    fun cancelled() = failResolution("Google sign-in was cancelled.")

    fun failResolution(message: String) {
        val provider = resolutionOwner ?: return
        resolutionOwner = null
        if (activeProvider === provider) activeProvider = null
        provider.fail(message)
    }

    fun fail(provider: AndroidGoogleAuth, message: String) {
        if (activeProvider !== provider) return
        activeProvider = null
        provider.fail(GoogleCalendarException(message))
    }

    fun fail(provider: AndroidGoogleAuth, exception: Exception) {
        if (activeProvider !== provider) return
        activeProvider = null
        provider.fail(exception)
    }

    fun clearAuthorization(provider: AndroidGoogleAuth) {
        if (activeProvider === provider) activeProvider = null
    }
}

private class AndroidGoogleAuth(
    private val activity: Activity,
    private val controller: AndroidAuthorizationController,
    private val sessions: GoogleSessionStore,
    saved: GoogleSavedAccount?,
) : GoogleAuth {
    private var pending: CancellableContinuation<String>? = null
    private var account: android.accounts.Account? = saved
        ?.takeIf { it.clientId == activity.packageName && it.profile.email.isNotBlank() }
        ?.let { android.accounts.Account(it.profile.email, "com.google") }
    private var lastAccessToken: String? = null
    private var pendingInteractive = false

    override suspend fun accessToken(interactive: Boolean): String {
        if (!interactive && account == null) throw GoogleReauthRequiredException()
        return suspendCancellableCoroutine { continuation ->
            check(pending == null) { "Google authorization is already running" }
            pending = continuation
            pendingInteractive = interactive
            if (!controller.begin(this)) {
                fail("Another Google sign-in is already running. Please try again.")
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                if (pending === continuation) {
                    pending = null
                    pendingInteractive = false
                    controller.clearAuthorization(this)
                }
            }

            try {
                val request = AuthorizationRequest.builder()
                    .setRequestedScopes(GOOGLE_SCOPES.map(::Scope))
                    .apply {
                        account?.let { setAccount(it) }
                        // A newly created provider must let the user choose even when Play services
                        // already has a grant for another account. The selected profile is bound below.
                        if (interactive && account == null) setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                    }
                    .build()
                Identity.getAuthorizationClient(activity).authorize(request)
                    .addOnSuccessListener { result ->
                        if (!controller.isActive(this) || pending !== continuation) return@addOnSuccessListener
                        if (result.hasResolution()) {
                            if (!interactive) controller.fail(this, GoogleReauthRequiredException("Google needs your permission again. Reconnect this account from Add Google account."))
                            else result.pendingIntent?.let { controller.launch(this, it) }
                                ?: controller.fail(this, "Google sign-in could not start.")
                        } else controller.completeDirect(this, result)
                    }
                    .addOnFailureListener {
                        if (controller.isActive(this) && pending === continuation) {
                            controller.fail(this, "Google sign-in failed. Check Google Play services and this app's OAuth configuration.")
                        }
                    }
            } catch (_: Exception) {
                controller.fail(this, "Google sign-in could not start. Please try again.")
            }
        }
    }

    fun complete(result: AuthorizationResult) {
        val continuation = pending ?: return
        val required = GOOGLE_SCOPES.filter { it.contains("/auth/calendar") }
        if (!required.all { scope -> result.grantedScopes.any { it == scope } }) {
            if (pendingInteractive) fail("Allow calendar list and event access to connect Google Calendar.")
            else fail(GoogleReauthRequiredException("Reconnect this Google account and allow calendar access to continue."))
            return
        }
        val token = result.accessToken
        if (token.isNullOrBlank()) { fail("Google did not grant Calendar access."); return }
        lastAccessToken = token
        pending = null
        pendingInteractive = false
        if (continuation.isActive) continuation.resume(token)
    }

    fun fail(message: String) {
        fail(GoogleCalendarException(message))
    }

    fun fail(exception: Exception) {
        val continuation = pending
        pending = null
        pendingInteractive = false
        if (continuation?.isActive == true) continuation.resumeWithException(exception)
    }

    override suspend fun invalidateAccessToken() {
        val token = lastAccessToken ?: return
        lastAccessToken = null
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                Identity.getAuthorizationClient(activity)
                    .clearToken(ClearTokenRequest.builder().setToken(token).build())
                    .addOnCompleteListener { task ->
                        if (!continuation.isActive) return@addOnCompleteListener
                        if (task.isSuccessful) continuation.resume(Unit)
                        else continuation.resumeWithException(task.exception ?: GoogleCalendarException("Could not refresh Google authorization."))
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw GoogleCalendarException("Could not refresh Google authorization.")
        }
    }

    override suspend fun saveSession(profile: GoogleProfile) {
        if (account == null) bindAccount(profile.email)
        sessions.upsert(GoogleSavedAccount(profile = profile, clientId = activity.packageName))
    }

    override fun bindAccount(email: String) {
        require(email.isNotBlank())
        account = android.accounts.Account(email, "com.google")
    }

    override fun signOut() { account = null; lastAccessToken = null }
}
