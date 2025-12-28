package com.debanshu.xcalendar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.debanshu.xcalendar.data.localDataSource.AppDatabase
import com.debanshu.xcalendar.data.localDataSource.DATABASE_NAME
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

    val dbFile = File(appDataDir, DATABASE_NAME)
    return Room
        .databaseBuilder<AppDatabase>(dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        // Apply all migrations
        .addMigrations(*AppDatabase.MIGRATIONS)
        // Fallback to destructive migration if no migration path exists
        // Remove this in production if data preservation is critical
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}
