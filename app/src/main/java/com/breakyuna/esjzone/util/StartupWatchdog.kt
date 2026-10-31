package com.breakyuna.esjzone.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Observe the first minute without depending on the main looper or coroutine dispatchers. */
internal class StartupWatchdog(
    private val mainThread: Thread,
    private val postHeartbeat: (Runnable) -> Boolean,
    private val onStall: (String) -> Unit,
    private val timeoutMillis: Long = 5_000L,
    private val durationMillis: Long = 60_000L
) {
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        worker = thread(name = "startup-watchdog", isDaemon = true) {
            val started = System.nanoTime()
            try {
                while (running.get() && elapsedMillis(started) < durationMillis) {
                    val acknowledged = CountDownLatch(1)
                    val posted = System.nanoTime()
                    if (!postHeartbeat(Runnable { acknowledged.countDown() })) return@thread
                    if (!acknowledged.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                        onStall(startupStallReport(mainThread, Thread.getAllStackTraces(), elapsedMillis(posted)))
                        return@thread // One dump per launch, without a permanent monitoring thread.
                    }
                    Thread.sleep(1_000L)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
    }

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000L
}

/** Stack frames contain code locations, never local variables, cookie values or request bodies. */
internal fun startupStallReport(
    mainThread: Thread,
    traces: Map<Thread, Array<StackTraceElement>>,
    stalledMillis: Long
): String = buildString {
    appendLine("Main thread did not acknowledge startup heartbeat for ${stalledMillis}ms")
    val workers = traces.keys.filter { it !== mainThread }.sortedBy { it.name }.take(63)
    for (target in listOf(mainThread) + workers) {
        appendLine("Thread: ${target.name}; state=${target.state}")
        val frames = traces[target] ?: target.stackTrace
        frames.take(64).forEach { appendLine("    at $it") }
        if (frames.size > 64) appendLine("    [remaining frames omitted]")
    }
}
