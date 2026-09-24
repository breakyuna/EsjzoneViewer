package com.breakyuna.esjzone.offline

import android.annotation.SuppressLint
import android.Manifest
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import com.breakyuna.esjzone.MainActivity
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.external.CloudflareChallengeRequiredException
import com.breakyuna.esjzone.novellibrary.novel.CategoryNovel
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.util.AppLogger
import java.security.MessageDigest
import java.util.UUID
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class BackgroundDownloadStatus(
    val id: String,
    val running: Boolean,
    val finished: Boolean,
    val succeeded: Boolean,
    val progress: DownloadProgress?,
    val cancelled: Boolean = false
)

/** Compresses large tables of contents to fit WorkManager's 10 KiB input limit. */
internal object ChapterSelectionCodec {
    fun encode(urls: Set<String>): String {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(urls.sorted().joinToString("\u0000").toByteArray(Charsets.UTF_8)) }
        return Base64.getEncoder().encodeToString(output.toByteArray()).also {
            require(it.length <= 8_000) { "Too many chapters selected for one download task" }
        }
    }

    fun decode(value: String): Set<String> = GZIPInputStream(
        ByteArrayInputStream(Base64.getDecoder().decode(value))
    ).use { it.readBytes().toString(Charsets.UTF_8).split('\u0000').filter(String::isNotBlank).toSet() }
}

/** Schedules resumable novel downloads independently from any Compose page. */
object NovelDownloadManager {

    fun enqueue(
        context: Context,
        authorization: Authorization,
        novel: DetailedNovel,
        selectedChapterUrls: Set<String>? = null
    ): UUID {
        require(selectedChapterUrls == null || selectedChapterUrls.isNotEmpty())
        // A session's domain is authoritative for queued work.  The DataStore
        // value is only the fallback for sessions that predate domain capture.
        val domain = authorization.domain.trim().ifBlank {
            runCatching {
                EsjzoneApplication.instance.container.settings.domain.value
            }.getOrDefault(SettingsDefaults.DOMAINS.first())
        }
        val concurrency = runCatching {
            EsjzoneApplication.instance.container.settings.downloadConcurrency.value
        }.getOrDefault(NovelDownloadStore.DEFAULT_DOWNLOAD_CONCURRENCY)
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
                    NovelDownloadWorker.KEY_DOMAIN to domain,
                    NovelDownloadWorker.KEY_CONCURRENCY to concurrency,
                    NovelDownloadWorker.KEY_SELECTED_CHAPTERS to selectedChapterUrls?.let(ChapterSelectionCodec::encode).orEmpty()
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

    fun cancel(context: Context, novelUrl: String) {
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(uniqueWorkName(novelUrl))
        }
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
            },
            cancelled = info.state == WorkInfo.State.CANCELLED
        )
    }

    fun statusFlow(context: Context, novelUrl: String): Flow<BackgroundDownloadStatus?> {
        return WorkManager.getInstance(context.applicationContext)
            .getWorkInfosForUniqueWorkFlow(uniqueWorkName(novelUrl))
            .map { infos ->
                val info = infos.lastOrNull { !it.state.isFinished }
                    ?: infos.lastOrNull()
                    ?: return@map null
                val total = info.progress.getInt(NovelDownloadWorker.KEY_TOTAL, 0)
                val completed = info.progress.getInt(NovelDownloadWorker.KEY_COMPLETED, 0)
                val chapterName = info.progress.getString(NovelDownloadWorker.KEY_CHAPTER).orEmpty()
                BackgroundDownloadStatus(
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
                    },
                    cancelled = info.state == WorkInfo.State.CANCELLED
                )
            }
    }

    /**
     * Stable identity for WorkManager's KEEP policy.  Kept pure so the
     * manifest/work contract can be regression-tested without Android's
     * WorkManager runtime.
     */
    internal fun uniqueWorkName(novelUrl: String): String {
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

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val name = inputData.getString(KEY_NAME)?.trim().orEmpty()
        val rawUrl = inputData.getString(KEY_URL)?.trim().orEmpty()
        val forumUrl = inputData.getString(KEY_FORUM_URL)?.trim().orEmpty()
        val domain = inputData.getString(KEY_DOMAIN)?.trim().orEmpty()
        val selectedChapterUrls = inputData.getString(KEY_SELECTED_CHAPTERS)?.takeIf { it.isNotEmpty() }
            ?.let(ChapterSelectionCodec::decode)
        val concurrency = inputData.getInt(
            KEY_CONCURRENCY,
            NovelDownloadStore.DEFAULT_DOWNLOAD_CONCURRENCY
        ).coerceIn(SettingsDefaults.MIN_DOWNLOAD_CONCURRENCY, SettingsDefaults.MAX_DOWNLOAD_CONCURRENCY)
        if (name.isBlank() || rawUrl.isBlank()) return Result.failure()

        return try {
            AppLogger.init(applicationContext)
            try {
                setForeground(createForegroundInfo(name, null))
            } catch (error: IllegalStateException) {
                if (error::class.java.name != "android.app.ForegroundServiceStartNotAllowedException") {
                    throw error
                }
                // Android 14+ can deny promotion after WorkManager starts us from
                // the background. The work itself is still safe to continue.
                AppLogger.w("NovelDownloadWorker", "Foreground promotion denied; continuing in background", error)
            }
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
            val manifest = NovelDownloadStore.download(authorization, detail, actualBaseUrl, concurrency,
                selectedChapterUrls) { next ->
                setProgressAsync(next.toWorkData())
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    applicationContext.getSystemService(NotificationManager::class.java)
                        .notify(notificationId(), createNotification(name, next))
                }
            }
            val selectedKeys = selectedChapterUrls?.map(NovelDownloadStore::chapterKey)?.toSet()
            val pendingPasswordCount = manifest.pendingPasswordChapters.count { record ->
                selectedKeys == null || NovelDownloadStore.chapterKey(record.url) in selectedKeys
            }
            if (pendingPasswordCount > 0) {
                sendPasswordRequiredNotification(name, rawUrl, pendingPasswordCount)
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e("NovelDownloadWorker", "Background novel download failed", error)
            if (generateSequence<Throwable>(error) { it.cause }.any {
                    it is CloudflareChallengeRequiredException
                }) {
                sendWenkuVerificationNotification(name, rawUrl)
                return Result.failure()
            }
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendWenkuVerificationNotification(novelName: String, novelUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        ensureNotificationChannel()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NOVEL_URL, novelUrl)
        }
        val pending = PendingIntent.getActivity(applicationContext, notificationId() + 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(applicationContext.getString(R.string.wenku_download_verification_title))
            .setContentText(novelName)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build().let { applicationContext.getSystemService(NotificationManager::class.java)
                .notify(notificationId() + 2, it) }
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

    private fun sendPasswordRequiredNotification(
        novelName: String,
        novelUrl: String,
        passwordCount: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        ensureNotificationChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NOVEL_URL, novelUrl)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(
                applicationContext.getString(
                    R.string.novel_download_password_required_title,
                    novelName,
                    passwordCount
                )
            )
            .setContentText(
                applicationContext.getString(R.string.novel_download_password_required_desc)
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(notificationId() + 1, notification)
    }

    companion object {
        internal const val KEY_NAME = "novel_name"
        internal const val KEY_URL = "novel_url"
        internal const val KEY_FORUM_URL = "forum_url"
        internal const val KEY_DOMAIN = "domain"
        internal const val KEY_CONCURRENCY = "download_concurrency"
        internal const val KEY_SELECTED_CHAPTERS = "selected_chapters"
        internal const val KEY_COMPLETED = "completed"
        internal const val KEY_TOTAL = "total"
        internal const val KEY_CHAPTER = "chapter"
        private const val MAX_RETRIES = 3
        private const val CHANNEL_ID = "novel_downloads"
    }
}
