package com.breakyuna.esjzone.network.external

import android.content.Context
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

internal class WenkuChapterClient(context: Context, userAgent: String) {
    private val jar = WenkuCookieJar(context)
    private val browser = WenkuWebViewSession(context, userAgent)
    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
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

    fun importBrowserCookies(raw: String?) = jar.importBrowserCookies(raw)

    /** Save a chapter already rendered by the verified, same-host browser. Call on IO. */
    fun importBrowserChapter(chapter: Chapter, url: String, html: String): Boolean {
        if (resolveChapterSource(url) != ChapterSource.WENKU8 ||
            html.toByteArray(Charsets.UTF_8).size > MAX_BROWSER_CHAPTER_HTML_BYTES) return false
        val detail = runCatching { validateAndParse(html, chapter, url) }.getOrNull() ?: return false
        val cachedDocument = cacheChapter(url, detail)
        return PageCache.read(cacheKey(url), PageCacheTtl.CHAPTER) == cachedDocument
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
        if (resolveChapterSource(url) != ChapterSource.WENKU8) throw UnsupportedExternalChapterException()
        val key = cacheKey(url)
        if (!forceRefresh) StructuredChapterCache.read(key, url)?.let { return it }
        if (!forceRefresh) PageCache.read(key, PageCacheTtl.CHAPTER)?.let { cached ->
            runCatching { validateAndParse(cached, chapter, url) }.getOrNull()?.let {
                StructuredChapterCache.write(key, cached, it)
                return it
            }
            PageCache.remove(key)
        }
        if (allowAutoSolve && browser.isReady()) {
            try {
                val detail = validateAndParse(browser.fetch(url), chapter, url)
                syncBrowserCookies(url)
                cacheChapter(url, detail)
                return detail
            } catch (error: CloudflareChallengeRequiredException) {
                throw error
            } catch (error: WenkuBrowserSessionClosedException) {
                throw error
            } catch (error: java.io.IOException) {
                if (pageRequestCancellation.get()?.isCancelled() == true) throw error
            }
        }
        val result = request(url)
        if (result.challenge) {
            if (!allowAutoSolve) throw CloudflareChallengeRequiredException()
            onSecurityCheck?.invoke()
            val html = browser.fetch(url)
            val detail = validateAndParse(html, chapter, url)
            syncBrowserCookies(url)
            cacheChapter(url, detail)
            return detail
        }
        if (result.status !in 200..299) throw NetworkHttpException("https://www.wenku8.net/", result.status)
        val detail = validateAndParse(result.html, chapter, url)
        cacheChapter(url, detail)
        return detail
    }

    private fun cacheKey(url: String): String = "wenku8|${EsjzoneUrls.canonicalPageKey(url)}"

    private fun syncBrowserCookies(url: String) {
        try {
            browser.cookies(url)?.let(jar::importBrowserCookies)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw java.io.IOException("Browser request cancelled", error)
        } catch (_: Exception) {
            // Browser HTML remains usable if optional cookie sharing fails.
        }
    }

    private fun cacheChapter(url: String, detail: DetailedChapter): String =
        ExternalChapterHtml.cacheDocument(detail, url).also {
            PageCache.write(cacheKey(url), it)
            StructuredChapterCache.write(cacheKey(url), it, detail)
        }

    private fun validateAndParse(html: String, chapter: Chapter, url: String): DetailedChapter {
        if (!PageResponsePolicy.validate(200, html, url, kind = PageKind.EXTERNAL_CHAPTER).trusted) {
            throw ExternalChapterParseException()
        }
        return ExternalChapterHtml.parse(html, chapter, url)
    }

    private fun request(url: String): ResponseData {
        val request = Request.Builder().url(url).header("User-Agent", userAgentHeader)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml")
            .get().build()
        val call = client.newCall(request)
        val cancellation = pageRequestCancellation.get()
        cancellation?.attach(call)
        try {
            return call.execute().use { response ->
                if (resolveChapterSource(response.request.url.toString()) != ChapterSource.WENKU8) {
                    throw UnsupportedExternalChapterException()
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
                        response.header("Server"), html)
                )
            }
        } finally {
            cancellation?.detachCall()
        }
    }

    private data class ResponseData(val status: Int, val html: String, val challenge: Boolean)

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) return false
        return true
    }
}
