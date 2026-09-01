package com.breakyuna.esjzone.offline

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.novellibrary.novel.CategoryNovel
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.util.AppLogger
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException

data class BackgroundDownloadStatus(
    val id: String,
    val running: Boolean,
    val finished: Boolean,
    val succeeded: Boolean,
    val progress: DownloadProgress?
)

/** Schedules resumable novel downloads independently from any Compose page. */
object NovelDownloadManager {

    fun enqueue(
        context: Context,
        authorization: Authorization,
        novel: DetailedNovel
    ): UUID {
        val domain = authorization.domain.trim().ifBlank { GlobalSettings.domain.value }
        val request = OneTimeWorkRequestBuilder<NovelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    NovelDownloadWorker.KEY_NAME to novel.name,
                    NovelDownloadWorker.KEY_URL to novel.url,
                    NovelDownloadWorker.KEY_FORUM_URL to novel.forumUrl,
                    NovelDownloadWorker.KEY_DOMAIN to domain
                )
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            uniqueWorkName(novel.url),
            ExistingWorkPolicy.KEEP,
            request
        )
        return request.id
    }

    fun status(context: Context, novelUrl: String): BackgroundDownloadStatus? {
        val infos = WorkManager.getInstance(context.applicationContext)
            .getWorkInfosForUniqueWork(uniqueWorkName(novelUrl))
            .get()
        val info = infos.lastOrNull { !it.state.isFinished }
            ?: infos.lastOrNull()
            ?: return null
        val total = info.progress.getInt(NovelDownloadWorker.KEY_TOTAL, 0)
        val completed = info.progress.getInt(NovelDownloadWorker.KEY_COMPLETED, 0)
        val chapterName = info.progress.getString(NovelDownloadWorker.KEY_CHAPTER).orEmpty()
        return BackgroundDownloadStatus(
            id = info.id.toString(),
            running = info.state == WorkInfo.State.ENQUEUED ||
                info.state == WorkInfo.State.BLOCKED ||
                info.state == WorkInfo.State.RUNNING,
            finished = info.state.isFinished,
            succeeded = info.state == WorkInfo.State.SUCCEEDED,
            progress = if (total > 0) {
                DownloadProgress(completed, total, chapterName)
            } else {
                null
            }
        )
    }

    private fun uniqueWorkName(novelUrl: String): String {
        val key = EsjzoneUrls.canonicalPageKey(novelUrl).ifBlank { novelUrl.trim() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "novel-download-$digest"
    }

    private const val TAG = "novel-download"
}

class NovelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val name = inputData.getString(KEY_NAME)?.trim().orEmpty()
        val rawUrl = inputData.getString(KEY_URL)?.trim().orEmpty()
        val forumUrl = inputData.getString(KEY_FORUM_URL)?.trim().orEmpty()
        val domain = inputData.getString(KEY_DOMAIN)?.trim().orEmpty()
        if (name.isBlank() || rawUrl.isBlank()) return Result.failure()

        return try {
            AppLogger.init(applicationContext)
            setForeground(createForegroundInfo(name, null))
            val taskBaseUrl = if (domain.isBlank()) EsjzoneUrls.Base
            else EsjzoneUrls.baseForDomain(domain)
            EsjzoneClient.initialize(applicationContext)
            NovelDownloadStore.initialize(applicationContext)

            val resolvedUrl = EsjzoneUrls.resolve(rawUrl, taskBaseUrl)
            val parsedUrl = Uri.parse(resolvedUrl)
            val host = parsedUrl.host.orEmpty().ifBlank { domain }
            val actualBaseUrl = if (!parsedUrl.scheme.isNullOrBlank() && host.isNotBlank()) {
                "${parsedUrl.scheme}://$host/"
            } else taskBaseUrl
            val authorization = EsjzoneClient.restoreAuthorization(host)
                ?: Authorization("", "", host)
            val detail = EsjzoneClient.getNovelDetail(
                authorization = authorization,
                novel = CategoryNovel(name = name, url = rawUrl, forumUrl = forumUrl),
                includeComments = false,
                forceRefresh = true,
                baseUrl = actualBaseUrl
            )
            NovelDownloadStore.download(authorization, detail, actualBaseUrl) { next ->
                setProgressAsync(next.toWorkData())
                applicationContext.getSystemService(NotificationManager::class.java)
                    .notify(notificationId(), createNotification(name, next))
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e("NovelDownloadWorker", "Background novel download failed", error)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private fun DownloadProgress.toWorkData(): Data = workDataOf(
        KEY_COMPLETED to completed,
        KEY_TOTAL to total,
        KEY_CHAPTER to chapterName
    )

    private fun createForegroundInfo(
        novelName: String,
        progress: DownloadProgress?
    ): ForegroundInfo {
        ensureNotificationChannel()
        return ForegroundInfo(
            notificationId(),
            createNotification(novelName, progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun createNotification(
        novelName: String,
        progress: DownloadProgress?
    ) = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(
            applicationContext.getString(R.string.novel_download_notification_title, novelName)
        )
        .setContentText(
            progress?.let {
                if (it.chapterName.isBlank()) {
                    applicationContext.getString(
                        R.string.novel_downloading_count,
                        it.completed,
                        it.total
                    )
                } else {
                    applicationContext.getString(
                        R.string.novel_downloading,
                        it.completed,
                        it.total,
                        it.chapterName
                    )
                }
            } ?: applicationContext.getString(R.string.novel_download_notification_waiting)
        )
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setProgress(progress?.total ?: 0, progress?.completed ?: 0, progress == null)
        .build()

    private fun ensureNotificationChannel() {
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.novel_download_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
    }

    private fun notificationId(): Int =
        (id.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

    companion object {
        internal const val KEY_NAME = "novel_name"
        internal const val KEY_URL = "novel_url"
        internal const val KEY_FORUM_URL = "forum_url"
        internal const val KEY_DOMAIN = "domain"
        internal const val KEY_COMPLETED = "completed"
        internal const val KEY_TOTAL = "total"
        internal const val KEY_CHAPTER = "chapter"
        private const val MAX_RETRIES = 3
        private const val CHANNEL_ID = "novel_downloads"
    }
}
