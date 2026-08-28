package com.breakyuna.esjzone.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * A small persistent CookieJar for the server-rendered ESJ session.
 *
 * The browser keeps the complete Set-Cookie state.  Keeping the same state here is
 * important because the site can rotate session cookies after a successful request.
 */
internal class PersistentCookieJar(context: Context) : CookieJar {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()
    private val cookies = loadCookies()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val changed = removeExpiredCookies()
            if (changed) persistLocked()
            return cookies.mapNotNull { it.toCookie() }.filter { it.matches(url) }
        }
    }

    override fun saveFromResponse(url: HttpUrl, responseCookies: List<Cookie>) {
        if (responseCookies.isEmpty()) return
        synchronized(lock) {
            removeExpiredCookies()
            for (cookie in responseCookies) {
                val index = cookies.indexOfFirst { it.sameIdentity(cookie) }
                if (cookie.expiresAt <= System.currentTimeMillis()) {
                    if (index >= 0) cookies.removeAt(index)
                } else {
                    val stored = StoredCookie.from(cookie)
                    if (index >= 0) {
                        cookies[index] = stored
                    } else {
                        cookies += stored
                    }
                }
            }
            persistLocked()
        }
    }

    fun authorizationFor(host: String): Authorization? {
        val url = runCatching {
            HttpUrl.Builder()
                .scheme("https")
                .host(host)
                .build()
        }.getOrNull() ?: return null
        val matching = loadForRequest(url)
        val key = matching.firstOrNull { it.name == "ews_key" }?.value
        val token = matching.firstOrNull { it.name == "ews_token" }?.value
        return if (!key.isNullOrBlank() && !token.isNullOrBlank()) {
            Authorization(key, token, host)
        } else {
            null
        }
    }

    /** Imports the two-cookie format written by older app versions. */
    fun importLegacyAuthorization(host: String, authorization: Authorization): Boolean {
        if (!authorization.hasCredentials()) return false
        val migrationHost = normalizedHost(host)
        val url = runCatching {
            HttpUrl.Builder()
                .scheme("https")
                .host(host)
                .build()
        }.getOrNull() ?: return false
        synchronized(lock) {
            val migratedHosts = preferences.getStringSet(LEGACY_MIGRATED_HOSTS, emptySet()).orEmpty()
            if (migrationHost in migratedHosts) return false
            saveFromResponse(
                url,
                listOf(
                    legacyCookie(host, "ews_key", authorization.ewsKey),
                    legacyCookie(host, "ews_token", authorization.ewsToken)
                )
            )
            preferences.edit()
                .putStringSet(LEGACY_MIGRATED_HOSTS, migratedHosts + migrationHost)
                .apply()
            return true
        }
    }

    fun clear(host: String? = null) {
        synchronized(lock) {
            if (host.isNullOrBlank()) {
                cookies.clear()
            } else {
                val normalizedHost = host.trim().lowercase().removePrefix("www.")
                cookies.removeAll {
                    domainMatchesHost(it.domain, normalizedHost) ||
                        domainMatchesHost(it.domain, "www.$normalizedHost")
                }
            }
            persistLocked()
        }
    }

    private fun legacyCookie(host: String, name: String, value: String): Cookie {
        val normalizedHost = host.trim().lowercase().removePrefix("www.")
        return Cookie.Builder()
            .domain(normalizedHost)
            .path("/")
            .name(name)
            .value(value)
            .build()
    }

    private fun removeExpiredCookies(): Boolean {
        val now = System.currentTimeMillis()
        return cookies.removeAll { it.expiresAt <= now }
    }

    private fun loadCookies(): MutableList<StoredCookie> {
        val json = preferences.getString(COOKIES, null) ?: return mutableListOf()
        return try {
            gson.fromJson(json, Array<StoredCookie>::class.java)?.toMutableList()
                ?: mutableListOf()
        } catch (_: JsonSyntaxException) {
            mutableListOf()
        }
    }

    private fun persistLocked() {
        preferences.edit().putString(COOKIES, gson.toJson(cookies)).apply()
    }

    private fun domainMatchesHost(domain: String, host: String): Boolean {
        val normalizedDomain = domain.lowercase().removePrefix(".")
        return host == normalizedDomain || host.endsWith(".$normalizedDomain")
    }

    private fun normalizedHost(host: String): String =
        host.trim().lowercase().removePrefix("www.")

    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean
    ) {
        fun toCookie(): Cookie? {
            return runCatching {
                val builder = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .path(path)
                if (hostOnly) {
                    builder.hostOnlyDomain(domain)
                } else {
                    builder.domain(domain)
                }
                if (expiresAt != Long.MAX_VALUE) builder.expiresAt(expiresAt)
                if (secure) builder.secure()
                if (httpOnly) builder.httpOnly()
                builder.build()
            }.getOrNull()
        }

        fun sameIdentity(cookie: Cookie): Boolean =
            name == cookie.name &&
                domain.equals(cookie.domain, ignoreCase = true) &&
                path == cookie.path

        companion object {
            fun from(cookie: Cookie): StoredCookie = StoredCookie(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain,
                path = cookie.path,
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                hostOnly = cookie.hostOnly
            )
        }
    }

    private companion object {
        const val PREFERENCES = "esj_session"
        const val COOKIES = "cookies"
        const val LEGACY_MIGRATED_HOSTS = "legacy_migrated_hosts"
    }
}
