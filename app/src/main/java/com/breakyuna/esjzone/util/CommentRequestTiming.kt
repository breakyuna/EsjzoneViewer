package com.breakyuna.esjzone.util

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Log stages once on completion; keep explicit milestones for in-flight diagnostics.
 * Only local IDs, fixed operation/stage/result labels, exception types and durations are logged.
 * Never include comment content, URLs, response bodies, credentials or exception messages.
 */
internal class CommentRequestTiming(private val operation: String) {
    private val id = nextId.incrementAndGet()
    private val startedAt = System.nanoTime()

    init {
        log("flow", "started", startedAt)
    }

    fun <T> stage(name: String, block: () -> T): T {
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

    fun finish(result: String) = log("flow", result, startedAt)

    fun point(name: String, result: String = "reached") = log(name, result, System.nanoTime())

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
