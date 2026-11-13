package com.debanshu.xcalendar.data.google

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.debanshu.xcalendar.BuildKonfig
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.compose.koinInject
import java.awt.Desktop
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Composable
internal actual fun rememberGoogleAuthFactory(): GoogleAuthFactory {
    val client = koinInject<HttpClient>()
    val sessions = remember(client) { GoogleSessionStore(DesktopGoogleSecureStorage()) }
    return remember(client, sessions) {
        GoogleAuthFactory(sessions) { saved ->
            BrowserGoogleAuth(
                client = client,
                clientId = BuildKonfig.GOOGLE_DESKTOP_CLIENT_ID,
                saved = saved,
                sessions = sessions,
                authorize = ::desktopAuthorize,
            )
        }
    }
}

private fun randomSecret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

private suspend fun desktopAuthorize(clientId: String): OAuthCode = withTimeout(600_000) {
    withContext(Dispatchers.IO) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw GoogleCalendarException("Open this app in a desktop session with a browser to sign in.")
        }
        val verifier = randomSecret()
        val state = randomSecret()
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 500
            val redirect = "http://127.0.0.1:${server.localPort}/oauth2callback"
            Desktop.getDesktop().browse(URI(authorizationUrl(clientId, redirect, state, challenge)))
            while (true) {
                currentCoroutineContext().ensureActive()
                val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
                socket.use {
                    socket.soTimeout = 1000
                    val line = try { socket.getInputStream().bufferedReader().readLine() } catch (_: SocketTimeoutException) { null }
                    val path = line?.split(' ')?.takeIf { it.size == 3 && it[0] == "GET" }?.get(1)
                    // Ignore favicon probes and unrelated local requests without ending sign-in.
                    if (path == null || !path.startsWith("/oauth2callback?")) return@use
                    val callback = "http://127.0.0.1:${server.localPort}$path"
                    val code = try { checkedAuthorizationCode(callback, redirect, state) } catch (e: GoogleCalendarException) {
                        socket.getOutputStream().write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        throw e
                    }
                    val body = "Connected. You can close this tab and return to XCalendar."
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: ${body.length}\r\n\r\n$body").toByteArray())
                    return@withContext OAuthCode(code, verifier, redirect)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
        }
    }
}
