package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes fire-and-forget local history writes so a final session update
 * cannot overtake the initial insert when a reader is closed quickly.
 */
object LocalReadingHistoryRecorder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    fun upsert(activity: LocalReadingActivity): Job = scope.launch {
        writeMutex.withLock {
            try {
                MainActivity.database.localReadingActivityDao().upsert(activity)
            } catch (e: Exception) {
                AppLogger.e(
                    "LocalReadingHistory",
                    "Failed to persist local reading activity",
                    e
                )
            }
        }
    }
}
