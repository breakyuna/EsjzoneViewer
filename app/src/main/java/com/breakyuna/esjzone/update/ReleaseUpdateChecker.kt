package com.breakyuna.esjzone.update

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

    fun checkOnce() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try {
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
                    val release = JSONObject(response.body?.string() ?: return@launch)
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
}
