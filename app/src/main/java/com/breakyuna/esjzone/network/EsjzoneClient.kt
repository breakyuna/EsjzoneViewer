package com.breakyuna.esjzone.network

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong
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

    var EMPTY_HTTP_CLIENT = OkHttpClient()

    @Volatile
    private var sharedHttpClient: OkHttpClient = OkHttpClient()

    @Volatile
    internal var persistentCookieJar: PersistentCookieJar? = null
        private set

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightPages = ConcurrentHashMap<String, CompletableFuture<String>>()
    private val cacheEpoch = AtomicLong(0L)
    private val networkPermits = Semaphore(6, true)

    @Volatile
    private var initialized = false

    /** Initializes the shared connection pool and page cache during app startup. */
    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        persistentCookieJar = PersistentCookieJar(context.applicationContext)
        PageCache.initialize(context.applicationContext)
        // PageCache owns response persistence. The shared client is intentionally kept
        // without OkHttp's URL-only HTTP cache so one account can never receive another
        // account's authenticated HTML response.
        sharedHttpClient = OkHttpClient.Builder().build()
        initialized = true
    }

    /** Builds a cookie-scoped client while retaining the shared connection pool and cache. */
    fun authenticatedClient(authorization: Authorization): OkHttpClient =
        sharedHttpClient.newBuilder()
            .cookieJar(AuthorizationCookieJar(authorization))
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
        pageKind: PageKind = PageKind.GENERIC
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
                        pageKind
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
            pageKind
        )
    }

    private fun fetchPageCoalesced(
        authorization: Authorization,
        url: String,
        cacheKey: String,
        stalePage: String?,
        requestEpoch: Long,
        pageKind: PageKind
    ): String {
        val owner = CompletableFuture<String>()
        val existing = inFlightPages.putIfAbsent(cacheKey, owner)
        if (existing != null) {
            return try {
                existing.get()
            } catch (error: ExecutionException) {
                val cause = error.cause
                if (cause is Exception) throw cause
                throw error
            }
        }

        return try {
            networkPermits.acquire()
            val responseData = try {
                val response = authenticatedClient(authorization).newCall(
                    Request.Builder()
                        .url(url)
                        .get()
                        .headers(headers)
                        .build()
                ).execute()
                response.use {
                    val body = it.body?.string().orEmpty()
                    PageResponseData(
                        statusCode = it.code,
                        body = body,
                        finalUrl = it.request.url.toString(),
                        contentType = it.header("Content-Type")
                    )
                }
            } finally {
                networkPermits.release()
            }
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
                val fallback = PageResponsePolicy.selectTrustedBody(
                    validation,
                    responseData.body,
                    stalePage
                )
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
            owner.completeExceptionally(error)
            throw error
        } catch (error: Exception) {
            // A previously fetched page is preferable to a blank screen during a transient
            // timeout or offline period. The page remains scoped to this account and URL.
            val result = stalePage
            if (result != null) {
                owner.complete(result)
                result
            } else {
                owner.completeExceptionally(error)
                throw error
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

    internal fun wasAuthorizationVerifiedRecently(host: String, maxAgeMillis: Long): Boolean =
        persistentCookieJar?.wasVerifiedRecently(host, maxAgeMillis) == true

    internal fun markAuthorizationVerified(host: String) {
        persistentCookieJar?.markVerified(host)
    }

    internal fun novelDetailCacheKey(authorization: Authorization, url: String): String =
        pageCacheKey(authorization, url)

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
            .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    private fun pageCacheKey(authorization: Authorization, url: String): String {
        val host = url.toHttpUrlOrNull()?.host ?: authorization.domain
        val scope = persistentCookieJar?.cacheScopeFor(host) ?: if (authorization.hasCredentials()) {
            // Use a one-way digest so credentials never appear in cache file names.
            MessageDigest.getInstance("SHA-256")
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
}
