package com.debanshu.xcalendar.common

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform-provided dispatcher for blocking I/O (DB, network, disk).
 *
 * - androidMain / desktopMain (JVM): `Dispatchers.IO` — backed by an
 *   unbounded thread pool, keeps blocking work off the compute pool.
 * - iosMain / nativeMain: falls back to `Dispatchers.Default` — Kotlin/Native
 *   does not provide `Dispatchers.IO`; blocking work belongs on the worker
 *   pool. See KT-46689 and Kotlin docs on multiplatform dispatchers.
 *
 * Use this anywhere a suspend operation does blocking I/O. Do NOT default
 * to `Dispatchers.Default` for I/O — it starves Compose layout/measure.
 */
expect val ioDispatcher: CoroutineDispatcher
