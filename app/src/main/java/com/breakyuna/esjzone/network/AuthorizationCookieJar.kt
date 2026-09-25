package com.breakyuna.esjzone.network

import com.breakyuna.esjzone.util.AppLogger
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class AuthorizationCookieJar(
    private val authorization: Authorization,
    private val persistResponses: Boolean = true,
    private val deferResponses: Boolean = false
) : CookieJar {

    private val persistentJar = EsjzoneClient.persistentCookieJar
    private val sessionEpoch = persistentJar?.sessionEpoch()
    private val pendingResponses = mutableListOf<Pair<HttpUrl, List<Cookie>>>()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!authorization.hasCredentials()) return emptyList<Cookie>().also { logSession(url, it, "no_authorization") }
        if (!isAuthorizationHost(url)) return emptyList<Cookie>().also { logSession(url, it, "other_host") }
        if (persistentJar != null) {
            if (!persistentJar.belongsToActiveAccount(authorization)) {
                return emptyList<Cookie>().also { logSession(url, it, "other_account") }
            }
            if (!persistentJar.isActiveSession(url.host)) {
                return emptyList<Cookie>().also { logSession(url, it, "no_site_session") }
            }
            if (sessionEpoch != persistentJar.sessionEpoch()) {
                return emptyList<Cookie>().also { logSession(url, it, "stale_session_generation") }
            }
            // Once the persistent jar is available it is the source of truth.  Falling
            // back to stale legacy values here could resurrect a server-deleted session.
            val stored = persistentJar.loadForRequest(url, sessionEpoch)
            val result = if (deferResponses) mergePending(url, stored) else stored
            logSession(url, result, "active")
            return result
        }
        return legacyCookies(url).also { logSession(url, it, "legacy") }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!authorization.hasCredentials()) return
        if (!isAuthorizationHost(url)) return
        if (persistResponses && persistentJar?.belongsToActiveAccount(authorization) == true &&
            persistentJar.isActiveSession(url.host)) {
            if (deferResponses) {
                synchronized(pendingResponses) { pendingResponses += url to cookies.toList() }
            } else {
                persistentJar.saveFromResponse(url, cookies, sessionEpoch)
            }
        }
    }

    /** Commit rotations only after the caller validates the complete HTML response. */
    fun commitDeferredResponses() {
        if (!deferResponses || !persistResponses) return
        val jar = persistentJar ?: return
        if (!jar.belongsToActiveAccount(authorization)) return
        val responses = synchronized(pendingResponses) {
            pendingResponses.toList().also { pendingResponses.clear() }
        }
        responses.forEach { (url, cookies) ->
            if (isAuthorizationHost(url) && jar.isActiveSession(url.host)) {
                jar.saveFromResponse(url, cookies, sessionEpoch)
            }
        }
    }

    private fun mergePending(url: HttpUrl, stored: List<Cookie>): List<Cookie> {
        val merged = stored.toMutableList()
        val now = System.currentTimeMillis()
        synchronized(pendingResponses) {
            pendingResponses.forEach { (_, cookies) ->
                cookies.forEach { cookie ->
                    merged.removeAll {
                        it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
                    }
                    if (cookie.expiresAt > now && cookie.matches(url)) merged += cookie
                }
            }
        }
        return merged
    }

    private fun logSession(url: HttpUrl, cookies: List<Cookie>, reason: String) {
        val route = when (url.encodedPath) {
            "/my/profile" -> "profile"
            "/my/favorite/udate/" -> "favorite"
            else -> return
        }
        val hasKey = cookies.any { it.name == "ews_key" && it.value.isNotBlank() }
        val hasToken = cookies.any { it.name == "ews_token" && it.value.isNotBlank() }
        AppLogger.i("AuthorizationCookieJar", "$route session cookies: key=$hasKey, token=$hasToken, state=$reason")
    }

    private fun isAuthorizationHost(url: HttpUrl): Boolean {
        if (!url.isHttps) return false
        val sessionHost = authorization.domain.trim().lowercase()
            .ifBlank { EsjzoneUrls.BaseWithoutProtocol.lowercase() }
            .removePrefix("www.")
        return url.host.lowercase().removePrefix("www.") == sessionHost
    }

    private fun legacyCookies(url: HttpUrl): List<Cookie> {
        if (!authorization.hasCredentials()) return emptyList()
        val sessionHost = authorization.domain.trim().lowercase()
            .ifBlank { EsjzoneUrls.BaseWithoutProtocol.lowercase() }
        val normalizedSessionHost = sessionHost.removePrefix("www.")
        val normalizedRequestHost = url.host.lowercase().removePrefix("www.")
        if (normalizedSessionHost != normalizedRequestHost) return emptyList()

        return listOf(
            Cookie.Builder()
                .domain(normalizedSessionHost)
                .path("/")
                .name("ews_key")
                .value(authorization.ewsKey)
                .secure()
                .build(),
            Cookie.Builder()
                .domain(normalizedSessionHost)
                .path("/")
                .name("ews_token")
                .value(authorization.ewsToken)
                .secure()
                .build()
        )
    }

}
