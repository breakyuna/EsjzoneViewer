package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.EsjzoneApplication
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

    fun upsert(activity: LocalReadingActivity, bookshelfScope: String): Job = scope.launch {
        writeMutex.withLock {
            try {
                EsjzoneApplication.instance.container.database.localReadingActivityDao().upsertLatest(activity)
                BookshelfRepository.markLatestRead(bookshelfScope, activity.chapterUrl)
            } catch (e: Exception) {
                AppLogger.e(
                    "LocalReadingHistory",
                    "Failed to persist local reading activity",
                    e
                )
            }
        }
    }

    /**
     * Promotes a restored local-history record immediately when its reader is
     * opened. This keeps the bookshelf's Room-backed recent-read order current
     * even if the reader is closed before its saved position has finished
     * restoring.
     */
    fun touch(novelId: String, novelUrl: String): Job = scope.launch {
        if (novelId.isBlank() && novelUrl.isBlank()) return@launch
        writeMutex.withLock {
            try {
                EsjzoneApplication.instance.container.database.localReadingActivityDao()
                    .touchLatestForIdentity(novelId, novelUrl, System.currentTimeMillis())
            } catch (e: Exception) {
                AppLogger.e("LocalReadingHistory", "Failed to update local reading timestamp", e)
            }
        }
    }
}
