package com.debanshu.xcalendar.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Kotlin/Native has no Dispatchers.IO; Default's worker pool is the right
// fallback for blocking work on iOS. See audit F1.
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
