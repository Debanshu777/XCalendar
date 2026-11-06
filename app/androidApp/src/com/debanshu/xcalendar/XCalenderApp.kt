package com.debanshu.xcalendar

import android.app.Application
import com.debanshu.xcalendar.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class XCalenderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin{
            androidContext(this@XCalenderApp)
            androidLogger()
        }
    }
}