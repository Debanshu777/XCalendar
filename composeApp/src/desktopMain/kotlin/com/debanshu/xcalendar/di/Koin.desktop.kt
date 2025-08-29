package com.debanshu.xcalendar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.debanshu.xcalendar.data.localDataSource.AppDatabase
import kotlinx.coroutines.Dispatchers
import java.io.File

actual fun getDatabase(): AppDatabase {
    val os = System.getProperty("os.name").lowercase()
    val userHome = System.getProperty("user.home")
    val appDataDir =
        when {
            os.contains("win") -> File(System.getenv("APPDATA"), "XCalendar")
            os.contains("mac") -> File(userHome, "Library/Application Support/XCalendar")
            else -> File(userHome, ".local/share/XCalendar")
        }

    if (!appDataDir.exists()) {
        appDataDir.mkdirs()
    }

    val dbFile = File(appDataDir, "calendar.db")
    return Room
        .databaseBuilder<AppDatabase>(dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
