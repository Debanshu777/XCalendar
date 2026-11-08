package com.debanshu.xcalendar.di

import org.koin.core.module.Module
import org.koin.ksp.generated.module

internal actual fun generatedAppModule(): Module = AppModule().module
