package com.breakyuna.esjzone.network.external

import android.content.Context
import androidx.annotation.MainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.network.wenku8.Wenku8PageKind
import com.breakyuna.esjzone.network.wenku8.wenku8PageAllowed
import com.breakyuna.esjzone.network.wenku8.wenku8PageCacheIdentity
import com.breakyuna.esjzone.network.wenku8.Wenku8PageResponse
import java.io.IOException
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.StructuredChapterCache
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageResponsePolicy
import com.breakyuna.esjzone.network.NetworkHttpException
import com.breakyuna.esjzone.network.pageRequestCancellation
import com.breakyuna.esjzone.network.readBytesBounded
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class WenkuChapterClient(context: Context, userAgent: String) {
    private val jar = WenkuCookieJar(context)
    private val browser = WenkuWebViewSession(context, userAgent, ::restoreBrowserCookies)
    private val sessionLock = Any()
    private val sessionEpoch = java.util.concurrent.atomic.AtomicLong()
    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .addInterceptor { chain ->
            jar.withinRequest(chain.request().tag(String::class.java)) { chain.proceed(chain.request()) }
        }
        .dns(Dns { host ->
            if (host != "www.wenku8.net") throw java.net.UnknownHostException("External host is not allowed")
            val addresses = Dns.SYSTEM.lookup(host)
            addresses.filter(::isPublicAddress).takeIf { it.isNotEmpty() }
                ?: throw java.net.UnknownHostException("External host has no public address")
        })
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    private val userAgentHeader = userAgent
    // An explicit process-only login must not be resurrected by either snapshot store.
    // Native cookies are cleared synchronously; browser navigation waits for scoped deletion.
    // After construction, this flag and the restore queue are accessed only on main.
    private var startupClearing = jar.processSessionFromPreviousRun
    private val pendingRestores = mutableListOf<() -> Unit>()

    init {
        if (startupClearing) clearSession {
            startupClearing = false
            val callbacks = pendingRestores.toList()
            pendingRestores.clear()
            callbacks.forEach { jar.restoreBrowserCookies(it) }
        }
    }

    @MainThread
    fun restoreBrowserCookies(onRestored: () -> Unit) {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        if (startupClearing) pendingRestores += onRestored else jar.restoreBrowserCookies(onRestored)
    }

    suspend fun recordBrowserLogin(usecookie: String) {
        val epoch = sessionEpoch.get()
        withContext(Dispatchers.IO) {
            synchronized(sessionLock) {
                if (epoch != sessionEpoch.get()) throw WenkuBrowserSessionClosedException()
                jar.recordBrowserLogin(usecookie)
            }
        }
    }

    fun importBrowserCookies(raw: String?, sourceUrl: String = "https://www.wenku8.net/") = synchronized(sessionLock) {
        sessionEpoch.incrementAndGet()
        browser.close()
        jar.importBrowserCookies(raw, sourceUrl, forceNewScope = true)
    }
    fun clearSession(onCleared: () -> Unit = {}) {
        val cookies = synchronized(sessionLock) {
            sessionEpoch.incrementAndGet()
            browser.close()
            jar.clear()
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val manager = android.webkit.CookieManager.getInstance()
            val origin = "https://www.wenku8.net/"
            val names = manager.getCookie(origin).orEmpty().split(';').map { it.trim().substringBefore('=') }
            val targets = cookies.map { Triple(it.name, it.path, if (it.hostOnly) "" else it.domain) } +
                names.filter(String::isNotBlank).flatMap { name ->
                    listOf("", "www.wenku8.net", "wenku8.net").map { domain -> Triple(name, "/", domain) }
                }
            val distinctTargets = targets.distinct()
            fun finish() {
                android.webkit.WebStorage.getInstance().deleteOrigin("https://www.wenku8.net")
                manager.flush()
                onCleared()
            }
            if (distinctTargets.isEmpty()) finish()
            else {
                var remaining = distinctTargets.size
                distinctTargets.forEach { (name, path, domain) ->
                    manager.setCookie(origin, "$name=; Max-Age=0; Path=$path; Secure" +
                        domain.takeIf(String::isNotBlank)?.let { "; Domain=$it" }.orEmpty()) {
                        remaining--
                        if (remaining == 0) finish()
                    }
                }
            }
        }
    }

    /** Save a chapter already rendered by the verified, same-host browser. Call on IO. */
    fun importBrowserChapter(chapter: Chapter, url: String, html: String): Boolean {
        if (resolveChapterSource(url) != ChapterSource.WENKU8 ||
            html.toByteArray(Charsets.UTF_8).size > MAX_BROWSER_CHAPTER_HTML_BYTES) return false
        val epoch = sessionEpoch.get()
        val key = cacheKey(url)
        val detail = runCatching { validateAndParse(html, chapter, url) }.getOrNull() ?: return false
        val cachedDocument = cacheChapter(url, detail, epoch, key)
        return PageCache.read(key, PageCacheTtl.CHAPTER) == cachedDocument
    }

    fun userAgent(): String = userAgentHeader

    fun closeBrowserSession() = browser.close()

    private val readerImageClient: OkHttpClient by lazy {
        client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", userAgentHeader)
                    .header("Referer", "https://www.wenku8.net/")
                    .build())
            }
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .build()
    }

    fun imageClient(): OkHttpClient = readerImageClient

    fun load(chapter: Chapter, url: String, forceRefresh: Boolean, allowAutoSolve: Boolean,
             onSecurityCheck: (() -> Unit)? = null): DetailedChapter {
        if (resolveChapterSource(url) != ChapterSource.WENKU8) throw UnsupportedExternalChapterException(requestedUrl = url)
        val epoch = sessionEpoch.get()
        val scope = jar.cacheScope()
        val key = cacheKey(url, scope)
        if (!forceRefresh) StructuredChapterCache.read(key, url)?.let {
            if (sessionEpoch.get() != epoch) throw WenkuBrowserSessionClosedException()
            return it
        }
        if (!forceRefresh) PageCache.read(key, PageCacheTtl.CHAPTER)?.let { cached ->
            runCatching { validateAndParse(cached, chapter, url) }.getOrNull()?.let {
                synchronized(sessionLock) {
                    if (sessionEpoch.get() != epoch) throw WenkuBrowserSessionClosedException()
                    StructuredChapterCache.write(key, cached, it)
                }
                return it
            }
            PageCache.remove(key)
        }
        if (allowAutoSolve && browser.isReady()) {
            try {
                val detail = validateAndParse(browser.fetch(url), chapter, url)
                val browserScope = syncBrowserCookies(url, epoch)
                cacheChapter(url, detail, epoch, browserScope?.let { cacheKey(url, it) } ?: key)
                return detail
            } catch (error: CloudflareChallengeRequiredException) {
                throw error
            } catch (error: WenkuBrowserSessionClosedException) {
                throw error
            } catch (error: java.io.IOException) {
                browser.invalidate()
                if (pageRequestCancellation.get()?.isCancelled() == true) throw error
            }
        }
        val result = request(url, scope = scope)
        if (result.challenge) {
            if (!allowAutoSolve) throw CloudflareChallengeRequiredException()
            onSecurityCheck?.invoke()
            val html = browser.fetch(url)
            val detail = try {
                validateAndParse(html, chapter, url)
            } catch (error: IOException) {
                browser.invalidate()
                throw error
            }
            val browserScope = syncBrowserCookies(url, epoch)
            cacheChapter(url, detail, epoch, browserScope?.let { cacheKey(url, it) } ?: key)
            return detail
        }
        if (result.status !in 200..299) throw NetworkHttpException("https://www.wenku8.net/", result.status)
        val detail = validateAndParse(result.html, chapter, url)
        cacheChapter(url, detail, epoch, key)
        return detail
    }

    private fun cacheKey(url: String, scope: String = jar.cacheScope()): String =
        "wenku8|$scope|${EsjzoneUrls.canonicalPageKey(url)}"

    private fun syncBrowserCookies(url: String, epoch: Long): String? =
        try {
            val pairs = browser.cookies(url)
            synchronized(sessionLock) {
                if (sessionEpoch.get() != epoch) throw WenkuBrowserSessionClosedException()
                jar.importBrowserCookies(pairs, url)
                jar.cacheScope()
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw java.io.IOException("Browser request cancelled", error)
        } catch (error: WenkuBrowserSessionClosedException) {
            throw error
        } catch (_: Exception) {
            // Browser HTML remains usable if optional cookie sharing fails.
            null
        }

    private fun cacheChapter(url: String, detail: DetailedChapter, epoch: Long, key: String): String =
        ExternalChapterHtml.cacheDocument(detail, url).also { document ->
            synchronized(sessionLock) {
                if (sessionEpoch.get() != epoch) throw WenkuBrowserSessionClosedException()
                // Use the captured request/bridge namespace, never a later session's current scope.
                PageCache.write(key, document)
                StructuredChapterCache.write(key, document, detail)
            }
        }

    private fun validateAndParse(html: String, chapter: Chapter, url: String): DetailedChapter {
        if (!PageResponsePolicy.validate(200, html, url, kind = PageKind.EXTERNAL_CHAPTER).trusted) {
            throw ExternalChapterParseException()
        }
        return ExternalChapterHtml.parse(html, chapter, url)
    }

    /** Non-chapter pages never enter ESJ transport or the chapter cache. Call on IO. */
    fun loadPage(url: String, kind: Wenku8PageKind, allowAutoSolve: Boolean, forceRefresh: Boolean): Wenku8PageResponse {
        if (!wenku8PageAllowed(url, kind)) throw UnsupportedExternalChapterException("page-input", kind, url)
        com.breakyuna.esjzone.util.AppLogger.i("WenkuChapterClient", "Loading page: kind=$kind, forceRefresh=$forceRefresh, url=${wenkuDiagnosticUrl(url)}")
        val epoch = sessionEpoch.get()
        val scope = jar.cacheScope()
        val initialKey = "wenku8-pages|$scope|${kind.name}|${wenku8PageCacheIdentity(url)}"
        val ttl = when (kind) {
            Wenku8PageKind.HOME -> PageCacheTtl.HOME
            Wenku8PageKind.BROWSE -> PageCacheTtl.LIST
            else -> PageCacheTtl.DETAIL
        }
        if (!forceRefresh) PageCache.read(initialKey, ttl)?.let { cached ->
            val page = Wenku8PageResponse(cached.substringAfter('\n'), cached.substringBefore('\n'))
            if (runCatching { validatePage(page, kind); true }.getOrDefault(false)) {
                if (sessionEpoch.get() != epoch) throw WenkuBrowserSessionClosedException()
                return page
            }
            PageCache.remove(initialKey)
        }
        val result = request(url, kind, scope)
        if (sessionEpoch.get() != epoch) throw WenkuBrowserSessionClosedException()
        var resultKey = initialKey
        val page = if (result.challenge) {
            if (!allowAutoSolve) throw CloudflareChallengeRequiredException()
            val captured = browser.fetchPage(url, kind)
            if (sessionEpoch.get() != epoch) throw WenkuBrowserSessionClosedException()
            syncBrowserCookies(captured.url, epoch)?.let { browserScope ->
                resultKey = "wenku8-pages|$browserScope|${kind.name}|${wenku8PageCacheIdentity(url)}"
            }
            captured
        } else {
            if (result.status !in 200..299) throw NetworkHttpException("https://www.wenku8.net/", result.status)
            Wenku8PageResponse(result.html, result.url)
        }
        try {
            validatePage(page, kind)
        } catch (error: IOException) {
            if (result.challenge) browser.invalidate()
            throw error
        }
        synchronized(sessionLock) {
            if (sessionEpoch.get() != epoch) throw WenkuBrowserSessionClosedException()
            PageCache.write(resultKey, "${page.url}\n${page.html}")
        }
        return page
    }

    private fun validatePage(page: Wenku8PageResponse, kind: Wenku8PageKind) {
        if (!wenku8PageAllowed(page.url, kind)) throw UnsupportedExternalChapterException("page-validation", kind, actualUrl = page.url)
        val parsers = com.breakyuna.esjzone.network.wenku8.Wenku8Parsers
        when (kind) {
            Wenku8PageKind.DETAIL -> parsers.detail(page.html, page.url,
                com.breakyuna.esjzone.novellibrary.novel.NovelChapterList(emptyList()))
            Wenku8PageKind.CATALOG -> parsers.catalog(page.html, page.url)
            Wenku8PageKind.HOME -> parsers.home(page.html, page.url)
            Wenku8PageKind.SEARCH, Wenku8PageKind.BROWSE -> parsers.search(page.html, page.url,
                page.url.toHttpUrlOrNull()?.queryParameter("page")?.toIntOrNull() ?: 1)
            Wenku8PageKind.CHAPTER -> throw UnsupportedExternalChapterException("non-chapter-validation", kind, actualUrl = page.url)
        }
    }

    private fun request(url: String, kind: Wenku8PageKind = Wenku8PageKind.CHAPTER,
        scope: String = jar.cacheScope()): ResponseData {
        var target = url
        repeat(4) {
            val result = requestOnce(target, kind, scope)
            val next = result.redirect
            com.breakyuna.esjzone.util.AppLogger.i("WenkuChapterClient",
                "HTTP result: kind=$kind, hop=$it, status=${result.status}, challenge=${result.challenge}, " +
                    "url=${wenkuDiagnosticUrl(target)}, redirect=${wenkuDiagnosticUrl(next)}")
            if (next == null) return result
            if (com.breakyuna.esjzone.network.wenku8.Wenku8Urls.isLogin(next)) {
                throw com.breakyuna.esjzone.network.wenku8.Wenku8LoginRequiredException()
            }
            if (!wenku8PageAllowed(next, kind)) throw UnsupportedExternalChapterException("http-redirect", kind, url, next)
            target = next
        }
        throw IOException("Wenku8 redirect limit exceeded")
    }

    private fun requestOnce(url: String, kind: Wenku8PageKind, scope: String): ResponseData {
        val request = Request.Builder().url(url).tag(String::class.java, scope).header("User-Agent", userAgentHeader)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml")
            .get().build()
        val call = client.newCall(request)
        val cancellation = pageRequestCancellation.get()
        cancellation?.attach(call)
        try {
            return call.execute().use { response ->
                if (!wenku8PageAllowed(response.request.url.toString(), kind)) {
                    throw UnsupportedExternalChapterException("http-response", kind, url, response.request.url.toString())
                }
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    val next = response.header("Location")?.let(response.request.url::resolve)
                        ?: throw IOException("Wenku8 redirect location missing")
                    return@use ResponseData(response.code, "", false, url, next.toString())
                }
                val contentType = response.header("Content-Type")
                if (response.isSuccessful && contentType != null &&
                    !contentType.contains("text/html", ignoreCase = true) &&
                    !contentType.contains("application/xhtml+xml", ignoreCase = true)) {
                    throw ExternalChapterParseException()
                }
                val bytes = response.body?.readBytesBounded() ?: ByteArray(0)
                val html = ExternalChapterHtml.decode(bytes, contentType)
                ResponseData(
                    response.code, html,
                    CloudflareChallenge.isChallenge(response.code, response.header("cf-mitigated"),
                        response.header("Server"), html), url
                )
            }
        } finally {
            cancellation?.detachCall()
        }
    }

    private data class ResponseData(val status: Int, val html: String, val challenge: Boolean,
        val url: String, val redirect: String? = null)

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) return false
        return true
    }
}
