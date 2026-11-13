package com.debanshu.xcalendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.debanshu.xcalendar.common.model.YearMonth
import com.debanshu.xcalendar.ui.state.DateStateHolder
import java.io.File
import java.lang.management.ManagementFactory
import java.awt.Window
import java.awt.event.WindowFocusListener
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext

/** Opt-in diagnostic, copied into an isolated benchmark checkout only.
 * Frame-clock callbacks are NOT GPU completion or presentation timestamps.
 * Sampling requests frames, including the settling tail; never report this as FPS.
 */
@Composable
fun DesktopPerformanceProbe(onComplete: () -> Unit) {
    LaunchedEffect(Unit) {
        val output = File(requireNotNull(System.getProperty("xcalendar.benchmark.output")))
        output.mkdirs()
        val dates = GlobalContext.get().get<DateStateHolder>()
        val cpu = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        fun cpuNanos(): Long = cpu.processCpuTime
        // Allow compilation on other platforms to finish before measuring.
        while (!File(output, "start.signal").exists()) delay(100)
        // CUA can publish start.signal just before native focus settles. Require
        // a showing Compose window to stay active for half a second before the
        // loading fixture or benchmark environment is captured.
        val window = awaitStableActiveComposeWindow(output)
        val fixture = Class.forName("com.debanshu.xcalendar.PerformanceUiFixture")
        fixture.methods.firstOrNull { it.name == "startLoading" }?.invoke(fixture.getField("INSTANCE").get(null))
        val firstMonth = System.getProperty("xcalendar.benchmark.firstMonth", "11").toInt()
        val environment = java.util.Properties().apply {
            setProperty("java", System.getProperty("java.runtime.version"))
            setProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version"))
            setProperty("frameClock", kotlin.coroutines.coroutineContext[androidx.compose.runtime.MonotonicFrameClock]!!.javaClass.name)
            setProperty("windowSize", "${window.width}x${window.height}")
            setProperty("displayRefreshRate", window.graphicsConfiguration.device.displayMode.refreshRate.toString())
            setProperty("activeAtStart", window.isActive.toString())
            setProperty("renderApiAtStart", (window as androidx.compose.ui.awt.ComposeWindow).renderApi.name)
        }
        File(output, "environment.properties").outputStream().use { environment.store(it, "Desktop benchmark environment") }
        delay(5000)
        val rows = mutableListOf("run,transition,callback_interval_ms")
        val summaries = mutableListOf("run,process_cpu_ms,heap_used_bytes,active_at_end")
        // Round zero warms the same path; five measured rounds follow.
        for (run in 0..5) {
            val before = cpuNanos()
            repeat(6) { transition ->
                val month = if (transition % 2 == 0) firstMonth + 1 else firstMonth
                dates.updateSelectedInViewMonthState(YearMonth(2026, month))
                var previous = withFrameNanos { it }
                val start = previous
                do {
                    val now = withFrameNanos { it }
                    rows.add("$run,$transition,${(now - previous) / 1_000_000.0}")
                    previous = now
                } while (previous - start < 1_000_000_000L)
                check(dates.currentDateState.value.selectedInViewMonth == YearMonth(2026, month))
                delay(250)
            }
            summaries.add("$run,${(cpuNanos() - before) / 1_000_000.0},${ManagementFactory.getMemoryMXBean().heapMemoryUsage.used},${window.isActive}")
        }
        File(output, "frame-clock.csv").writeText(rows.joinToString("\n") + "\n")
        File(output, "process.csv").writeText(summaries.joinToString("\n") + "\n")
        environment.setProperty("renderApiAtEnd", (window as androidx.compose.ui.awt.ComposeWindow).renderApi.name)
        environment.setProperty("activeAtEnd", window.isActive.toString())
        File(output, "environment.properties").outputStream().use { environment.store(it, "Desktop benchmark environment") }
        File(output, "completed.txt").writeText("36 programmatic month requests; state assignment checked, rendered settlement not verified; round 0 warmup; frame-clock diagnostics only.\n")
        onComplete()
    }
}

private suspend fun awaitStableActiveComposeWindow(output: File): androidx.compose.ui.awt.ComposeWindow {
    val stabilityNanos = 500_000_000L
    val timeoutNanos = 60_000_000_000L
    val startedAt = System.nanoTime()
    val focusLosses = AtomicLong(0L)
    var watchedWindow: androidx.compose.ui.awt.ComposeWindow? = null
    var focusListener: WindowFocusListener? = null
    var activeSince = 0L
    var stableFocusLosses = 0L
    fun removeFocusListener() {
        val oldWindow = watchedWindow ?: return
        focusListener?.let(oldWindow::removeWindowFocusListener)
        watchedWindow = null
        focusListener = null
    }

    try {
        while (true) {
            val composeWindows = Window.getWindows().filterIsInstance<androidx.compose.ui.awt.ComposeWindow>()
            val current = composeWindows.firstOrNull { it.isShowing && it.isActive }
                ?: composeWindows.firstOrNull { it.isShowing }

            if (current !== watchedWindow) {
                removeFocusListener()
                watchedWindow = current
                focusListener = current?.let {
                    object : WindowFocusListener {
                        override fun windowGainedFocus(event: java.awt.event.WindowEvent) = Unit
                        override fun windowLostFocus(event: java.awt.event.WindowEvent) {
                            focusLosses.incrementAndGet()
                        }
                    }
                }
                current?.let { activeCandidate ->
                    focusListener?.let(activeCandidate::addWindowFocusListener)
                }
                activeSince = 0L
                stableFocusLosses = focusLosses.get()
            }

            val now = System.nanoTime()
            val observedLosses = focusLosses.get()
            if (current != null && current.isShowing && current.isActive) {
                if (activeSince == 0L || observedLosses != stableFocusLosses) {
                    activeSince = now
                    stableFocusLosses = observedLosses
                } else if (now - activeSince >= stabilityNanos && observedLosses == focusLosses.get() && current.isActive) {
                    return current
                }
            } else {
                activeSince = 0L
                stableFocusLosses = observedLosses
            }

            if (now - startedAt >= timeoutNanos) {
                val failure = "No showing ComposeWindow stayed active for 500 ms within 60 seconds after start.signal."
                File(output, "focus-gate-failed.txt").writeText(failure + "\n")
                error(failure)
            }
            delay(50)
        }
    } finally {
        removeFocusListener()
    }
}
