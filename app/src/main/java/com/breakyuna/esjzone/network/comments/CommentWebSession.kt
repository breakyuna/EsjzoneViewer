package com.breakyuna.esjzone.network.comments

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.features.ForumReplyBusinessException
import com.breakyuna.esjzone.network.features.commentParentId
import com.breakyuna.esjzone.network.features.parseComments
import com.breakyuna.esjzone.novellibrary.novel.Comment
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlin.coroutines.resume

internal class CommentBrowserUnavailableException : IOException("Comment browser unavailable")
internal class CommentBrowserPreparationException : IOException("Comment page is not ready or login has expired")
internal class CommentBrowserNotDispatchedException : IOException("Comment page did not start a submission")
internal class CommentBrowserSnapshotTooLargeException : IOException("Comment snapshot exceeds the browser limit")
internal class CommentReplyNotLoadedException : IOException("Reply target is absent from the comment page")
internal class CommentSessionChangedException : IOException("Comment account session changed")
internal data class CommentWebResult(
    val comments: List<Comment>,
    val created: Comment?,
    val accepted: Boolean,
    val acceptedId: String?
)

/** A single isolated, invisible browser. Never retries a write or falls back to HTTP. */
internal object CommentWebSession {
    private val mutex = Mutex()
    private const val PROFILE_PREFIX = "esj_comments_"
    private var cleanedOldProfiles = false

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun submit(
        context: Context,
        authorization: Authorization,
        pageUrl: String,
        content: String,
        replyToken: String?,
        onDispatch: suspend (Set<String>, String?) -> Unit,
        onAccepted: suspend (String?) -> Unit,
        onUncertain: () -> Unit
    ): CommentWebResult = mutex.withLock {
        val script = withContext(Dispatchers.IO) {
            context.assets.open("comment_session.js").bufferedReader().use { it.readText() }
        }
        val url = pageUrl.toHttpUrl()
        require(url.isHttps && EsjzoneUrls.isEsjHost(url.host))
        val jar = EsjzoneClient.persistentCookieJar ?: throw CommentBrowserPreparationException()
        val epoch = jar.sessionEpoch()
        fun current() = jar.sessionEpoch() == epoch && jar.belongsToActiveAccount(authorization) &&
            jar.isActiveSession(url.host)
        val cookies = AuthorizationCookieJar(authorization).loadForRequest(url)
        if (!current() || cookies.isEmpty()) throw CommentBrowserPreparationException()
        withContext(Dispatchers.Main.immediate) {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
            ) throw CommentBrowserUnavailableException()
            val profiles = ProfileStore.getInstance()
            if (!cleanedOldProfiles) {
                profiles.allProfileNames.filter { it.startsWith(PROFILE_PREFIX) }.forEach {
                    runCatching { profiles.deleteProfile(it) }
                }
                cleanedOldProfiles = true
            }
            val profileName = PROFILE_PREFIX + java.util.UUID.randomUUID().toString()
            val web = WebView(context.applicationContext)
            var browserProfile: androidx.webkit.Profile? = null
            var active = true
            var dead = false
            var submitted = false
            var accepted = false
            var acceptedId: String? = null
            var recordedAcceptance = false
            var dispatchedAt: Long? = null
            var uncertainAt: Long? = null
            var networkUnknown = false
            var snapshotTooLarge = false
            var authorName: String? = null
            var rejection: String? = null
            var html = ""
            var previousIds = emptySet<String>()
            var comments = emptyList<Comment>()
            var created: Comment? = null
            try {
                WebViewCompat.setProfile(web, profileName)
                val profile = WebViewCompat.getProfile(web)
                browserProfile = profile
                val cookieManager = profile.cookieManager
                withTimeoutOrNull(5_000L) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        cookieManager.removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
                    }
                }
                ?: throw CommentBrowserPreparationException()
                profile.webStorage.deleteAllData()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(web, false)
                for (cookie in cookies) {
                    val applied = withTimeoutOrNull(5_000L) {
                        suspendCancellableCoroutine { continuation ->
                            cookieManager.setCookie(url.toString(), cookie.toString()) {
                                if (continuation.isActive) continuation.resume(it)
                            }
                        }
                    }
                    if (applied != true) throw CommentBrowserPreparationException()
                }
                web.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    setSupportMultipleWindows(false)
                    mediaPlaybackRequiresUserGesture = true
                }
                web.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.scheme != "https" || request.url.host != url.host ||
                            (request.isForMainFrame && request.url.path != url.encodedPath)

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val permitted = current() && request.url.scheme == "https" &&
                            (!EsjzoneUrls.isEsjHost(request.url.host.orEmpty()) || request.url.host == url.host) &&
                            (!request.isForMainFrame || (request.url.host == url.host && request.url.path == url.encodedPath)) &&
                            (request.method == "GET" || (request.url.host == url.host &&
                                request.url.path in setOf(url.encodedPath, "/inc/forum_reply.php", "/inc/gb_reply.php")))
                        return if (permitted) null else WebResourceResponse(
                            "text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf())
                        )
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        dead = true
                        return true
                    }
                }
                val origin = "https://${url.host}" + if (url.port == 443) "" else ":${url.port}"
                WebViewCompat.addWebMessageListener(web, "EsjCommentBridge", setOf(origin)) { _, message, source, main, _ ->
                    if (!active || !main || !current() || source.toString().trimEnd('/') != origin) return@addWebMessageListener
                    val raw = message.data ?: return@addWebMessageListener
                    if (raw.length > 1_600_000) return@addWebMessageListener
                    val event = runCatching { JSONObject(raw) }.getOrNull() ?: return@addWebMessageListener
                    when (event.optString("type")) {
                        "author" -> authorName = event.optString("name").takeIf { it.isNotBlank() }
                        "dom" -> html = event.optString("html")
                        "dispatched" -> dispatchedAt = android.os.SystemClock.elapsedRealtime()
                        "unknown" -> networkUnknown = true
                        "snapshot_too_large" -> snapshotTooLarge = true
                        "response" -> if (submitted) {
                            when (event.optInt("status", 0)) {
                                200 -> {
                                    accepted = true
                                    acceptedId = event.optString("anchor").takeIf { it.matches(Regex("#comment-[0-9]+")) }
                                        ?.removePrefix("#comment-")
                                }
                                in 201..599 -> rejection = event.optString("msg").take(1000)
                                else -> Unit // Malformed/unknown replies cannot authorize another write.
                            }
                        }
                    }
                }
                WebViewCompat.addDocumentStartJavaScript(web, script, setOf(origin))
                web.loadUrl(url.toString())
                val reply = JSONObject.quote(replyToken.orEmpty())
                val ready = withTimeoutOrNull(30_000L) {
                    while (!dead && current()) {
                        when (web.evaluate("window.__esjComment && window.__esjComment.prepare($reply)")) {
                            "true" -> return@withTimeoutOrNull true
                            "\"missing_reply\"" -> throw CommentReplyNotLoadedException()
                        }
                        delay(250)
                    }
                    false
                } == true
                if (!current()) throw CommentSessionChangedException()
                if (!ready) throw CommentBrowserPreparationException()
                val initialMarkup = web.evaluate("window.__esjComment.snapshot()")
                val decoded = runCatching { org.json.JSONTokener(initialMarkup).nextValue() as? String }.getOrNull()
                    ?: throw CommentBrowserPreparationException()
                if (decoded == "__esj_snapshot_too_large__") throw CommentBrowserSnapshotTooLargeException()
                comments = withContext(Dispatchers.Default) { parseComments(Jsoup.parse(decoded, pageUrl), commentParentId(pageUrl)) }
                previousIds = comments.mapTo(mutableSetOf()) { it.id }
                // Persist uncertainty BEFORE JS can dispatch. A lost callback is not safe to retry.
                onDispatch(previousIds, authorName)
                if (!current()) throw CommentSessionChangedException()
                submitted = true
                if (web.evaluate("window.__esjComment.submit(${JSONObject.quote(content)}, $reply)") == "false") {
                    throw CommentBrowserNotDispatchedException()
                }
                var parsedHtml = ""
                var parsedAcceptedId: String? = null
                val observingSince = android.os.SystemClock.elapsedRealtime()
                withTimeoutOrNull(90_000L) {
                    while (!dead && current()) {
                        rejection?.let { throw ForumReplyBusinessException(it) }
                        val now = android.os.SystemClock.elapsedRealtime()
                        val observation = commentObservationDecision(
                            accepted, networkUnknown, dispatchedAt, observingSince, uncertainAt, now
                        )
                        if (accepted && !recordedAcceptance) {
                            onAccepted(acceptedId)
                            recordedAcceptance = true
                        } else if (observation.notifyUncertain) {
                            onUncertain()
                            uncertainAt = now
                        }
                        if (snapshotTooLarge && !accepted) throw CommentBrowserSnapshotTooLargeException()
                        if (html != parsedHtml || acceptedId != parsedAcceptedId) {
                            parsedHtml = html
                            parsedAcceptedId = acceptedId
                            val snapshot = html
                            comments = withContext(Dispatchers.Default) {
                                parseComments(Jsoup.parse(snapshot, pageUrl), commentParentId(pageUrl))
                            }
                            created = findObservedComment(comments, previousIds, content, acceptedId, authorName)
                            if (created != null) return@withTimeoutOrNull
                        }
                        if (observation.stop) {
                            return@withTimeoutOrNull
                        }
                        delay(250)
                    }
                }
                if (!current()) throw CommentSessionChangedException()
                CommentWebResult(comments, created, accepted, acceptedId)
            } finally {
                active = false
                withContext(NonCancellable) {
                    runCatching { web.stopLoading() }
                    runCatching { web.destroy() }
                    // Browser rotations never overwrite the native session with an unverified cookie set.
                    // Remove browser credentials on completion, including cancellation and renderer death.
                    browserProfile?.let { profile ->
                        withTimeoutOrNull(5_000L) {
                            suspendCancellableCoroutine<Unit> { continuation ->
                                profile.cookieManager.removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
                            }
                        }
                        profile.webStorage.deleteAllData()
                    }
                    // Some providers retain loaded profiles until process exit. A new process
                    // removes those orphans before creating its first comment browser.
                    runCatching { profiles.deleteProfile(profileName) }
                }
            }
        }
    }

    private suspend fun WebView.evaluate(script: String): String = withTimeoutOrNull(3_000L) {
        suspendCancellableCoroutine { continuation ->
            evaluateJavascript(script) { if (continuation.isActive) continuation.resume(it ?: "null") }
        }
    } ?: "null"
}
