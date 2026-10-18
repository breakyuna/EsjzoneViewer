package com.breakyuna.esjzone.update

import android.content.Context
import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.util.AppLogger
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

// A cancelled transfer may still be unwinding its IO when another download starts.
private val updateDownloadMutex = Mutex()

/** Called on IO at process startup; shares the download lock so a new APK cannot be deleted mid-transfer. */
internal suspend fun deleteInstalledUpdateApk(context: Context, installedVersionCode: Long) =
    updateDownloadMutex.withLock {
        val file = File(context.cacheDir, "app_updates/update.apk")
        if (!file.isFile) return@withLock
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return@withLock
        if (info.packageName != context.packageName || info.longVersionCode > installedVersionCode) return@withLock
        check(file.delete()) { "Cannot delete installed update APK" }
        AppLogger.i("ReleaseApkDownloader",
            "Deleted installed update APK: installed=$installedVersionCode, cached=${info.longVersionCode}")
    }

internal sealed interface ReleaseDownloadState {
    data object Idle : ReleaseDownloadState
    data class Downloading(val downloaded: Long, val total: Long) : ReleaseDownloadState {
        val progress: Float get() = (downloaded.toDouble() / total).toFloat().coerceIn(0f, 1f)
    }
    data class Ready(val file: File) : ReleaseDownloadState
    data object Failed : ReleaseDownloadState
}

/** Retained independently from dialog visibility; the foreground service owns cancellation. */
internal class ReleaseApkDownloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<ReleaseDownloadState>(ReleaseDownloadState.Idle)
    val state = _state.asStateFlow()
    private var job: Job? = null
    @Volatile private var activeCall: Call? = null

    fun markStarting(asset: ReleaseApkAsset) {
        _state.value = ReleaseDownloadState.Downloading(0, asset.size)
    }

    fun start(context: Context, asset: ReleaseApkAsset) {
        if (job != null) return
        val appContext = context.applicationContext
        _state.value = ReleaseDownloadState.Downloading(0, asset.size)
        job = scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    updateDownloadMutex.withLock {
                        val directory = File(appContext.cacheDir, "app_updates")
                        check(directory.isDirectory || directory.mkdirs())
                        directory.listFiles()?.forEach { check(it.delete()) }
                        // This client has no ESJ cookies, authentication or logging interceptors.
                        val client = OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .callTimeout(15, TimeUnit.MINUTES)
                            .retryOnConnectionFailure(false)
                            .followSslRedirects(false)
                            .build()
                        val call = client.newCall(Request.Builder().url(asset.url)
                            .header("User-Agent", "EsjzoneViewer/${BuildConfig.VERSION_NAME}").build())
                        activeCall = call
                        val downloadContext = currentCoroutineContext()
                        downloadContext.ensureActive()
                        call.execute().use { response ->
                            if (!response.isSuccessful) throw IOException("APK download failed: HTTP ${response.code}")
                            var lastPercent = -1
                            val file = response.body.byteStream().use { input ->
                                saveReleaseApk(input, directory, asset) { downloaded ->
                                    downloadContext.ensureActive()
                                    val percent = (downloaded.toDouble() / asset.size * 100).toInt()
                                    if (percent != lastPercent) {
                                        _state.value = ReleaseDownloadState.Downloading(downloaded, asset.size)
                                        lastPercent = percent
                                    }
                                }
                            }
                            try {
                                val info = requireNotNull(appContext.packageManager.getPackageArchiveInfo(file.absolutePath, 0)) {
                                    "Cannot parse update APK"
                                }
                                require(info.packageName == appContext.packageName) {
                                    "Update APK package mismatch: expected=${appContext.packageName}, actual=${info.packageName}"
                                }
                                require(info.longVersionCode > BuildConfig.VERSION_CODE) {
                                    "Update APK versionCode must be newer: installed=${BuildConfig.VERSION_CODE}, downloaded=${info.longVersionCode}"
                                }
                                downloadContext.ensureActive()
                                file
                            } catch (error: Exception) {
                                file.delete()
                                throw error
                            }
                        }
                    }
                }
                _state.value = ReleaseDownloadState.Ready(apk)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("ReleaseApkDownloader", "Update APK download or validation failed", error)
                _state.value = ReleaseDownloadState.Failed
            } finally {
                activeCall = null
                job = null
                if (_state.value is ReleaseDownloadState.Downloading) _state.value = ReleaseDownloadState.Idle
            }
        }
    }

    fun cancel() {
        activeCall?.cancel()
        job?.cancel()
        if (job == null && _state.value is ReleaseDownloadState.Downloading) _state.value = ReleaseDownloadState.Idle
    }

    fun discardMissingFile() {
        AppLogger.e("ReleaseApkDownloader", "Downloaded update APK is missing before installation")
        markFailed()
    }

    fun markFailed() {
        _state.value = ReleaseDownloadState.Failed
    }

    fun restoreReady(file: File) {
        _state.value = ReleaseDownloadState.Ready(file)
    }
}

/** Only commit a complete download; an interrupted or mismatched APK is never installable. */
internal fun saveReleaseApk(
    input: InputStream,
    directory: File,
    asset: ReleaseApkAsset,
    onProgress: (Long) -> Unit = {}
): File {
    val partial = File(directory, "update.apk.part")
    val target = File(directory, "update.apk")
    val digest = MessageDigest.getInstance("SHA-256")
    try {
        var downloaded = 0L
        partial.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                downloaded += count
                if (downloaded > asset.size) throw IOException("APK size mismatch: expected=${asset.size}, received=$downloaded")
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                onProgress(downloaded)
            }
        }
        if (downloaded != asset.size) throw IOException("Incomplete APK download: expected=${asset.size}, received=$downloaded")
        val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
        if (asset.digest != null && !asset.digest.substringAfter(':').equals(actualDigest, ignoreCase = true)) {
            throw IOException("APK checksum mismatch")
        }
        if (!partial.renameTo(target)) throw IOException("Cannot save update APK")
        return target
    } finally {
        partial.delete()
    }
}
