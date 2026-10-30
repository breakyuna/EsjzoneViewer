package com.breakyuna.esjzone.network

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Reserve capacity for page navigation; speculative work runs one request at a time. */
internal class PageRequestGate(private val capacity: Int = 6) {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var active = 0
    private var backgroundActive = 0
    private var foregroundWaiting = 0

    fun acquire(background: Boolean, timeout: Long, unit: TimeUnit): Boolean {
        lock.lockInterruptibly()
        try {
            if (!background) foregroundWaiting++
            try {
                var remaining = unit.toNanos(timeout)
                while (active >= capacity || background && (backgroundActive > 0 || foregroundWaiting > 0)) {
                    if (remaining <= 0) return false
                    remaining = changed.awaitNanos(remaining)
                }
                active++
                if (background) backgroundActive++
                return true
            } finally {
                if (!background) { foregroundWaiting--; changed.signalAll() }
            }
        } finally { lock.unlock() }
    }

    fun release(background: Boolean) = lock.withLock {
        active--
        if (background) backgroundActive--
        changed.signalAll()
    }
}
