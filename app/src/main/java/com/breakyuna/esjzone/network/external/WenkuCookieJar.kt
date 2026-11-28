package com.breakyuna.esjzone.network.external

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Stores only Cloudflare cookies, independently of site sign-in and sign-out. */
internal class WenkuCookieJar internal constructor(
    private val preferences: SharedPreferences
) : CookieJar {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    )

    private val origin = "https://www.wenku8.net/".toHttpUrl()
    private val lock = Any()

    init {
        synchronized(lock) {
            if (preferences.contains(KEY_KEYSET_PREFERENCES) ||
                preferences.contains(VALUE_KEYSET_PREFERENCES)
            ) {
                preferences.edit().clear().apply()
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        if (url.host != origin.host || !url.isHttps) return@synchronized emptyList()
        val now = System.currentTimeMillis()
        val entries = preferences.all
        val stored = entries.mapNotNull { (key, value) ->
            if (!isCookieKey(key)) return@mapNotNull null
            val raw = value as? String ?: return@mapNotNull null
            Cookie.parse(origin, raw)?.takeIf { it.name in CLOUDFLARE_COOKIE_NAMES }
        }
        val active = stored.filter { it.expiresAt > now }
        if (active.size != entries.size) {
            val editor = preferences.edit().clear()
            active.forEach { editor.putString(cookieKey(it), it.toString()) }
            editor.apply()
        }
        active.filter { it.matches(url) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
        if (url.host != origin.host || !url.isHttps) return@synchronized
        cookies.filter { (origin.host == it.domain || origin.host.endsWith(".${it.domain}")) &&
            it.name in CLOUDFLARE_COOKIE_NAMES }
            .forEach(::persist)
    }

    private fun persist(cookie: Cookie) {
        val key = cookieKey(cookie)
        val edit = preferences.edit()
        if (cookie.expiresAt <= System.currentTimeMillis()) edit.remove(key)
        else edit.putString(key, cookie.toString())
        edit.apply()
    }

    fun importBrowserCookies(raw: String?) {
        if (raw.isNullOrBlank()) return
        val cookies = raw.split(';').mapNotNull { item ->
            val parts = item.trim().split('=', limit = 2)
            if (parts.size != 2 || parts[0] !in CLOUDFLARE_COOKIE_NAMES) null
            else runCatching {
                Cookie.Builder().hostOnlyDomain(origin.host).path("/").secure().httpOnly()
                    .name(parts[0]).value(parts[1]).expiresAt(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
                    .build()
            }.getOrNull()
        }
        saveFromResponse(origin, cookies)
    }

    private companion object {
        const val PREFERENCES_NAME = "wenku8_cookies"
        const val KEY_KEYSET_PREFERENCES = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        const val VALUE_KEYSET_PREFERENCES = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
        val CLOUDFLARE_COOKIE_NAMES = setOf("cf_clearance", "__cf_bm", "_cfuvid")

        fun cookieKey(cookie: Cookie): String = "${cookie.domain}|${cookie.path}|${cookie.name}"

        fun isCookieKey(key: String): Boolean {
            val parts = key.split('|', limit = 3)
            return parts.size == 3 && parts[0].isNotBlank() && parts[1].startsWith("/") && parts[2].isNotBlank()
        }
    }
}
