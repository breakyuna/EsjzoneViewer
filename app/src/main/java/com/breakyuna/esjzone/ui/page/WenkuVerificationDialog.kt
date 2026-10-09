package com.breakyuna.esjzone.ui.page

import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.external.MAX_BROWSER_CHAPTER_HTML_BYTES
import com.breakyuna.esjzone.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.json.JSONTokener

private const val OVERSIZED_BROWSER_CHAPTER = "__ESJ_BROWSER_CHAPTER_TOO_LARGE__"
private const val UNAVAILABLE_BROWSER_CHAPTER = "__ESJ_BROWSER_CHAPTER_UNAVAILABLE__"
private const val BROWSER_CHAPTER_HTML_CHUNK_LENGTH = 32_768

private fun clearanceValue(raw: String?): String? = raw?.split(';')?.firstNotNullOfOrNull { item ->
    item.trim().takeIf { it.startsWith("cf_clearance=") }
        ?.substringAfter('=')?.takeIf { it.isNotBlank() }
}

@Composable
internal fun WenkuVerificationDialog(
    url: String,
    acceptChapterContent: Boolean = false,
    onVerified: (String?) -> Unit,
    onUnavailable: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val browser = remember(url) { runCatching { WebView(context) }.getOrNull() }
    val completed = remember(url) { AtomicBoolean(false) }
    val checking = remember(url) { AtomicBoolean(false) }
    val active = remember(url) { AtomicBoolean(true) }
    val timedOut = remember(url) { mutableStateOf(false) }
    DisposableEffect(browser) {
        onDispose { active.set(false); browser?.stopLoading(); browser?.destroy() }
    }
    if (browser == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.wenku_webview_unavailable)) },
            text = { Text(stringResource(R.string.wenku_webview_unavailable_desc)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wenku_verification_title)) },
        text = {
            Column {
                key(url) { AndroidView(
                    factory = {
                    val cookieManager = CookieManager.getInstance()
                    val expectedPath = JSONObject.quote(Uri.parse(url).encodedPath.orEmpty())
                    val readableChapterScript = """
                        (function() {
                            const content = document.querySelector('#content');
                            return location.protocol === 'https:' &&
                                location.hostname === 'www.wenku8.net' &&
                                location.pathname === $expectedPath &&
                                !/^just a moment|^attention required/i.test(document.title.trim()) &&
                                !!content &&
                                (content.textContent.trim().length >= 20 || !!content.querySelector('img')) &&
                                !document.querySelector('#challenge-stage, #challenge-form, .cf-turnstile');
                        })();
                    """.trimIndent()
                    var policy = WenkuVerificationPolicy(acceptChapterContent)
                    var checkStartedAtMillis = SystemClock.elapsedRealtime()
                    fun complete(rawCookies: String?, html: String?) {
                        if (!active.get() || completed.get()) return
                        cookieManager.flush()
                        if (!EsjzoneClient.importWenkuBrowserCookies(rawCookies, url)) {
                            if (completed.compareAndSet(false, true)) onUnavailable()
                        } else if (completed.compareAndSet(false, true)) {
                            onVerified(html)
                        }
                    }
                    fun captureChapter(view: WebView, rawCookies: String?) {
                        val captureScript = """
                            (function() {
                                const source = document.querySelector('#content');
                                if (location.protocol !== 'https:' ||
                                    location.hostname !== 'www.wenku8.net' ||
                                    location.pathname !== $expectedPath ||
                                    /^just a moment|^attention required/i.test(document.title.trim()) ||
                                    !source ||
                                    (source.textContent.trim().length < 20 && !source.querySelector('img')) ||
                                    document.querySelector('#challenge-stage, #challenge-form, .cf-turnstile')) {
                                    return '$UNAVAILABLE_BROWSER_CHAPTER';
                                }
                                const doc = document.implementation.createHTMLDocument('');
                                const title = doc.createElement('div');
                                title.id = 'title';
                                const sourceTitle = document.querySelector('#title');
                                title.textContent = (sourceTitle && sourceTitle.textContent || document.title || '').trim();
                                const content = source.cloneNode(true);
                                content.querySelectorAll('script, style, iframe, form, button, nav, .ad, .ads, [id*=advert], [class*=advert]')
                                    .forEach(element => element.remove());
                                content.querySelectorAll('img').forEach(image => {
                                    if (/^(data:|blob:)/i.test((image.getAttribute('src') || '').trim())) image.remove();
                                });
                                doc.body.append(title, content);
                                document.querySelectorAll('a[href]').forEach(link => {
                                    const label = link.textContent.trim();
                                    if (/^(上一页|上一章|下一页|下一章)$/.test(label)) {
                                        const copy = doc.createElement('a');
                                        copy.href = link.href;
                                        copy.textContent = label;
                                        doc.body.append(copy);
                                    }
                                });
                                const html = doc.documentElement.outerHTML;
                                if (new Blob([html]).size > $MAX_BROWSER_CHAPTER_HTML_BYTES) {
                                    return '$OVERSIZED_BROWSER_CHAPTER';
                                }
                                window.__esjzoneWenkuChapterHtml = html;
                                return String(html.length);
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(captureScript) { encodedLength ->
                            if (!active.get() || completed.get()) return@evaluateJavascript
                            val descriptor = runCatching { JSONTokener(encodedLength).nextValue() as? String }
                                .getOrNull()
                            if (descriptor == OVERSIZED_BROWSER_CHAPTER) {
                                AppLogger.w("WenkuVerificationDialog", "Browser chapter exceeds the safe capture size")
                                complete(rawCookies, null)
                                return@evaluateJavascript
                            }
                            val length = descriptor?.toIntOrNull()
                            if (length == null || length <= 0 || length > MAX_BROWSER_CHAPTER_HTML_BYTES) {
                                AppLogger.w("WenkuVerificationDialog", "Browser chapter capture returned no usable document")
                                complete(rawCookies, null)
                                return@evaluateJavascript
                            }
                            val captured = StringBuilder(length)
                            fun readChunk(offset: Int) {
                                if (!active.get() || completed.get()) return
                                if (offset >= length) {
                                    complete(rawCookies, captured.toString())
                                    return
                                }
                                val end = minOf(offset + BROWSER_CHAPTER_HTML_CHUNK_LENGTH, length)
                                view.evaluateJavascript(
                                    "(window.__esjzoneWenkuChapterHtml || '').slice($offset, $end);"
                                ) { encodedChunk ->
                                    if (!active.get() || completed.get()) return@evaluateJavascript
                                    val chunk = runCatching { JSONTokener(encodedChunk).nextValue() as? String }
                                        .getOrNull()
                                    if (chunk.isNullOrEmpty()) {
                                        AppLogger.w("WenkuVerificationDialog", "Browser chapter capture ended before completion")
                                        complete(rawCookies, null)
                                    } else {
                                        captured.append(chunk)
                                        readChunk(offset + chunk.length)
                                    }
                                }
                            }
                            readChunk(0)
                        }
                    }
                    fun checkPage(view: WebView) {
                        if (!active.get() || completed.get()) return
                        view.evaluateJavascript(readableChapterScript) { result ->
                            if (!active.get() || completed.get()) return@evaluateJavascript
                            val rawCookies = cookieManager.getCookie(url)
                            // The policy handles chapter readiness and its clearance fallback.
                            val hasClearance = clearanceValue(rawCookies) != null
                            val now = SystemClock.elapsedRealtime()
                            when (policy.next(result == "true", hasClearance, now)) {
                                WenkuVerificationPolicy.Decision.USE_CHAPTER_CONTENT -> captureChapter(view, rawCookies)
                                WenkuVerificationPolicy.Decision.USE_CLEARANCE -> complete(rawCookies, null)
                                WenkuVerificationPolicy.Decision.WAIT -> {
                                    if (policy.shouldContinue(now, checkStartedAtMillis)) {
                                        view.postDelayed({ checkPage(view) }, 500)
                                    } else {
                                        checking.set(false)
                                        timedOut.value = true
                                    }
                                }
                            }
                        }
                    }
                    fun startCheck(view: WebView) {
                        if (!active.get() || completed.get() || !checking.compareAndSet(false, true)) return
                        policy = WenkuVerificationPolicy(acceptChapterContent)
                        checkStartedAtMillis = SystemClock.elapsedRealtime()
                        timedOut.value = false
                        checkPage(view)
                    }
                    browser.apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = EsjzoneClient.wenkuUserAgent()
                            allowFileAccess = false
                            allowContentAccess = false
                            allowFileAccessFromFileURLs = false
                            allowUniversalAccessFromFileURLs = false
                        }
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.isForMainFrame &&
                                    (request.url.scheme != "https" || request.url.host != "www.wenku8.net" ||
                                        (request.url.port != -1 && request.url.port != 443) || request.url.userInfo != null)

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val host = request.url.host.orEmpty()
                                if (request.url.scheme == "https" && (request.url.port == -1 || request.url.port == 443) &&
                                    request.url.userInfo == null && host in setOf(
                                        "www.wenku8.net", "challenges.cloudflare.com", "www.cloudflare.com")) return null
                                return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                            }

                            override fun onPageFinished(view: WebView, finishedUrl: String) {
                                startCheck(view)
                            }
                        }
                        loadUrl(url)
                    }
                    },
                    modifier = Modifier.fillMaxWidth().height(420.dp)
                ) }
                if (timedOut.value) Text(stringResource(R.string.wenku_verification_timeout))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        dismissButton = {
            if (timedOut.value) TextButton(onClick = {
                checking.set(false)
                browser.reload()
            }) { Text(stringResource(R.string.wenku_verification_retry)) }
        }
    )
}
