package com.breakyuna.esjzone

import com.breakyuna.esjzone.util.StartupWatchdog
import com.breakyuna.esjzone.util.startupStallReport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class StartupWatchdogTest {
    @Test fun missedHeartbeatProducesThreadEvidenceWithoutExecutingOnMain() {
        val main = Thread.currentThread()
        val notified = CountDownLatch(1)
        val report = AtomicReference<String>()
        val reporter = AtomicReference<Thread>()
        val watchdog = StartupWatchdog(
            mainThread = main,
            postHeartbeat = { true }, // Simulate a looper that accepts work but never runs it.
            onStall = {
                reporter.set(Thread.currentThread())
                report.set(it)
                notified.countDown()
            },
            timeoutMillis = 25,
            durationMillis = 1_000
        )
        try {
            watchdog.start()
            assertTrue(notified.await(5, TimeUnit.SECONDS))
            assertNotSame(main, reporter.get())
            assertTrue(report.get().contains("Main thread did not acknowledge startup heartbeat"))
            assertTrue(report.get().contains("Thread: ${main.name}; state="))
            assertTrue(report.get().contains("    at "))
        } finally { watchdog.stop() }
    }

    @Test fun responsiveLooperDoesNotProduceAStallReport() {
        val acknowledged = CountDownLatch(1)
        val reports = AtomicInteger()
        val watchdog = StartupWatchdog(
            mainThread = Thread.currentThread(),
            postHeartbeat = {
                it.run()
                acknowledged.countDown()
                true
            },
            onStall = { reports.incrementAndGet() },
            timeoutMillis = 25,
            durationMillis = 1_000
        )
        try {
            watchdog.start()
            assertTrue(acknowledged.await(5, TimeUnit.SECONDS))
        } finally { watchdog.stop() }
        assertEquals(0, reports.get())
    }

    @Test fun threadReportPrioritizesMainAndBoundsBackgroundThreadsAndStackDepth() {
        val main = Thread("main-fixture")
        val frames = Array(100) { index -> StackTraceElement("Fixture", "step$index", "Fixture.kt", index + 1) }
        val traces = (0 until 100).associate { Thread("worker-$it") to frames } + (main to frames)
        val report = startupStallReport(main, traces, 5_100)
        assertTrue(report.startsWith("Main thread did not acknowledge startup heartbeat for 5100ms\nThread: main-fixture;"))
        assertEquals(64, report.lineSequence().count { it.startsWith("Thread:") })
        assertTrue(report.contains("step63"))
        assertFalse(report.contains("step64"))
        assertTrue(report.contains("[remaining frames omitted]"))
    }
}
