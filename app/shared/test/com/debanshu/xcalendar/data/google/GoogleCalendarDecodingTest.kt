package com.debanshu.xcalendar.data.google

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class GoogleCalendarDecodingTest {
    @Test
    fun decodesEveryPageOffCallerDispatcherWhileKeepingAuthOnCaller() = runTest {
        val caller = currentCoroutineContext()[ContinuationInterceptor]
        val delegate = KotlinxSerializationConverter(Json { ignoreUnknownKeys = true })
        var decodedPages = 0
        var tokenCalls = 0
        val converter = object : ContentConverter by delegate {
            override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
                assertNotSame(caller, currentCoroutineContext()[ContinuationInterceptor])
                decodedPages++
                return delegate.deserialize(charset, typeInfo, content)
            }
        }
        val client = HttpClient(MockEngine { request ->
            respond(
                if (request.url.parameters["pageToken"] == null)
                    """{"items":[{"id":"first"}],"nextPageToken":"next"}"""
                else """{"items":[{"id":"second"}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { register(ContentType.Application.Json, converter) }
        }
        try {
            val events = GoogleCalendarApi(client).events({
                assertEquals(caller, currentCoroutineContext()[ContinuationInterceptor])
                tokenCalls++
                "token"
            }, "calendar", 0, 1000)
            assertEquals(listOf("first", "second"), events.map { it.id })
            assertEquals(2, tokenCalls)
            assertEquals(2, decodedPages)
            assertEquals(caller, currentCoroutineContext()[ContinuationInterceptor])
        } finally {
            client.close()
        }
    }
}
