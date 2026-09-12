package com.breakyuna.esjzone.update

import com.breakyuna.esjzone.network.readTextBounded
import android.content.Context
import com.breakyuna.esjzone.BuildConfig
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal data class ReleaseUpdate(val version: String, val pageUrl: String, val description: String)

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
        check(context)
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
                    .url("https://api.github.com/repos/breakyuna/EsjzoneViewer/releases/latest")
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
                    val release = JSONObject(response.body.readTextBounded())
                    if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) {
                        _status.value = ReleaseCheckState.UpToDate
                        return@launch
                    }
                    val tag = release.optString("tag_name")
                    if (!ReleaseVersion.isNewerStableRelease(tag, BuildConfig.VERSION_NAME)) {
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
                    pending.value = ReleaseUpdate(tag, url.toString(), release.optString("body").trim())
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
        pending.value = null
    }

    private const val PREFERENCES_NAME = "release_update_checker"
    private const val LAST_CHECK_AT_KEY = "last_check_at"
    private const val AUTO_CHECK_KEY = "auto_check"
    private const val CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
}
