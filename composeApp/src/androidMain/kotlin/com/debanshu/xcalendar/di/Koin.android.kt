package com.debanshu.xcalendar.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.debanshu.xcalendar.data.localDataSource.AppDatabase
import kotlinx.coroutines.Dispatchers
import org.koin.mp.KoinPlatform

actual fun getDatabase(): AppDatabase {
    val context = KoinPlatform.getKoin().get<Context>()
    return Room.databaseBuilder<AppDatabase>(
        context,
        context.getDatabasePath("calendar.db").absolutePath
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}