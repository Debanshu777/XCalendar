package com.debanshu.xcalendar.di

import com.debanshu.xcalendar.data.localDataSource.AppDatabase
import com.debanshu.xcalendar.data.localDataSource.CalendarDao
import com.debanshu.xcalendar.data.localDataSource.EventDao
import com.debanshu.xcalendar.data.localDataSource.HolidayDao
import com.debanshu.xcalendar.data.localDataSource.UserDao
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.ContentType
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
class DataModule {

    @Single
    fun json() = Json { ignoreUnknownKeys = true }

    @Single
    fun httpClient(json: Json) = HttpClient {
        install(ContentNegotiation) {
            json(json, contentType = ContentType.Application.Json)
        }
    }

    @Single
    fun getUserEntityDao(appDatabase: AppDatabase): UserDao = appDatabase.getUserEntityDao()

    @Single
    fun getCalendarEntityDao(appDatabase: AppDatabase): CalendarDao = appDatabase.getCalendarEntityDao()

    @Single
    fun getEventEntityDao(appDatabase: AppDatabase): EventDao = appDatabase.getEventEntityDao()

    @Single
    fun getHolidayEntityDao(appDatabase: AppDatabase): HolidayDao = appDatabase.getHolidayEntityDao()
}

@Module
@ComponentScan("com.debanshu.xcalendar.ui")
class ViewModelModule

@Module(includes = [PlatformModule::class, DataModule::class, ViewModelModule::class])
class AppModule

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        modules(
           AppModule().module
        )
        config?.invoke(this)
    }
}