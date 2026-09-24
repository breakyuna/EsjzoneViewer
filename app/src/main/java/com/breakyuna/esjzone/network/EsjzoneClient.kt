package com.breakyuna.esjzone.network

import android.content.Context
import java.nio.charset.StandardCharsets
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import com.breakyuna.esjzone.network.external.WenkuChapterClient
import com.breakyuna.esjzone.network.external.WenkuCookieStoreUnavailableException
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

object EsjzoneClient {

    /* Login steps:
     * 1. POST: Url: https://www.esjzone.me/my/login
     *          Payload: { "plxf": "getAuthToken" }
     *          Response: <JinJing>{login_token}</JinJing>
     *
     * 2. POST: Url: https://www.esjzone.me/inc/mem_login.php
     *          Headers: { "Authorization": "{login_token}" }
     *          Payload: { "email": "{email}", "pwd": "{password}", "remember_me": "on" }
     *          Response: !None!
     *          Set-Cookie: { "ews_key": "{ews_key}", "ews_token": "{ews_token}" }
     *
     * 3. Request everything with cookies {ews_key} and {ews_token} for authorization!
     *
     *
     * Logout steps:
     * GET: https://www.esjzone.me/my/logout
     * with cookies {ews_key} and {ews_token}
     *
     * After this, {ews_key} and {ews_token} will expire
     *
     */

    val headers = Headers.Builder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        )
        .build()

    var EMPTY_HTTP_CLIENT = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var sharedHttpClient: OkHttpClient = EMPTY_HTTP_CLIENT

    @Volatile
    internal var persistentCookieJar: PersistentCookieJar? = null
        private set

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightPages = ConcurrentHashMap<String, CompletableFuture<String>>()
    private val cacheEpoch = AtomicLong(0L)
    private val networkPermits = Semaphore(6, true)

    @Volatile
    private var initialized = false

    @Volatile private var wenkuClient: WenkuChapterClient? = null

    /** Initializes the shared connection pool and page cache during app startup. */
    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        persistentCookieJar = PersistentCookieJar(context.applicationContext)
        val wenkuInitialization = runCatching {
            WenkuChapterClient(context.applicationContext, headers["User-Agent"].orEmpty())
        }
        wenkuClient = wenkuInitialization.getOrNull()
        wenkuInitialization.exceptionOrNull()?.let { error ->
            com.breakyuna.esjzone.util.AppLogger.w(
                "EsjzoneClient", "Wenku cookie storage unavailable (${error::class.java.simpleName})"
            )
        }
        PageCache.initialize(context.applicationContext)
        // PageCache owns response persistence. The shared client is intentionally kept
        // without OkHttp's URL-only HTTP cache so one account can never receive another
        // account's authenticated HTML response.
        sharedHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        initialized = true
    }

    /** Builds a cookie-scoped client while retaining the shared connection pool and cache. */
    fun authenticatedClient(authorization: Authorization): OkHttpClient =
        sharedHttpClient.newBuilder()
            .cookieJar(AuthorizationCookieJar(authorization))
            .build()

    fun getWenkuChapter(chapter: Chapter, url: String, forceRefresh: Boolean, allowAutoSolve: Boolean,
                        onSecurityCheck: (() -> Unit)? = null): DetailedChapter =
        (wenkuClient ?: throw WenkuCookieStoreUnavailableException())
            .load(chapter, url, forceRefresh, allowAutoSolve, onSecurityCheck)

    fun importWenkuBrowserCookies(raw: String?): Boolean =
        wenkuClient?.let { runCatching { it.importBrowserCookies(raw) }.isSuccess } ?: false
    fun importWenkuBrowserChapter(chapter: Chapter, url: String, html: String): Boolean =
        wenkuClient?.let { runCatching { it.importBrowserChapter(chapter, url, html) }.getOrDefault(false) }
            ?: false
    fun wenkuUserAgent(): String = wenkuClient?.userAgent() ?: headers["User-Agent"].orEmpty()
    fun closeWenkuBrowserSession() { wenkuClient?.closeBrowserSession() }
    fun wenkuImageClient(): OkHttpClient =
        (wenkuClient ?: throw WenkuCookieStoreUnavailableException()).imageClient()

    /**
     * Download requests stream potentially large responses and therefore receive a
     * longer read/call budget without weakening the bounds on ordinary page requests.
     */
    fun downloadClient(authorization: Authorization): OkHttpClient =
        sharedHttpClient.newBuilder()
            .cookieJar(AuthorizationCookieJar(authorization))
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .build()

    /**
     * Returns fresh cached HTML when available. Expired HTML is returned immediately while
     * one background refresh updates it for the next read. A forced refresh or a true miss
     * waits for the shared request for this account and URL. Error pages and redirects to
     * login are never written; stale HTML remains a fallback for transient failures.
     */
    fun getPage(
        authorization: Authorization,
        url: String,
        maxAgeMillis: Long,
        forceRefresh: Boolean = false,
        pageKind: PageKind = PageKind.GENERIC,
        allowStaleOnError: Boolean = !forceRefresh
    ): String {
        val cacheKey = pageCacheKey(authorization, url)
        val requestEpoch = cacheEpoch.get()
        if (!forceRefresh) {
            PageCache.read(cacheKey, maxAgeMillis)?.let { cached ->
                if (PageResponsePolicy.validate(200, cached, url, kind = pageKind).trusted) {
                    return cached
                }
                // A previous app version could have cached an HTML block/challenge page.
                // Do not keep returning it after the network becomes healthy.
                PageCache.remove(cacheKey)
            }
        }
        val staleCandidate = PageCache.readStale(cacheKey)
        val stalePage = staleCandidate?.let { candidate ->
            if (PageResponsePolicy.validate(200, candidate, url, kind = pageKind).trusted) {
                candidate
            } else {
                PageCache.remove(cacheKey)
                null
            }
        }

        if (!forceRefresh && stalePage != null) {
            refreshScope.launch {
                runCatching {
                    fetchPageCoalesced(
                        authorization,
                        url,
                        cacheKey,
                        stalePage,
                        requestEpoch,
                        pageKind,
                        allowStaleOnError
                    )
                }
            }
            return stalePage
        }

        return fetchPageCoalesced(
            authorization,
            url,
            cacheKey,
            stalePage,
            requestEpoch,
            pageKind,
            allowStaleOnError
        )
    }

    private fun fetchPageCoalesced(
        authorization: Authorization,
        url: String,
        cacheKey: String,
        stalePage: String?,
        requestEpoch: Long,
        pageKind: PageKind,
        allowStaleOnError: Boolean
    ): String {
        val owner = CompletableFuture<String>()
        val existing = inFlightPages.putIfAbsent(cacheKey, owner)
        if (existing != null) {
            return try {
                existing.get(35, TimeUnit.SECONDS)
            } catch (error: TimeoutException) {
                throw NetworkRequestException(
                    url,
                    SocketTimeoutException("Timed out waiting for coalesced request for $url")
                )
            } catch (error: ExecutionException) {
                val cause = error.cause
                if (cause is CancellationException) {
                    // The caller which created the shared request disappeared, but this
                    // caller is still active. Become a fresh owner instead of surfacing a
                    // false network failure to every remaining waiter.
                    return fetchPageCoalesced(
                        authorization, url, cacheKey, stalePage, requestEpoch, pageKind, allowStaleOnError
                    )
                }
                if (cause is Exception) throw cause
                throw error
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw NetworkRequestException(url, error)
            }
        }

        return try {
            val permitAcquired = try {
                networkPermits.tryAcquire(NETWORK_PERMIT_WAIT_SECONDS, TimeUnit.SECONDS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw NetworkRequestException(url, error)
            }
            if (!permitAcquired) {
                throw NetworkRequestException(
                    url,
                    SocketTimeoutException("Timed out waiting for a network permit")
                )
            }
            val cancellation = pageRequestCancellation.get()
            val responseData = try {
                val response = try {
                    val call = authenticatedClient(authorization).newCall(
                        Request.Builder()
                            .url(url)
                            .get()
                            .headers(headers)
                            .build()
                    )
                    cancellation?.attach(call)
                    call.execute()
                } catch (error: java.io.IOException) {
                    throw NetworkRequestException(url, error)
                }
                try {
                    response.use {
                        val body = it.body?.readTextBounded().orEmpty()
                        PageResponseData(
                            statusCode = it.code,
                            body = body,
                            finalUrl = it.request.url.toString(),
                            contentType = it.header("Content-Type")
                        )
                    }
                } catch (error: java.io.IOException) {
                    throw NetworkRequestException(url, error)
                }
            } finally {
                cancellation?.detachCall()
                networkPermits.release()
            }
            if (cancellation?.isCancelled() == true) throw CancellationException("Page request cancelled")
            val validation = PageResponsePolicy.validate(
                statusCode = responseData.statusCode,
                body = responseData.body,
                requestedUrl = url,
                finalUrl = responseData.finalUrl,
                contentType = responseData.contentType,
                kind = pageKind
            )
            if (requestEpoch == cacheEpoch.get() && validation.trusted) {
                NovelDetailCache.remove(cacheKey)
                PageCache.write(cacheKey, responseData.body)
            }
            if (!validation.trusted) {
                val fallback = if (allowStaleOnError) {
                    PageResponsePolicy.selectTrustedBody(
                        validation,
                        responseData.body,
                        stalePage
                    )
                } else {
                    null
                }
                if (fallback != null) {
                    owner.complete(fallback)
                    return fallback
                }
                val error = UntrustedPageException(url, validation)
                owner.completeExceptionally(error)
                throw error
            }
            val result = responseData.body
            owner.complete(result)
            result
        } catch (error: CancellationException) {
            inFlightPages.remove(cacheKey, owner)
            owner.completeExceptionally(error)
            throw error
        } catch (error: Throwable) {
            if (pageRequestCancellation.get()?.isCancelled() == true) {
                val cancelled = CancellationException("Page request cancelled")
                inFlightPages.remove(cacheKey, owner)
                owner.completeExceptionally(cancelled)
                throw cancelled
            }
            // A previously fetched page is preferable to a blank screen during a transient
            // timeout or offline period. The page remains scoped to this account and URL.
            val result = if (error is Exception) stalePage.takeIf { allowStaleOnError } else null
            if (result != null) {
                owner.complete(result)
                result
            } else {
                owner.completeExceptionally(error)
                if (error is Exception) throw error
                throw java.lang.RuntimeException(error)
            }
        } finally {
            inFlightPages.remove(cacheKey, owner)
        }
    }

    fun clearPageCache() {
        cacheEpoch.incrementAndGet()
        inFlightPages.clear()
        NovelDetailCache.clear()
        PageCache.clear()
    }

    fun pageCacheStats(): PageCacheStats = PageCache.stats()

    /** Invalidates one account-scoped page after a successful remote write. */
    internal fun invalidatePage(authorization: Authorization, url: String) {
        val cacheKey = pageCacheKey(authorization, url)
        cacheEpoch.incrementAndGet()
        inFlightPages.remove(cacheKey)
        NovelDetailCache.remove(cacheKey)
        PageCache.remove(cacheKey)
    }

    /** Invalidates only data affected by a favorite toggle. */
    internal fun invalidateFavoriteCache(authorization: Authorization, novelUrl: String) {
        invalidatePage(authorization, EsjzoneUrls.resolve(novelUrl).substringBefore('#'))

        // The site has separate landing URLs for the two favorite orders.
        // Invalidate both first pages so switching order cannot reveal a stale
        // list, while leaving history/profile/home/chapter caches untouched.
        listOf(
            EsjzoneUrls.My.Favorite,
            "${EsjzoneUrls.My.Favorite}/",
            "${EsjzoneUrls.My.Favorite}/new/",
            "${EsjzoneUrls.My.Favorite}/udate/"
        ).forEach { url -> invalidatePage(authorization, url) }
        PageCacheInvalidation.favoritesChanged()
    }

    /** Invalidates the cloud reading-history page without touching other data. */
    internal fun invalidateHistoryCache(authorization: Authorization) {
        invalidatePage(authorization, EsjzoneUrls.My.View)
    }

    /** Returns the persisted session for a host, importing the legacy Room format once. */
    fun restoreAuthorization(host: String, legacy: Authorization? = null): Authorization? {
        val jar = persistentCookieJar ?: return legacy?.takeIf { it.hasCredentials() }
        jar.authorizationFor(host)?.let { return it }
        if (legacy?.hasCredentials() == true && jar.importLegacyAuthorization(host, legacy)) {
            return jar.authorizationFor(host)
        }
        return null
    }

    /** Stores all cookies returned by the login flow without exposing their values to logs. */
    internal fun persistCookies(url: HttpUrl, cookies: List<Cookie>) {
        persistentCookieJar?.saveFromResponse(url, cookies)
    }

    internal fun rotatePageCacheScope(host: String) {
        persistentCookieJar?.rotateCacheScope(host)
    }

    internal fun activateAccountScope(host: String, email: String, ewsKey: String) {
        persistentCookieJar?.activateAccountScope(host, email, ewsKey)
    }

    fun pendingLegacyBookshelfScope(authorization: Authorization): String? =
        persistentCookieJar?.pendingLegacyScopeFor(authorization.domain, authorization.ewsKey)

    fun clearPendingLegacyBookshelfScope(authorization: Authorization) {
        persistentCookieJar?.clearPendingLegacyScope(authorization.domain, authorization.ewsKey)
    }

    internal fun wasAuthorizationVerifiedRecently(
        authorization: Authorization,
        maxAgeMillis: Long
    ): Boolean = persistentCookieJar?.wasVerifiedRecently(authorization, maxAgeMillis) == true

    internal fun markAuthorizationVerified(authorization: Authorization) {
        persistentCookieJar?.markVerified(authorization)
    }

    fun accountScope(authorization: Authorization): String {
        val host = authorization.domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol }
        return if (authorization.hasCredentials()) {
            val accountId = persistentCookieJar?.accountScopeFor(host, authorization.ewsKey) ?: MessageDigest.getInstance("SHA-256")
                .digest(
                    "${authorization.ewsKey}:${authorization.ewsToken}"
                        .toByteArray(StandardCharsets.UTF_8)
                )
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                .take(16)
            "account:$host:$accountId"
        } else {
            "guest:$host"
        }
    }

    internal fun novelDetailCacheKey(authorization: Authorization, url: String): String =
        pageCacheKey(authorization, url)

    internal fun hasCachedPage(authorization: Authorization, url: String, maxAgeMillis: Long): Boolean {
        val cacheKey = pageCacheKey(authorization, url)
        return PageCache.read(cacheKey, maxAgeMillis) != null
    }

    /** Clears only the selected site's session; a null host clears every persisted session. */
    fun clearSession(host: String? = null) {
        // Prevent an old in-flight response from repopulating a cache namespace after
        // logout. The account-scoped files themselves remain safely inaccessible and can
        // still be reclaimed by the normal cache size policy.
        cacheEpoch.incrementAndGet()
        inFlightPages.clear()
        persistentCookieJar?.clear(host)
    }

    /** Builds a client for remote logout that cannot persist response cookies. */
    internal fun logoutClient(authorization: Authorization): OkHttpClient =
        sharedHttpClient.newBuilder()
            .cookieJar(AuthorizationCookieJar(authorization, persistResponses = false))
            .callTimeout(5, TimeUnit.SECONDS)
            .build()

    /** Builds a bounded, isolated client for a background session probe. */
    internal fun authorizationCheckClient(
        authorization: Authorization,
        timeoutMillis: Long
    ): OkHttpClient = sharedHttpClient.newBuilder()
        // A probe must never persist response cookies: a late result from an old
        // account must not rotate or replace the session used by the UI.
        .cookieJar(AuthorizationCookieJar(authorization, persistResponses = false))
        .callTimeout(timeoutMillis.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        .build()

    private fun pageCacheKey(authorization: Authorization, url: String): String {
        val host = url.toHttpUrlOrNull()?.host ?: authorization.domain
        val scope = if (authorization.hasCredentials()) {
            persistentCookieJar?.cacheScopeFor(host) ?: MessageDigest.getInstance("SHA-256")
                .digest(
                    "${authorization.ewsKey}:${authorization.ewsToken}"
                        .toByteArray(StandardCharsets.UTF_8)
                )
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        } else {
            "public"
        }
        return "$scope|$url"
    }

    private data class PageResponseData(
        val statusCode: Int,
        val body: String,
        val finalUrl: String,
        val contentType: String?
    )

    private const val NETWORK_PERMIT_WAIT_SECONDS = 30L
}
