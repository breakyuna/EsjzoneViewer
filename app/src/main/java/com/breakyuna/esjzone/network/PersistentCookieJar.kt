package com.breakyuna.esjzone.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * A small persistent CookieJar for the server-rendered ESJ session.
 *
 * The browser keeps the complete Set-Cookie state.  Keeping the same state here is
 * important because the site can rotate session cookies after a successful request.
 */
internal class PersistentCookieJar(context: Context) : CookieJar {

    /**
     * Non-secret cache metadata remains in ordinary preferences. Session cookies are
     * stored separately with an Android Keystore-backed AES key. There is deliberately
     * no plaintext fallback when the secure store cannot be opened.
     */
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val securePreferences: SharedPreferences? = createSecurePreferences(context)
    private val gson = Gson()
    private val lock = Any()
    private val cookies = mutableListOf<StoredCookie>()

    init {
        migrateLegacyCookies()
        cookies += loadCookies()
    }

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
                    // A WAF/interstitial can emit a broad session-cookie deletion even
                    // though the HTML request itself was never accepted. Do not let an
                    // arbitrary page response destroy ews_*; explicit clearSession() and
                    // the login flow remain the authoritative deletion/rotation paths.
                    if (cookie.name == "ews_key" || cookie.name == "ews_token") continue
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

    /**
     * Returns an opaque, host-scoped cache identity that survives cookie rotation.
     * Session cookies are deliberately excluded: ESJ can rotate them after any
     * response, which previously made every cached page unreachable after restart.
     */
    fun cacheScopeFor(host: String): String = synchronized(lock) {
        val key = cacheScopeKey(host)
        preferences.getString(key, null)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID()
            .toString()
            .also { preferences.edit().putString(key, it).commit() }
    }

    /** Starts a new cache namespace when an explicit login changes account state. */
    fun rotateCacheScope(host: String) = synchronized(lock) {
        preferences.edit()
            .putString(cacheScopeKey(host), UUID.randomUUID().toString())
            .commit()
        Unit
    }

    fun wasVerifiedRecently(authorization: Authorization, maxAgeMillis: Long): Boolean = synchronized(lock) {
        val verifiedAt = preferences.getLong(verificationKey(authorization), 0L)
        val age = System.currentTimeMillis() - verifiedAt
        verifiedAt > 0L && age in 0..maxAgeMillis
    }

    fun markVerified(authorization: Authorization) = synchronized(lock) {
        preferences.edit()
            .putLong(verificationKey(authorization), System.currentTimeMillis())
            .apply()
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
                val editor = preferences.edit()
                preferences.all.keys
                    .filter {
                        it.startsWith(CACHE_SCOPE_PREFIX) ||
                            it.startsWith(VERIFIED_AT_PREFIX)
                    }
                    .forEach(editor::remove)
                editor.commit()
            } else {
                val normalizedHost = host.trim().lowercase().removePrefix("www.")
                cookies.removeAll {
                    domainMatchesHost(it.domain, normalizedHost) ||
                        domainMatchesHost(it.domain, "www.$normalizedHost")
                }
                val editor = preferences.edit()
                    .remove(cacheScopeKey(host))
                    // Remove both the current session-scoped keys and the
                    // pre-session-scoped key used by older versions.
                    .remove(legacyVerificationKey(host))
                preferences.all.keys
                    .filter { it.startsWith(verificationPrefix(host)) }
                    .forEach(editor::remove)
                editor.commit()
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
            .secure()
            .build()
    }

    private fun removeExpiredCookies(): Boolean {
        val now = System.currentTimeMillis()
        return cookies.removeAll { it.expiresAt <= now }
    }

    private fun loadCookies(): MutableList<StoredCookie> {
        val json = runCatching { securePreferences?.getString(COOKIES, null) }
            .getOrNull()
            ?: return mutableListOf()
        return parseCookies(json)
    }

    private fun parseCookies(json: String): MutableList<StoredCookie> {
        return try {
            gson.fromJson(json, Array<StoredCookie>::class.java)?.toMutableList()
                ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun persistLocked() {
        runCatching {
            securePreferences?.edit()?.putString(COOKIES, gson.toJson(cookies))?.apply()
        }.onFailure {
            // Keep the current process usable, but never fall back to plaintext storage.
            Log.e(TAG, "Unable to persist the secure session", it)
        }
    }

    /** Migrates the old plaintext cookie JSON once, then removes that copy. */
    private fun migrateLegacyCookies() = synchronized(lock) {
        val legacyJson = preferences.getString(COOKIES, null)
        val secure = securePreferences
        runCatching {
            if (secure != null && !secure.contains(COOKIES) && !legacyJson.isNullOrBlank()) {
                val migrated = parseCookies(legacyJson)
                if (migrated.isNotEmpty()) {
                    secure.edit().putString(COOKIES, gson.toJson(migrated)).commit()
                }
            }
        }.onFailure {
            Log.e(TAG, "Unable to migrate the legacy session securely", it)
        }
        // Never retain a plaintext session copy, including when secure storage was
        // unavailable; failing closed is safer than allowing a future backup to copy it.
        if (legacyJson != null) preferences.edit().remove(COOKIES).commit()
    }

    private fun domainMatchesHost(domain: String, host: String): Boolean {
        val normalizedDomain = domain.lowercase().removePrefix(".")
        return host == normalizedDomain || host.endsWith(".$normalizedDomain")
    }

    private fun normalizedHost(host: String): String =
        host.trim().lowercase().removePrefix("www.")

    private fun cacheScopeKey(host: String): String =
        CACHE_SCOPE_PREFIX + normalizedHost(host)

    private fun verificationKey(authorization: Authorization): String =
        verificationPrefix(authorization.domain) + sessionDigest(authorization)

    private fun verificationPrefix(host: String): String =
        VERIFIED_AT_PREFIX + normalizedHost(host) + "_"

    private fun legacyVerificationKey(host: String): String =
        VERIFIED_AT_PREFIX + normalizedHost(host)

    private fun sessionDigest(authorization: Authorization): String =
        MessageDigest.getInstance("SHA-256")
            .digest(
                "${authorization.ewsKey}:${authorization.ewsToken}"
                    .toByteArray(StandardCharsets.UTF_8)
            )
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

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
                if (secure || name in SESSION_COOKIE_NAMES) builder.secure()
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
        const val CACHE_SCOPE_PREFIX = "cache_scope_"
        const val VERIFIED_AT_PREFIX = "verified_at_"
        val SESSION_COOKIE_NAMES = setOf("ews_key", "ews_token")

        private fun createSecurePreferences(context: Context): SharedPreferences? =
            runCatching<SharedPreferences> {
                val appContext: Context = context.applicationContext
                val masterKey: MasterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    SECURE_PREFERENCES,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.onFailure { error: Throwable ->
                Log.e(TAG, "Secure session storage is unavailable; session persistence disabled", error)
            }.getOrNull()

        const val SECURE_PREFERENCES = "esj_session_secure"
        const val TAG = "PersistentCookieJar"
    }
}
