package com.breakyuna.esjzone.update

import com.breakyuna.esjzone.network.readTextBounded
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.MainActivity
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

internal data class ReleaseUpdate(
    val version: String,
    val pageUrl: String,
    val description: String,
    val apk: ReleaseApkAsset?,
    val downloader: ReleaseApkDownloader = ReleaseApkDownloader()
)

internal sealed interface ReleaseCheckState {
    data object Idle : ReleaseCheckState
    data object Checking : ReleaseCheckState
    data object UpToDate : ReleaseCheckState
    data object Error : ReleaseCheckState
    data class Available(val version: String) : ReleaseCheckState
}

/** Process-wide state survives Activity recreation without repeating requests or prompts. */
internal object ReleaseUpdateChecker {
    private val checking = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = MutableStateFlow<ReleaseUpdate?>(null)
    val update = pending.asStateFlow()
    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible = _dialogVisible.asStateFlow()
    private val _status = MutableStateFlow<ReleaseCheckState>(ReleaseCheckState.Idle)
    val status = _status.asStateFlow()
    private val _autoCheck = MutableStateFlow(true)
    val autoCheck = _autoCheck.asStateFlow()

    /**
     * Performs at most one release lookup per device every 24 hours.
     *
     * The in-memory guard avoids duplicate requests caused by Activity recreation, while the
     * preference timestamp prevents the same release from prompting again on every app launch.
     */
    fun checkOnce(context: Context) {
        initialize(context)
        if (pending.value != null) return
        if (!_autoCheck.value) return
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheckAt = preferences.getLong(LAST_CHECK_AT_KEY, 0L)
        if (lastCheckAt <= now && now - lastCheckAt < CHECK_INTERVAL_MILLIS) return
        check(context)
    }

    fun initialize(context: Context) {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        _autoCheck.value = preferences.getBoolean(AUTO_CHECK_KEY, true)
    }

    fun setAutoCheck(context: Context, enabled: Boolean) {
        _autoCheck.value = enabled
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(AUTO_CHECK_KEY, enabled).apply()
    }

    fun checkNow(context: Context) {
        if (pending.value != null) {
            showDialog()
            return
        }
        check(context)
    }

    fun showDialog() {
        _dialogVisible.value = true
    }

    fun hideDialog() {
        _dialogVisible.value = false
    }

    fun startDownload(context: Context) {
        val release = pending.value ?: return
        if (release.apk == null || release.downloader.state.value is ReleaseDownloadState.Downloading) return
        release.downloader.markStarting(release.apk)
        try {
            ContextCompat.startForegroundService(context.applicationContext,
                Intent(context.applicationContext, AppUpdateDownloadService::class.java))
        } catch (_: RuntimeException) {
            release.downloader.markFailed()
        }
    }

    fun cancelDownload(context: Context) {
        pending.value?.downloader?.cancel()
        context.applicationContext.stopService(Intent(context.applicationContext, AppUpdateDownloadService::class.java))
    }

    /** A small payload also lets a completed notification reopen the APK after process recreation. */
    fun downloadIntent(context: Context, release: ReleaseUpdate): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_UPDATE_VERSION, release.version)
            .putExtra("update_page", release.pageUrl)
            .putExtra("update_apk_name", release.apk?.name)
            .putExtra("update_apk_url", release.apk?.url)
            .putExtra("update_apk_size", release.apk?.size ?: 0L)
            .putExtra("update_apk_digest", release.apk?.digest)

    internal fun releaseFromDownloadIntent(intent: Intent): ReleaseUpdate? {
        val tag = intent.getStringExtra(EXTRA_UPDATE_VERSION) ?: return null
        if (!ReleaseVersion.isNewerForInstalledChannel(tag, BuildConfig.VERSION_NAME)) return null
        val page = intent.getStringExtra("update_page")?.toHttpUrlOrNull() ?: return null
        if (page.scheme != "https" || page.host != "github.com" || page.port != 443 ||
            page.username.isNotEmpty() || page.password.isNotEmpty() ||
            page.pathSegments != listOf("breakyuna", "EsjzoneViewer", "releases", "tag", tag)) return null
        val asset = ReleaseApkAsset.select(tag, listOf(ReleaseApkAsset(
            name = intent.getStringExtra("update_apk_name").orEmpty(),
            url = intent.getStringExtra("update_apk_url").orEmpty(),
            size = intent.getLongExtra("update_apk_size", 0L),
            digest = intent.getStringExtra("update_apk_digest")
        ))) ?: return null
        return ReleaseUpdate(tag, page.toString(), "", asset)
    }

    fun openDownload(context: Context, intent: Intent) {
        val release = releaseFromDownloadIntent(intent) ?: return
        val current = pending.value
        if (current != null) {
            showDialog()
            return
        }
        pending.value = release
        showDialog()
        val appContext = context.applicationContext
        scope.launch {
            val file = File(appContext.cacheDir, "app_updates/update.apk")
            val info = if (file.isFile && file.length() == release.apk?.size) {
                appContext.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            } else null
            if (info != null && info.packageName == appContext.packageName &&
                info.longVersionCode > BuildConfig.VERSION_CODE) {
                withContext(Dispatchers.Main.immediate) {
                    if (pending.value === release && release.downloader.state.value == ReleaseDownloadState.Idle) {
                        release.downloader.restoreReady(file)
                    }
                }
            }
        }
    }

    private fun check(context: Context) {
        if (!checking.compareAndSet(false, true)) return
        _status.value = ReleaseCheckState.Checking
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val preferences = context.applicationContext.getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
                // Record the attempt before the request so a slow/offline network cannot cause a
                // repeated startup lookup. The next scheduled daily check can retry normally.
                preferences.edit().putLong(LAST_CHECK_AT_KEY, now).apply()

                // Independent client: never send ESJ cookies or change its network/session state.
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .callTimeout(10, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .build()
                val request = Request.Builder()
                    .url("https://api.github.com/repos/breakyuna/EsjzoneViewer/releases?per_page=20")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "EsjzoneViewer/${BuildConfig.VERSION_NAME}")
                    .build()
                client.newCall(request).execute().use { response ->
                    // Includes no published Release (404), rate limiting and server errors.
                    if (!response.isSuccessful) {
                        _status.value = ReleaseCheckState.Error
                        return@launch
                    }
                    val releases = JSONArray(response.body.readTextBounded())
                    val betaChannel = BuildConfig.VERSION_NAME.contains("beta", ignoreCase = true)
                    val release = (0 until releases.length())
                        .asSequence()
                        .map { releases.optJSONObject(it) }
                        .filterNotNull()
                        .firstOrNull { candidate ->
                            !candidate.optBoolean("draft", false) &&
                                (candidate.optBoolean("prerelease", false) == betaChannel)
                        }
                    if (release == null) {
                        _status.value = ReleaseCheckState.UpToDate
                        return@launch
                    }
                    val tag = release.optString("tag_name")
                    if (!ReleaseVersion.isNewerForInstalledChannel(tag, BuildConfig.VERSION_NAME)) {
                        _status.value = ReleaseCheckState.UpToDate
                        return@launch
                    }
                    val url = release.optString("html_url").toHttpUrlOrNull() ?: run {
                        _status.value = ReleaseCheckState.Error
                        return@launch
                    }
                    if (url.scheme != "https" || url.host != "github.com" || url.port != 443 ||
                        url.username.isNotEmpty() || url.password.isNotEmpty() ||
                        !url.encodedPath.startsWith("/breakyuna/EsjzoneViewer/releases/tag/")) {
                        _status.value = ReleaseCheckState.Error
                        return@launch
                    }
                    val assets = release.optJSONArray("assets")
                    val apks = (0 until (assets?.length() ?: 0)).mapNotNull { index ->
                        val asset = assets?.optJSONObject(index) ?: return@mapNotNull null
                        if (asset.optString("state") != "uploaded") return@mapNotNull null
                        ReleaseApkAsset(
                            name = asset.optString("name"),
                            url = asset.optString("browser_download_url"),
                            size = asset.optLong("size", 0),
                            digest = asset.optString("digest").takeIf { it.isNotBlank() }
                        )
                    }
                    withContext(Dispatchers.Main.immediate) {
                        if (pending.value == null) {
                            pending.value = ReleaseUpdate(tag, url.toString(), release.optString("body").trim(),
                                ReleaseApkAsset.select(tag, apks))
                            showDialog()
                        }
                    }
                    _status.value = ReleaseCheckState.Available(tag)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Startup checks are best effort: offline, timeout and malformed data stay silent.
                _status.value = ReleaseCheckState.Error
            } finally {
                checking.set(false)
            }
        }
    }

    fun dismiss() {
        hideDialog()
        val state = pending.value?.downloader?.state?.value
        if (state !is ReleaseDownloadState.Downloading && state !is ReleaseDownloadState.Ready) pending.value = null
    }

    internal const val EXTRA_UPDATE_VERSION = "app_update_version"
    private const val PREFERENCES_NAME = "release_update_checker"
    private const val LAST_CHECK_AT_KEY = "last_check_at"
    private const val AUTO_CHECK_KEY = "auto_check"
    private const val CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
}
