package com.breakyuna.esjzone.network.external

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Kept in its own encrypted store, independent of ESJ sign-in and sign-out. */
internal class WenkuCookieJar(context: Context) : CookieJar {
    private val origin = "https://www.wenku8.net/".toHttpUrl()
    private fun createPreferences(context: Context) = EncryptedSharedPreferences.create(
        context.applicationContext,
        "wenku8_cookies",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val preferences = try {
        createPreferences(context)
    } catch (error: Exception) {
        if (error !is java.security.GeneralSecurityException && error !is java.io.IOException &&
            error !is SecurityException) throw error
        // A restored ciphertext can outlive its device-bound key. Reset this session only.
        check(context.applicationContext.deleteSharedPreferences("wenku8_cookies")) {
            "Unable to reset Wenku session storage"
        }
        createPreferences(context)
    }
    private val lock = Any()

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        if (url.host != origin.host || !url.isHttps) return@synchronized emptyList()
        val now = System.currentTimeMillis()
        val stored = preferences.all.values.filterIsInstance<String>()
            .mapNotNull { Cookie.parse(origin, it) }
        val active = stored.filter { it.expiresAt > now }
        if (active.size != stored.size) {
            preferences.edit().clear().apply()
            active.forEach(::persist)
        }
        active.filter { it.matches(url) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
        if (url.host != origin.host || !url.isHttps) return@synchronized
        cookies.filter { (origin.host == it.domain || origin.host.endsWith(".${it.domain}")) &&
            it.name != "ews_key" && it.name != "ews_token" }
            .forEach(::persist)
    }

    private fun persist(cookie: Cookie) {
        val key = "${cookie.domain}|${cookie.path}|${cookie.name}"
        val edit = preferences.edit()
        if (cookie.expiresAt <= System.currentTimeMillis()) edit.remove(key)
        else edit.putString(key, cookie.toString())
        edit.apply()
    }

    fun importBrowserCookies(raw: String?) {
        if (raw.isNullOrBlank()) return
        val cookies = raw.split(';').mapNotNull { item ->
            val parts = item.trim().split('=', limit = 2)
            if (parts.size != 2 || parts[0].isBlank() || parts[0].startsWith("ews_")) null
            else runCatching {
                Cookie.Builder().hostOnlyDomain(origin.host).path("/").secure().httpOnly()
                    .name(parts[0]).value(parts[1]).expiresAt(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
                    .build()
            }.getOrNull()
        }
        saveFromResponse(origin, cookies)
    }
}
