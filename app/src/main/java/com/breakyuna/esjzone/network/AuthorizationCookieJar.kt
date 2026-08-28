package com.breakyuna.esjzone.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class AuthorizationCookieJar(private val authorization: Authorization) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val persistentJar = EsjzoneClient.persistentCookieJar
        if (persistentJar != null) {
            // Once the persistent jar is available it is the source of truth.  Falling
            // back to stale legacy values here could resurrect a server-deleted session.
            return persistentJar.loadForRequest(url)
        }
        return legacyCookies(url)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        EsjzoneClient.persistentCookieJar?.saveFromResponse(url, cookies)
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
                .build(),
            Cookie.Builder()
                .domain(normalizedSessionHost)
                .path("/")
                .name("ews_token")
                .value(authorization.ewsToken)
                .build()
        )
    }

}
