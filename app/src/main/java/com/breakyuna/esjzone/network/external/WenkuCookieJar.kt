package com.breakyuna.esjzone.network.external

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Kept in its own encrypted store, independent of ESJ sign-in and sign-out. */
internal class WenkuCookieJar(context: Context) : CookieJar {
    private val origin = "https://www.wenku8.net/".toHttpUrl()
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "wenku8_cookies",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val lock = Any()
    // Keep observed URL scopes; getCookie does not expose the original expiry/path.
    private val browserPairs = decodeWenkuBrowserSnapshots(preferences.getString(BROWSER_SNAPSHOTS, null)).toMutableMap()
    private val requestScope = ThreadLocal<String?>()
    fun <T> withinRequest(expectedScope: String? = null, block: () -> T): T {
        val scope = expectedScope ?: cacheScope()
        if (scope != cacheScope()) throw WenkuBrowserSessionClosedException()
        requestScope.set(scope)
        return try { block() } finally { requestScope.remove() }
    }
    fun cacheScope(): String = synchronized(lock) {
        preferences.getString("session_generation", null) ?: java.util.UUID.randomUUID().toString().also {
            preferences.edit().putString("session_generation", it).apply()
        }
    }

    fun clear(): List<Cookie> = synchronized(lock) {
        val cookies = preferences.all.values.filterIsInstance<String>().mapNotNull { Cookie.parse(origin, it) } +
            wenkuBrowserCookieDeletionTargets(browserPairs)
        browserPairs.clear()
        check(preferences.edit().clear().putString("session_generation", java.util.UUID.randomUUID().toString()).commit()) {
            "Unable to clear encrypted Wenku session"
        }
        cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        if (url.host != origin.host || !url.isHttps || url.port != 443) return@synchronized emptyList()
        val now = System.currentTimeMillis()
        val stored = preferences.all.values.filterIsInstance<String>()
            .mapNotNull { Cookie.parse(origin, it) }
        val active = stored.filter { it.expiresAt > now }
        if (active.size != stored.size) {
            stored.filter { it.expiresAt <= now }.forEach { expired ->
                persist(expired)
                browserPairs.entries.forEach { entry ->
                    if (expired.matches(entry.key) && entry.value[expired.name] == expired.value) {
                        entry.setValue(entry.value - expired.name)
                    }
                }
            }
            persistBrowserSnapshots()
        }
        val normal = active.filter { it.matches(url) }
        wenkuBrowserCookiesForRequest(url, browserPairs, normal)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
        if (url.host != origin.host || !url.isHttps || url.port != 443) return@synchronized
        if (requestScope.get()?.let { it != cacheScope() } == true) return@synchronized
        val accepted = cookies.filter { (origin.host == it.domain || origin.host.endsWith(".${it.domain}")) &&
            it.name != "ews_key" && it.name != "ews_token" }
        accepted.forEach { cookie ->
            persist(cookie)
            browserPairs.entries.forEach { entry ->
                if (cookie.matches(entry.key)) {
                    entry.setValue(if (cookie.expiresAt <= System.currentTimeMillis()) entry.value - cookie.name
                        else entry.value + (cookie.name to cookie.value))
                }
            }
        }
        if (accepted.isNotEmpty()) persistBrowserSnapshots()
        // Mirror the real Set-Cookie attributes, never reconstructed browser-pair attributes.
        val scope = cacheScope()
        if (accepted.isNotEmpty()) android.os.Handler(android.os.Looper.getMainLooper()).post {
            synchronized(lock) {
                if (scope == cacheScope()) {
                    val manager = android.webkit.CookieManager.getInstance()
                    var remaining = accepted.size
                    accepted.forEach { cookie ->
                        manager.setCookie(url.toString(), cookie.toString()) {
                            remaining--
                            if (remaining == 0) manager.flush()
                        }
                    }
                }
            }
        }
    }

    private fun persist(cookie: Cookie) {
        val key = "${cookie.domain}|${cookie.path}|${cookie.name}"
        val edit = preferences.edit()
        if (cookie.expiresAt <= System.currentTimeMillis()) edit.remove(key)
        else edit.putString(key, cookie.toString())
        edit.apply()
    }

    private fun persistBrowserSnapshots() {
        check(preferences.edit().putString(BROWSER_SNAPSHOTS, encodeWenkuBrowserSnapshots(browserPairs)).commit()) {
            "Unable to save encrypted Wenku browser session"
        }
    }

    /** Called on main before browser navigation. Never replace newer browser-owned values. */
    fun restoreBrowserCookies(onRestored: () -> Unit) {
        val manager = android.webkit.CookieManager.getInstance()
        val scope = cacheScope()
        val pending = synchronized(lock) {
            val urls = browserPairs.keys + origin
            urls.flatMap { url ->
                val existing = manager.getCookie(url.toString()).orEmpty().split(';')
                    .map { it.trim().substringBefore('=') }.toSet()
                loadForRequest(url).filter { it.name !in existing }.map { url to it }
            }.distinctBy { (_, cookie) -> "${cookie.domain}|${cookie.path}|${cookie.name}" }
        }
        if (pending.isEmpty()) {
            onRestored()
            return
        }
        var remaining = pending.size
        pending.forEach { (url, cookie) ->
            synchronized(lock) {
                if (scope != cacheScope()) {
                    if (--remaining == 0) onRestored()
                } else {
                    manager.setCookie(url.toString(), cookie.toString()) {
                        if (--remaining == 0) {
                            manager.flush()
                            onRestored()
                        }
                    }
                }
            }
        }
    }

    fun importBrowserCookies(raw: String?, sourceUrl: String = origin.toString(), forceNewScope: Boolean = false) = synchronized(lock) {
        val source = sourceUrl.toHttpUrlOrNull()
            ?: return@synchronized
        if (source.host != origin.host || !source.isHttps || source.port != 443 ||
            source.username.isNotEmpty() || source.password.isNotEmpty()) return@synchronized
        val next = raw.orEmpty().split(';').mapNotNull { item ->
            val pair = item.trim().split('=', limit = 2)
            if (pair.size != 2 || pair[0].isBlank() || pair[0].startsWith("ews_")) null
            else pair[0] to pair[1]
        }.toMap()
        val previous = browserPairs[source]
        val changed = if (source == origin) previous.orEmpty() != next || browserPairs.keys.any { it != origin }
            else previous != null && previous != next
        // The root browser snapshot is authoritative for persisted cookies applicable at the root.
        if (source == origin) {
            preferences.all.values.filterIsInstance<String>().mapNotNull { Cookie.parse(origin, it) }
                .filter { it.matches(origin) && it.name !in next }
                .forEach { preferences.edit().remove("${it.domain}|${it.path}|${it.name}").apply() }
        }
        // Merely visiting a new path must not invalidate already bridged detail/catalog caches.
        if (source == origin) browserPairs.clear()
        browserPairs[source] = next
        if (changed || forceNewScope) preferences.edit().putString("session_generation", java.util.UUID.randomUUID().toString()).apply()
        persistBrowserSnapshots()
    }

    private companion object {
        const val BROWSER_SNAPSHOTS = "browser_snapshots_v1"
    }
}

internal fun encodeWenkuBrowserSnapshots(snapshots: Map<HttpUrl, Map<String, String>>): String =
    Gson().toJson(snapshots.mapKeys { it.key.toString() })

internal fun decodeWenkuBrowserSnapshots(json: String?): Map<HttpUrl, Map<String, String>> {
    if (json == null) return emptyMap()
    return runCatching {
        val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
        val stored: Map<String, Map<String, String>> = Gson().fromJson(json, type) ?: emptyMap()
        stored.mapNotNull { (rawUrl, pairs) ->
            val url = rawUrl.toHttpUrlOrNull() ?: return@mapNotNull null
            if (!url.isHttps || url.host != "www.wenku8.net" || url.port != 443 ||
                url.username.isNotEmpty() || url.password.isNotEmpty()) return@mapNotNull null
            url to pairs.filter { (name, value) ->
                !name.startsWith("ews_") && runCatching {
                    Cookie.Builder().hostOnlyDomain(url.host).name(name).value(value).build()
                }.isSuccess
            }
        }.toMap()
    }.getOrDefault(emptyMap())
}

/** getCookie(url) proves applicability only at that URL; a root snapshot also applies site-wide. */
internal fun wenkuBrowserCookiesForRequest(
    url: HttpUrl,
    snapshots: Map<HttpUrl, Map<String, String>>,
    normal: List<Cookie>
): List<Cookie> {
    val pairs = linkedMapOf<String, Cookie>()
    val rootUrl = url.newBuilder().encodedPath("/").query(null).fragment(null).build()
    listOf(rootUrl, url).distinct().forEach { source ->
        val values = snapshots[source] ?: return@forEach
        if (source.scheme != url.scheme || source.host != url.host || source.port != url.port ||
            source.username.isNotEmpty() || source.password.isNotEmpty()) return@forEach
        val root = source.encodedPath == "/" && source.query == null
        if (!root && source != url) return@forEach
        values.forEach { (name, value) ->
            val known = normal.firstOrNull { it.name == name && it.value == value }
            val cookie = known ?: runCatching {
                Cookie.Builder().hostOnlyDomain(source.host).path(source.encodedPath).secure()
                    .name(name).value(value).build()
            }.getOrNull()
            if (cookie != null) pairs[name] = cookie
        }
    }
    val exact = snapshots[url]
    val root = snapshots[rootUrl]
    val retained = normal.filter { cookie ->
        if (exact != null) cookie.name in exact
        else root == null || !cookie.matches(rootUrl) || cookie.name in root
    }
    return retained.filterNot { it.name in pairs } + pairs.values
}

/** Deletion scopes cover every path prefix capable of supplying a captured pair. */
internal fun wenkuBrowserCookieDeletionTargets(snapshots: Map<HttpUrl, Map<String, String>>): List<Cookie> =
    snapshots.flatMap { (source, pairs) ->
        if (!source.isHttps || source.host != "www.wenku8.net" || source.port != 443 ||
            source.username.isNotEmpty() || source.password.isNotEmpty()) return@flatMap emptyList()
        val paths = mutableSetOf("/", source.encodedPath)
        source.encodedPath.forEachIndexed { index, char ->
            if (char == '/' && index > 0) {
                paths += source.encodedPath.substring(0, index)
                paths += source.encodedPath.substring(0, index + 1)
            }
        }
        pairs.keys.flatMap { name ->
            paths.flatMap { path ->
                listOf(null, "www.wenku8.net", "wenku8.net").mapNotNull { cookieDomain ->
                    runCatching {
                        Cookie.Builder().apply {
                            if (cookieDomain == null) hostOnlyDomain(source.host) else domain(cookieDomain)
                        }.path(path).secure().name(name).value("").expiresAt(0).build()
                    }.getOrNull()
                }
            }
        }
    }.distinctBy { "${it.hostOnly}|${it.domain}|${it.path}|${it.name}" }
