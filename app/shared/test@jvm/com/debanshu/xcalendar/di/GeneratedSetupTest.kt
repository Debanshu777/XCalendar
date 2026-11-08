package com.debanshu.xcalendar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.debanshu.xcalendar.data.localDataSource.AppDatabase
import com.debanshu.xcalendar.data.localDataSource.UserDao
import com.debanshu.xcalendar.data.localDataSource.model.UserEntity
import com.debanshu.xcalendar.domain.repository.ICalendarRepository
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.repository.IHolidayRepository
import com.debanshu.xcalendar.domain.repository.IUserRepository
import com.debanshu.xcalendar.domain.usecase.event.CreateEventUseCase
import com.debanshu.xcalendar.ui.viewmodel.EventViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Exercise generated Room/Koin code together without touching a user's database. */
class GeneratedSetupTest {
    @Test
    fun generatedGraphResolvesAndRoomCanReadAndWrite() = runTest {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val application = koinApplication {
            modules(generatedAppModule(), module { single<AppDatabase> { database } })
        }
        try {
            val koin = application.koin
            assertNotNull(koin.get<IUserRepository>())
            assertNotNull(koin.get<ICalendarRepository>())
            assertNotNull(koin.get<IEventRepository>())
            assertNotNull(koin.get<IHolidayRepository>())
            assertNotNull(koin.get<CreateEventUseCase>())
            assertNotNull(koin.get<EventViewModel>())
            val user = UserEntity("setup-test", "Setup Test", "setup@example.com", "")
            val dao = koin.get<UserDao>()
            dao.insertUser(user)
            assertEquals(user, dao.getUserById(user.id))
        } finally {
            application.koin.getOrNull<HttpClient>()?.close()
            application.close()
            database.close()
        }
    }
}
