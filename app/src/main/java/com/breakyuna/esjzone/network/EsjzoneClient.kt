package com.breakyuna.esjzone.network

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import okhttp3.Headers
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

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

    /** Initializes the shared connection pool and page cache during app startup. */
    fun initialize(context: Context) {
        persistentCookieJar = PersistentCookieJar(context.applicationContext)
        PageCache.initialize(context.applicationContext)
        // PageCache owns response persistence. The shared client is intentionally kept
        // without OkHttp's URL-only HTTP cache so one account can never receive another
        // account's authenticated HTML response.
        sharedHttpClient = OkHttpClient.Builder().build()
    }

    /** Builds a cookie-scoped client while retaining the shared connection pool and cache. */
    fun authenticatedClient(authorization: Authorization): OkHttpClient =
        sharedHttpClient.newBuilder()
            .cookieJar(AuthorizationCookieJar(authorization))
            .build()

    /**
     * Returns fresh cached HTML when available, otherwise fetches and stores a successful
     * page. Error pages and redirects to login are never written to the page cache.
     */
    fun getPage(
        authorization: Authorization,
        url: String,
        maxAgeMillis: Long
    ): String {
        val cacheKey = pageCacheKey(authorization, url)
        PageCache.read(cacheKey, maxAgeMillis)?.let { return it }
        val stalePage = PageCache.readStale(cacheKey)

        return try {
            val response = authenticatedClient(authorization).newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .headers(headers)
                    .build()
            ).execute()
            val responseCode = response.code
            val body = response.use { it.body?.string().orEmpty() }
            if (responseCode in 200..299 && body.isNotBlank() && !looksLikeLoginPage(body)) {
                PageCache.write(cacheKey, body)
            }
            if (responseCode in 500..599 && stalePage != null) stalePage else body
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A previously fetched page is preferable to a blank screen during a transient
            // timeout or offline period. The page remains scoped to this account and URL.
            stalePage ?: throw error
        }
    }

    fun clearPageCache() {
        PageCache.clear()
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

    /** Clears only the selected site's session; a null host clears every persisted session. */
    fun clearSession(host: String? = null) {
        persistentCookieJar?.clear(host)
    }

    private fun pageCacheKey(authorization: Authorization, url: String): String {
        val scope = if (authorization.hasCredentials()) {
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

    private fun looksLikeLoginPage(body: String): Boolean {
        val document = body.lowercase()
        val hasPasswordField = Regex("name\\s*=\\s*['\"]pwd['\"]").containsMatchIn(document)
        return hasPasswordField &&
            (document.contains("login-box") || document.contains("/my/login"))
    }

}
