package com.breakyuna.esjzone.util

import com.breakyuna.esjzone.BuildConfig
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Log stages once on completion; keep explicit milestones for in-flight diagnostics.
 * Only local IDs, fixed operation/stage/result labels, exception types and durations are logged.
 * Never include comment content, URLs, response bodies, credentials or exception messages.
 */
internal class CommentRequestTiming(private val operation: String) {
    private val enabled = BuildConfig.DEBUG
    private val id = if (enabled) nextId.incrementAndGet() else 0L
    private val startedAt = if (enabled) System.nanoTime() else 0L

    init {
        if (enabled) log("flow", "started", startedAt)
    }

    fun <T> stage(name: String, block: () -> T): T {
        if (!enabled) return block()
        val start = System.nanoTime()
        var result = "completed"
        try {
            return block()
        } catch (error: Throwable) {
            result = error.javaClass.simpleName
            throw error
        } finally {
            log(name, result, start)
        }
    }

    suspend fun <T> suspendingStage(name: String, block: suspend () -> T): T {
        if (!enabled) return block()
        val start = System.nanoTime()
        var result = "completed"
        try {
            return block()
        } catch (error: Throwable) {
            result = error.javaClass.simpleName
            throw error
        } finally {
            log(name, result, start)
        }
    }

    fun finish(result: String) {
        if (enabled) log("flow", result, startedAt)
    }

    fun point(name: String, result: String = "reached") {
        if (enabled) log(name, result, System.nanoTime())
    }

    private fun log(stage: String, result: String, stageStart: Long) {
        val now = System.nanoTime()
        AppLogger.i(
            "CommentTiming",
            "trace=$id operation=$operation stage=$stage result=$result " +
                "elapsed_ms=${TimeUnit.NANOSECONDS.toMillis(now - stageStart)} " +
                "total_ms=${TimeUnit.NANOSECONDS.toMillis(now - startedAt)}"
        )
    }

    private companion object {
        val nextId = AtomicLong()
    }
}
