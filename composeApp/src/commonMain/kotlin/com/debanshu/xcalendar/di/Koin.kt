package com.debanshu.xcalendar.di

import com.debanshu.xcalendar.data.localDataSource.AppDatabase
import com.debanshu.xcalendar.data.localDataSource.CalendarDao
import com.debanshu.xcalendar.data.localDataSource.EventDao
import com.debanshu.xcalendar.data.localDataSource.HolidayDao
import com.debanshu.xcalendar.data.localDataSource.PendingWriteDao
import com.debanshu.xcalendar.data.localDataSource.SyncFailureDao
import com.debanshu.xcalendar.data.localDataSource.UserDao
import com.debanshu.xcalendar.data.outbox.NoopOutboxSender
import com.debanshu.xcalendar.data.outbox.OutboxDrainer
import com.debanshu.xcalendar.data.outbox.OutboxSender
import com.debanshu.xcalendar.data.outbox.OutboxWriter
import com.debanshu.xcalendar.data.remoteDataSource.HolidayApiService
import com.debanshu.xcalendar.data.remoteDataSource.RemoteCalendarApiService
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.ksp.generated.module

@Module
class PlatformModule {
    @Single
    fun getLocalDatabase() = getDatabase()
}

expect fun getDatabase(): AppDatabase

@Module
@ComponentScan("com.debanshu.xcalendar.data")
class DataModule {

    @Single
    fun json() = Json {
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Single
    fun httpClient(json: Json) = HttpClient {
        install(ContentNegotiation) {
            json(json, contentType = ContentType.Application.Json)
        }
        
        // Configure timeouts
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L  // 30 seconds
            connectTimeoutMillis = 15_000L  // 15 seconds
            socketTimeoutMillis = 30_000L   // 30 seconds
        }
        
        // Configure retry logic for transient failures.
        //
        // - 5xx server errors retried by `retryOnServerErrors` (exponential).
        // - 408 Request Timeout retried explicitly via `retryIf` — Ktor's
        //   `HttpRequestRetry` does not match it under `retryOnServerErrors`
        //   (404 / 408 are 4xx). See audit F26.
        // - 429 Too Many Requests retried (server-signalled backpressure).
        // - Client-side timeouts (`HttpRequestTimeoutException`,
        //   `ConnectTimeoutException`, `SocketTimeoutException`) retried via
        //   `retryOnExceptionIf` so flaky networks don't immediately fail.
        install(HttpRequestRetry) {
            maxRetries = 3
            retryIf { _, response ->
                val code = response.status.value
                !response.status.isSuccess() && (
                    code in 500..599 ||
                        code == HttpStatusCode.RequestTimeout.value ||
                        code == HttpStatusCode.TooManyRequests.value
                )
            }
            retryOnExceptionIf { _, cause ->
                cause is HttpRequestTimeoutException ||
                    cause is ConnectTimeoutException ||
                    cause is SocketTimeoutException
            }
            exponentialDelay()
        }
    }

    @Single
    fun getUserEntityDao(appDatabase: AppDatabase): UserDao = appDatabase.getUserEntityDao()

    @Single
    fun getCalendarEntityDao(appDatabase: AppDatabase): CalendarDao =
        appDatabase.getCalendarEntityDao()

    @Single
    fun getEventEntityDao(appDatabase: AppDatabase): EventDao = appDatabase.getEventEntityDao()

    @Single
    fun getHolidayEntityDao(appDatabase: AppDatabase): HolidayDao =
        appDatabase.getHolidayEntityDao()

    @Single
    fun getSyncFailureDao(appDatabase: AppDatabase): SyncFailureDao =
        appDatabase.getSyncFailureDao()

    @Single
    fun getPendingWriteDao(appDatabase: AppDatabase): PendingWriteDao =
        appDatabase.getPendingWriteDao()

    @Single
    fun provideOutboxWriter(pendingWriteDao: PendingWriteDao, json: Json): OutboxWriter =
        OutboxWriter(pendingWriteDao, json)

    /**
     * Default sender stub — replace with a real backend-aware implementation
     * once the remote write API is live (audit F29).
     */
    @Single
    fun provideOutboxSender(): OutboxSender = NoopOutboxSender()

    @Single
    fun provideOutboxDrainer(
        pendingWriteDao: PendingWriteDao,
        sender: OutboxSender,
    ): OutboxDrainer = OutboxDrainer(pendingWriteDao, sender)

    @Single
    fun holidayCountryResolver(): HolidayCountryResolver = HolidayCountryResolver()
}

@Module
@ComponentScan("com.debanshu.xcalendar.ui")
class ViewModelModule

@Module
@ComponentScan("com.debanshu.xcalendar.domain.repository")
class DomainModule

@Module
@ComponentScan("com.debanshu.xcalendar.domain.usecase")
class UseCaseModule

@Module
@ComponentScan("com.debanshu.xcalendar.domain.states")
class StateModule

@Module(
    includes = [PlatformModule::class, DataModule::class, ViewModelModule::class,
        DomainModule::class, UseCaseModule::class, StateModule::class]
)
class AppModule

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        modules(
            AppModule().module,
            userSessionStoreModule,
        )
        config?.invoke(this)
    }
}