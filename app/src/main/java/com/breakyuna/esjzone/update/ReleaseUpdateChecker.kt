package com.breakyuna.esjzone.update

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

internal data class ReleaseUpdate(val version: String, val pageUrl: String)

/** Process-wide state survives Activity recreation without repeating requests or prompts. */
internal object ReleaseUpdateChecker {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = MutableStateFlow<ReleaseUpdate?>(null)
    val update = pending.asStateFlow()

    /**
     * Performs at most one release lookup per device every 24 hours.
     *
     * The in-memory guard avoids duplicate requests caused by Activity recreation, while the
     * preference timestamp prevents the same release from prompting again on every app launch.
     */
    fun checkOnce(context: Context) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val preferences = context.applicationContext.getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
                val lastCheckAt = preferences.getLong(LAST_CHECK_AT_KEY, 0L)
                if (lastCheckAt <= now && now - lastCheckAt < CHECK_INTERVAL_MILLIS) {
                    return@launch
                }
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
                    if (!response.isSuccessful) return@launch
                    val release = JSONObject(response.body.string())
                    if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) return@launch
                    val tag = release.optString("tag_name")
                    if (!ReleaseVersion.isNewerStableRelease(tag, BuildConfig.VERSION_NAME)) return@launch
                    val url = release.optString("html_url").toHttpUrlOrNull() ?: return@launch
                    if (url.scheme != "https" || url.host != "github.com" || url.port != 443 ||
                        url.username.isNotEmpty() || url.password.isNotEmpty() ||
                        !url.encodedPath.startsWith("/breakyuna/EsjzoneViewer/releases/tag/")) return@launch
                    pending.value = ReleaseUpdate(tag, url.toString())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Startup checks are best effort: offline, timeout and malformed data stay silent.
            }
        }
    }

    fun dismiss() {
        pending.value = null
    }

    private const val PREFERENCES_NAME = "release_update_checker"
    private const val LAST_CHECK_AT_KEY = "last_check_at"
    private const val CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
}
