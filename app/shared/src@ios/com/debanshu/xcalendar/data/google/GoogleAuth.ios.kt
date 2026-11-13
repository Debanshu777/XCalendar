@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.io.encoding.ExperimentalEncodingApi::class, ExperimentalUnsignedTypes::class)

package com.debanshu.xcalendar.data.google

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.debanshu.xcalendar.BuildKonfig
import io.ktor.client.HttpClient
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.compose.koinInject
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreCrypto.CC_SHA256
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64

@Composable
internal actual fun rememberGoogleAuthFactory(): GoogleAuthFactory {
    val client = koinInject<HttpClient>()
    val sessions = remember(client) { GoogleSessionStore(IosGoogleSecureStorage()) }
    return remember(client, sessions) {
        GoogleAuthFactory(sessions) { saved ->
            BrowserGoogleAuth(
                client = client,
                clientId = BuildKonfig.GOOGLE_IOS_CLIENT_ID,
                saved = saved,
                sessions = sessions,
                authorize = ::iosAuthorize,
            )
        }
    }
}

private fun randomSecret(): String {
    val bytes = ByteArray(32)
    bytes.usePinned { check(SecRandomCopyBytes(kSecRandomDefault, bytes.size.convert(), it.addressOf(0)) == 0) }
    return Base64.UrlSafe.encode(bytes).trimEnd('=')
}

private suspend fun iosAuthorize(clientId: String): OAuthCode = withTimeout(600_000) {
    withContext(Dispatchers.Main) {
        val verifier = randomSecret()
        val state = randomSecret()
        val hash = UByteArray(32)
        verifier.encodeToByteArray().usePinned { input ->
            hash.usePinned { output -> CC_SHA256(input.addressOf(0), verifier.length.convert(), output.addressOf(0)) }
        }
        val challenge = Base64.UrlSafe.encode(hash.toByteArray()).trimEnd('=')
        val scheme = clientId.split('.').reversed().joinToString(".")
        val redirect = "$scheme:/oauthredirect"
        val window = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>().flatMap { it.windows.filterIsInstance<UIWindow>() }.firstOrNull { it.isKeyWindow() }
            ?: throw GoogleCalendarException("Open XCalendar before connecting Google.")
        val presentation = object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
            override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): UIWindow = window
        }
        suspendCancellableCoroutine { continuation ->
            val session = ASWebAuthenticationSession(
                uRL = NSURL(string = authorizationUrl(clientId, redirect, state, challenge)),
                callbackURLScheme = scheme,
            ) { callback, error ->
                if (continuation.isActive) {
                    if (error != null || callback?.absoluteString == null) {
                        continuation.resumeWithException(GoogleCalendarException("Google sign-in was cancelled or could not complete."))
                    } else try {
                        continuation.resume(OAuthCode(checkedAuthorizationCode(callback.absoluteString!!, redirect, state), verifier, redirect))
                    } catch (e: Exception) { continuation.resumeWithException(e) }
                }
            }
            session.presentationContextProvider = presentation
            // Keep both the session and its weak presentation provider alive until completion.
            continuation.invokeOnCancellation { session.cancel(); presentation.hashCode() }
            if (!session.start()) continuation.resumeWithException(GoogleCalendarException("Google sign-in could not open."))
        }
    }
}
