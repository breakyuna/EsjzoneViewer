package com.breakyuna.esjzone.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** The user's download keeps running independently from the dialog and current page. */
class AppUpdateDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var release: ReleaseUpdate? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            release?.downloader?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (release?.downloader?.state?.value is ReleaseDownloadState.Downloading) return START_NOT_STICKY
        val current = ReleaseUpdateChecker.update.value
        val asset = current?.apk
        if (current == null || asset == null || current.downloader.state.value !is ReleaseDownloadState.Downloading) {
            stopSelf()
            return START_NOT_STICKY
        }
        release = current
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,
                getString(R.string.update_notification_channel), NotificationManager.IMPORTANCE_LOW))
            manager.cancel(RESULT_NOTIFICATION_ID)
            ServiceCompat.startForeground(this, DOWNLOAD_NOTIFICATION_ID,
                notification(current, ReleaseDownloadState.Downloading(0, asset.size)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (error: RuntimeException) {
            AppLogger.e("AppUpdateDownloadService", "Failed to enter foreground for update download", error)
            current.downloader.markFailed()
            stopSelf()
            return START_NOT_STICKY
        }
        current.downloader.start(this, asset)
        scope.launch {
            current.downloader.state.collect { state ->
                when (state) {
                    is ReleaseDownloadState.Downloading -> postNotification(DOWNLOAD_NOTIFICATION_ID,
                        notification(current, state))
                    is ReleaseDownloadState.Ready, ReleaseDownloadState.Failed -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        postNotification(RESULT_NOTIFICATION_ID, notification(current, state))
                        stopSelf()
                    }
                    ReleaseDownloadState.Idle -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(release: ReleaseUpdate, state: ReleaseDownloadState): Notification {
        val downloading = state is ReleaseDownloadState.Downloading
        val open = PendingIntent.getActivity(this, DOWNLOAD_NOTIFICATION_ID,
            ReleaseUpdateChecker.downloadIntent(this, release).setAction(ACTION_OPEN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = when (state) {
            is ReleaseDownloadState.Downloading -> getString(R.string.update_download_progress, (state.progress * 100).toInt())
            is ReleaseDownloadState.Ready -> getString(R.string.update_notification_install)
            else -> getString(R.string.update_download_failed)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (downloading) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.update_notification_title, release.version))
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(downloading)
            .setAutoCancel(!downloading)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (state is ReleaseDownloadState.Downloading) {
            val cancel = PendingIntent.getService(this, DOWNLOAD_NOTIFICATION_ID,
                Intent(this, AppUpdateDownloadService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setProgress(100, (state.progress * 100).toInt(), false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.update_cancel_download), cancel)
        }
        return builder.build()
    }

    private fun postNotification(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        AppLogger.e("AppUpdateDownloadService", "Update download foreground service timed out: type=$fgsType")
        release?.downloader?.cancel()
        release?.downloader?.markFailed()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        release?.downloader?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "app_updates"
        private const val DOWNLOAD_NOTIFICATION_ID = 47_001
        private const val RESULT_NOTIFICATION_ID = 47_002
        private const val ACTION_CANCEL = "com.breakyuna.esjzone.CANCEL_APP_UPDATE"
        private const val ACTION_OPEN = "com.breakyuna.esjzone.OPEN_APP_UPDATE"
    }
}
