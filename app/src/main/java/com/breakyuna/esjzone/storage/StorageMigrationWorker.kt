package com.breakyuna.esjzone.storage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** WorkManager owns retries and process-death recovery; no Activity coroutine owns the transfer. */
class StorageMigrationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = EsjzoneApplication.instance.container
        var lastPublishedAt = 0L
        var lastPhase: StorageTransferPhase? = null
        var foregroundAllowed = true
        try {
            container.initializeDownloads()
            try {
                setForeground(foregroundInfo(StorageTransferProgress(StorageTransferPhase.QUEUED)))
            } catch (error: IllegalStateException) {
                if (error::class.java.name != "android.app.ForegroundServiceStartNotAllowedException") throw error
                // Work remains journaled if the system denies foreground promotion.
                foregroundAllowed = false
            }
            val completed = container.storage.transferFiles { progress ->
                val now = android.os.SystemClock.elapsedRealtime()
                if (progress.phase != lastPhase || now - lastPublishedAt >= 500L) {
                    setProgressAsync(workDataOf("phase" to progress.phase.name,
                        "completed" to progress.completed, "total" to progress.total))
                    if (foregroundAllowed) setForegroundAsync(foregroundInfo(progress))
                    lastPublishedAt = now
                    lastPhase = progress.phase
                }
            }
            if (completed) Result.success() else Result.retry()
        } catch (error: CancellationException) {
            // The journal and originals survive cancellation. WorkManager resumes stopped work later.
            throw error
        } catch (error: Exception) {
            container.storage.recordTransferFailure(error)
            Result.failure()
        }
    }

    private fun foregroundInfo(progress: StorageTransferProgress): ForegroundInfo = ForegroundInfo(
        NOTIFICATION_ID, notification(progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    private fun notification(progress: StorageTransferProgress): Notification {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,
            applicationContext.getString(R.string.storage_transfer_title), NotificationManager.IMPORTANCE_LOW))
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(applicationContext, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.storage_transfer_title))
            .setContentText(progress.message(applicationContext))
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(progress.total, progress.completed, progress.total <= 0)
            .build()
    }

    companion object {
        private const val WORK_NAME = "shared-storage-migration"
        private const val CHANNEL_ID = "storage-migration"
        private const val NOTIFICATION_ID = 7201
        fun enqueue(context: Context, replace: Boolean = false) {
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<StorageMigrationWorker>()
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS).build())
        }
    }
}

internal fun StorageTransferProgress.message(context: Context): String {
    if ((phase == StorageTransferPhase.FAILED || phase == StorageTransferPhase.CLEANUP_FAILED) && failure != null) {
        val reason = if (phase == StorageTransferPhase.CLEANUP_FAILED &&
            failure.reason in setOf(StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT,
                StorageTransferFailureReason.TARGET_FILE_CONFLICT)) R.string.storage_failure_cleanup_conflict else when (failure.reason) {
            StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT -> R.string.storage_failure_manifest_conflict
            StorageTransferFailureReason.TARGET_FILE_CONFLICT -> R.string.storage_failure_file_conflict
            StorageTransferFailureReason.SOURCE_MANIFEST_INVALID -> R.string.storage_failure_source_manifest
            StorageTransferFailureReason.SOURCE_FILE_MISSING -> R.string.storage_failure_source_missing
            StorageTransferFailureReason.VERIFICATION_FAILED -> R.string.storage_failure_verification
            StorageTransferFailureReason.ACCESS -> R.string.storage_failure_access
            StorageTransferFailureReason.IO -> R.string.storage_failure_io
            StorageTransferFailureReason.UNKNOWN -> R.string.storage_failure_unknown
        }
        val status = context.getString(if (phase == StorageTransferPhase.CLEANUP_FAILED)
            R.string.storage_location_cleanup_failed else R.string.storage_transfer_failed)
        return listOf(status, context.getString(reason), failure.item.takeIf { it.isNotBlank() })
            .filterNotNull().joinToString("\n")
    }
    if (total <= 0 && phase in setOf(StorageTransferPhase.COPYING, StorageTransferPhase.VERIFYING)) {
        return context.getString(R.string.storage_transfer_queued)
    }
    return when (phase) {
        StorageTransferPhase.COPYING -> context.getString(R.string.storage_transfer_copying, book, completed, total)
        StorageTransferPhase.VERIFYING -> context.getString(R.string.storage_transfer_verifying, book, completed, total)
        StorageTransferPhase.CLEANING -> context.getString(R.string.storage_transfer_cleaning, completed, total)
        StorageTransferPhase.WAITING_FOR_READER -> context.getString(R.string.storage_transfer_waiting_reader)
        StorageTransferPhase.FAILED -> context.getString(R.string.storage_transfer_failed)
        StorageTransferPhase.CLEANUP_FAILED -> context.getString(R.string.storage_location_cleanup_failed)
        StorageTransferPhase.DONE -> context.getString(R.string.storage_location_done)
        else -> context.getString(R.string.storage_transfer_queued)
    }
}
