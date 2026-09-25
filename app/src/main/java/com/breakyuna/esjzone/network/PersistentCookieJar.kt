package com.breakyuna.esjzone.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.util.AppLogger
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
import java.util.Locale

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
    private var epoch = 0L
    private var memoryIdentity: String? = null
    private var memoryEmailDigest: String? = null
    private val memoryActiveHosts = mutableSetOf<String>()

    fun sessionEpoch(): Long = synchronized(lock) { epoch }

    init {
        migrateLegacyCookies()
        cookies += loadCookies()
        val restoredHosts = SettingsDefaults.DOMAINS.filter { host -> authorizationFor(host) != null }
        AppLogger.i(
            TAG,
            "Session storage restored: secure=${securePreferences != null}, " +
                "siteSessions=${restoredHosts.joinToString(",").ifBlank { "none" }}"
        )
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = loadForRequest(url, expectedEpoch = null)

    fun loadForRequest(url: HttpUrl, expectedEpoch: Long?): List<Cookie> {
        synchronized(lock) {
            if (expectedEpoch != null && expectedEpoch != epoch) return emptyList()
            val changed = removeExpiredCookies()
            if (changed) persistLocked()
            return cookies.mapNotNull { it.toCookie() }.filter { it.matches(url) }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) =
        saveFromResponse(url, cookies, expectedEpoch = null)

    /** A successful explicit login replaces every cookie from the old account on this host. */
    fun replaceHostCookies(url: HttpUrl, responseCookies: List<Cookie>) = synchronized(lock) {
        val host = url.host.lowercase().removePrefix("www.")
        cookies.removeAll {
            domainMatchesHost(it.domain, host) || domainMatchesHost(it.domain, "www.$host")
        }
        saveFromResponse(url, responseCookies, expectedEpoch = null)
    }

    fun saveFromResponse(url: HttpUrl, responseCookies: List<Cookie>, expectedEpoch: Long?) {
        if (responseCookies.isEmpty()) return
        synchronized(lock) {
            if (expectedEpoch != null && expectedEpoch != epoch) return
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
            val activeIdentity = securePreferences?.getString(activeAccountScopeKey(url.host), null)
            val rotatedKey = responseCookies.firstOrNull {
                it.name == "ews_key" && it.expiresAt > System.currentTimeMillis()
            }?.value
            if (!activeIdentity.isNullOrBlank() && !rotatedKey.isNullOrBlank()) {
                securePreferences?.edit()
                    ?.putString(accountKeyMappingKey(url.host, rotatedKey), activeIdentity)
                    ?.apply()
            }
            persistLocked(durable = responseCookies.any {
                it.name in SESSION_COOKIE_NAMES && it.expiresAt > System.currentTimeMillis()
            })
        }
    }

    fun authorizationFor(host: String): Authorization? {
        val url = runCatching {
            HttpUrl.Builder()
                .scheme("https")
                .host(host)
                .addPathSegments("my/profile")
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

    /** Upgrades the selected legacy session to the one active account. */
    fun establishActiveAccount(host: String) = synchronized(lock) {
        val secure = securePreferences
        val authorization = authorizationFor(host)
        if (secure == null) {
            if (memoryIdentity == null && authorization != null) {
                memoryIdentity = UUID.randomUUID().toString()
                memoryActiveHosts += normalizedHost(host)
            }
            return@synchronized
        }
        if (secure.getString(ACTIVE_SHARED_SCOPE, null).isNullOrBlank() &&
            authorization != null) {
            val identity = secure.getString(activeAccountScopeKey(host), null)
                ?.takeIf(String::isNotBlank) ?: cacheScopeFor(host)
            secure.edit().putString(ACTIVE_SHARED_SCOPE, identity)
                .putString(activeAccountScopeKey(host), identity)
                .putString(accountKeyMappingKey(host, authorization.ewsKey), identity)
                .putString(LEGACY_PRIMARY_HOST, normalizedHost(host))
                .putString(LEGACY_PRIMARY_IDENTITY, identity).commit()
        } else if (authorization != null) {
            val shared = secure.getString(ACTIVE_SHARED_SCOPE, null)
            if (!shared.isNullOrBlank() &&
                secure.getString(activeAccountScopeKey(host), null) == shared &&
                secure.getString(accountKeyMappingKey(host, authorization.ewsKey), null) != shared
            ) {
                // A restored key may have been rotated in a previous process before
                // its account mapping reached disk. Keep the page's Authorization
                // associated with this account after the next cookie rotation.
                val committed = secure.edit()
                    .putString(accountKeyMappingKey(host, authorization.ewsKey), shared)
                    .commit()
                AppLogger.i(TAG, "Restored session account mapping: host=${normalizedHost(host)}, committed=$committed")
            }
        }
    }

    fun mayMigrateLegacyDomainScope(host: String): Boolean = synchronized(lock) {
        val secure = securePreferences ?: return@synchronized false
        secure.getString(LEGACY_PRIMARY_HOST, null) == normalizedHost(host) &&
            secure.getString(LEGACY_PRIMARY_IDENTITY, null) ==
                secure.getString(ACTIVE_SHARED_SCOPE, null)
    }

    fun legacyPageCacheKey(url: HttpUrl): String? = synchronized(lock) {
        val secure = securePreferences ?: return@synchronized null
        val legacyHost = secure.getString(LEGACY_PRIMARY_HOST, null)
            ?: return@synchronized null
        if (!mayMigrateLegacyDomainScope(legacyHost)) return@synchronized null
        val fullHost = SettingsDefaults.DOMAINS.firstOrNull {
            normalizedHost(it) == legacyHost
        } ?: return@synchronized null
        val legacyUrl = url.newBuilder().host(fullHost).build().toString()
        "${cacheScopeFor(fullHost)}|$legacyUrl"
    }

    fun isActiveSession(host: String): Boolean = synchronized(lock) {
        val secure = securePreferences
            ?: return@synchronized normalizedHost(host) in memoryActiveHosts
        val shared = secure.getString(ACTIVE_SHARED_SCOPE, null) ?: return@synchronized false
        secure.getString(activeAccountScopeKey(host), null) == shared
    }

    /** Reject work started with an authorization from a different active account. */
    fun belongsToActiveAccount(authorization: Authorization): Boolean = synchronized(lock) {
        if (!authorization.hasCredentials()) return@synchronized false
        val active = activeAccountIdentity() ?: return@synchronized false
        SettingsDefaults.DOMAINS.any { host ->
            accountScopeFor(host, authorization.ewsKey) == active
        }
    }

    fun activeAccountIdentity(): String? = synchronized(lock) {
        securePreferences?.getString(ACTIVE_SHARED_SCOPE, null)?.takeIf(String::isNotBlank)
            ?: memoryIdentity
    }

    /** null means an upgraded session has no known email mapping yet. */
    fun matchesActiveAccountEmail(email: String): Boolean? = synchronized(lock) {
        val activeDigest = securePreferences?.getString(ACTIVE_EMAIL_DIGEST, null)
            ?: memoryEmailDigest ?: return@synchronized null
        matchesKnownAccountEmail(activeDigest, email)
    }

    fun legacyAccountScopeFor(host: String, ewsKey: String): String? = synchronized(lock) {
        val identity = securePreferences?.getString(accountKeyMappingKey(host, ewsKey), null)
            ?: securePreferences?.getString(activeAccountScopeKey(host), null)
                ?.takeIf { authorizationFor(host)?.ewsKey == ewsKey }
            ?: return@synchronized null
        "account:$host:$identity"
    }

    fun matchingEmailLegacyScopeFor(host: String): String? = synchronized(lock) {
        val identity = activeAccountIdentity() ?: return@synchronized null
        securePreferences?.getString(matchingEmailLegacyScopeKey(host, identity), null)
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

    /** Bookshelf identity is independent of the disposable page-cache namespace. */
    fun accountScopeFor(host: String, ewsKey: String): String = synchronized(lock) {
        if (securePreferences == null && normalizedHost(host) in memoryActiveHosts &&
            authorizationFor(host)?.ewsKey == ewsKey) {
            memoryIdentity?.let { return@synchronized it }
        }
        securePreferences?.getString(accountKeyMappingKey(host, ewsKey), null)
            ?.takeIf(String::isNotBlank)?.let { return@synchronized it }
        val currentKey = authorizationFor(host)?.ewsKey
        if (currentKey == ewsKey) {
            securePreferences?.getString(activeAccountScopeKey(host), null)
                ?.takeIf(String::isNotBlank) ?: cacheScopeFor(host)
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(ewsKey.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            "stale:$digest"
        }
    }

    /** Reuse one opaque identity when the same account explicitly logs in again. */
    fun activateAccountScope(host: String, email: String, newEwsKey: String) = synchronized(lock) {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        if (normalizedEmail.isBlank()) return@synchronized
        // Invalidate requests from the previous login before changing which
        // account owns this host. Cookie reads and writes check this under lock.
        epoch++
        val identityDigest = normalizedEmailDigest(normalizedEmail)
        val secure = securePreferences
        if (secure == null) {
            if (memoryEmailDigest != identityDigest) {
                memoryIdentity = UUID.randomUUID().toString()
                memoryActiveHosts.clear()
            }
            memoryEmailDigest = identityDigest
            memoryActiveHosts += normalizedHost(host)
            return@synchronized
        }
        val mappingKey = ACCOUNT_SCOPE_PREFIX + identityDigest
        val legacyEmailDigest = MessageDigest.getInstance("SHA-256")
            .digest("${normalizedHost(host)}:$normalizedEmail".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val existingIdentity = (secure.getString(mappingKey, null)
            ?: secure.getString(ACCOUNT_SCOPE_PREFIX + legacyEmailDigest, null))
            ?.takeIf(String::isNotBlank)
        val previousHostIdentity = secure.getString(activeAccountScopeKey(host), null)
        val previousAuthorization = authorizationFor(host)
        val legacyCacheScope = if (existingIdentity == null &&
            secure.getString(activeAccountScopeKey(host), null).isNullOrBlank() &&
            previousAuthorization?.ewsKey != newEwsKey
        ) preferences.getString(cacheScopeKey(host), null)?.takeIf(String::isNotBlank) else null
        val identity = chooseSharedAccountIdentity(
            activeEmailDigest = secure.getString(ACTIVE_EMAIL_DIGEST, null),
            requestedEmailDigest = identityDigest,
            activeIdentity = secure.getString(ACTIVE_SHARED_SCOPE, null),
            mappedIdentity = existingIdentity
        ) ?: UUID.randomUUID().toString()
        val editor = secure.edit()
            .putString(mappingKey, identity)
            .putString(ACTIVE_SHARED_SCOPE, identity)
            .putString(ACTIVE_EMAIL_DIGEST, identityDigest)
            .putString(activeAccountScopeKey(host), identity)
            .putString(accountKeyMappingKey(host, newEwsKey), identity)
        if (previousHostIdentity != null && previousHostIdentity != identity) {
            editor.putString(pendingLegacyScopeKey(host, identity),
                "account:$host:$previousHostIdentity")
        }
        if (existingIdentity != null && existingIdentity != identity) {
            editor.putString(matchingEmailLegacyScopeKey(host, identity),
                "account:$host:$existingIdentity")
        }
        if (legacyCacheScope != null) {
            editor.putString(pendingLegacyScopeKey(host, identity), "account:$host:$legacyCacheScope")
        }
        editor.commit()
    }

    fun pendingLegacyScopeFor(host: String, ewsKey: String): String? = synchronized(lock) {
        val identity = accountScopeFor(host, ewsKey)
        securePreferences?.getString(pendingLegacyScopeKey(host, identity), null)
            ?.takeIf { it.startsWith("account:$host:") }
    }

    fun clearPendingLegacyScope(host: String, ewsKey: String) = synchronized(lock) {
        val identity = accountScopeFor(host, ewsKey)
        securePreferences?.edit()?.remove(pendingLegacyScopeKey(host, identity))?.commit()
        Unit
    }

    /** Starts a new cache namespace when an explicit login changes account state. */
    fun rotateCacheScope(host: String) = synchronized(lock) {
        epoch++
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

    fun invalidateVerification(authorization: Authorization) = synchronized(lock) {
        preferences.edit().remove(verificationKey(authorization)).apply()
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
            epoch++
            if (host.isNullOrBlank()) {
                memoryIdentity = null
                memoryEmailDigest = null
                memoryActiveHosts.clear()
                securePreferences?.let { secure ->
                    val editor = secure.edit()
                    secure.all.keys.filter { it.startsWith(ACTIVE_ACCOUNT_SCOPE_PREFIX) }
                        .forEach(editor::remove)
                    editor.remove(ACTIVE_SHARED_SCOPE)
                    editor.remove(ACTIVE_EMAIL_DIGEST)
                    editor.remove(LEGACY_PRIMARY_HOST)
                    editor.remove(LEGACY_PRIMARY_IDENTITY)
                    editor.commit()
                }
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
                memoryActiveHosts.remove(normalizedHost(host))
                securePreferences?.edit()?.remove(activeAccountScopeKey(host))?.commit()
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
            persistLocked(durable = true)
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
            .onFailure { AppLogger.w(TAG, "Secure session read failed (${it::class.java.simpleName})") }
            .getOrNull()
            ?: return mutableListOf()
        return parseCookies(json)
    }

    private fun parseCookies(json: String): MutableList<StoredCookie> {
        return try {
            gson.fromJson(json, Array<StoredCookie>::class.java)?.toMutableList()
                ?: mutableListOf()
        } catch (error: Exception) {
            AppLogger.w(TAG, "Stored session could not be parsed (${error::class.java.simpleName})")
            mutableListOf()
        }
    }

    private fun persistLocked(durable: Boolean = false) {
        runCatching {
            val editor = securePreferences?.edit()?.putString(COOKIES, gson.toJson(cookies))
                ?: return@runCatching
            if (durable) {
                if (!editor.commit()) AppLogger.w(TAG, "Secure session write was not committed")
            } else {
                editor.apply()
            }
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

    private fun activeAccountScopeKey(host: String): String =
        ACTIVE_ACCOUNT_SCOPE_PREFIX + normalizedHost(host)

    private fun accountKeyMappingKey(host: String, ewsKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${normalizedHost(host)}:$ewsKey".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return ACCOUNT_KEY_SCOPE_PREFIX + digest
    }

    private fun pendingLegacyScopeKey(host: String, identity: String): String =
        PENDING_LEGACY_SCOPE_PREFIX + normalizedHost(host) + ":" + identity

    private fun matchingEmailLegacyScopeKey(host: String, identity: String): String =
        MATCHING_EMAIL_LEGACY_SCOPE_PREFIX + normalizedHost(host) + ":" + identity

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
        const val ACCOUNT_SCOPE_PREFIX = "account_identity_"
        const val ACTIVE_ACCOUNT_SCOPE_PREFIX = "active_account_identity_"
        const val ACTIVE_SHARED_SCOPE = "active_shared_account_identity"
        const val ACTIVE_EMAIL_DIGEST = "active_shared_email_digest"
        const val LEGACY_PRIMARY_HOST = "legacy_primary_host"
        const val LEGACY_PRIMARY_IDENTITY = "legacy_primary_identity"
        const val ACCOUNT_KEY_SCOPE_PREFIX = "account_key_identity_"
        const val PENDING_LEGACY_SCOPE_PREFIX = "pending_legacy_bookshelf_"
        const val MATCHING_EMAIL_LEGACY_SCOPE_PREFIX = "matching_email_legacy_bookshelf_"
        const val VERIFIED_AT_PREFIX = "verified_at_"
        val SESSION_COOKIE_NAMES = setOf("ews_key", "ews_token")

        private fun createSecurePreferences(context: Context): SharedPreferences? {
            val appContext = context.applicationContext
            fun create(): SharedPreferences {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    appContext,
                    SECURE_PREFERENCES,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }

            return try {
                create()
            } catch (error: Exception) {
                // Keyset files restored from another device cannot be decrypted by this
                // device's Android Keystore. Remove the complete encrypted-preference set
                // once, then recreate it instead of leaving every later launch logged out.
                Log.w(TAG, "Resetting unreadable secure session storage", error)
                appContext.deleteSharedPreferences(SECURE_PREFERENCES)
                appContext.deleteSharedPreferences(KEY_KEYSET_PREFERENCES)
                appContext.deleteSharedPreferences(VALUE_KEYSET_PREFERENCES)
                runCatching { create() }.onFailure {
                    Log.e(TAG, "Secure session storage is unavailable; session persistence disabled", it)
                }.getOrNull()
            }
        }

        const val SECURE_PREFERENCES = "esj_session_secure"
        const val KEY_KEYSET_PREFERENCES = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        const val VALUE_KEYSET_PREFERENCES = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
        const val TAG = "PersistentCookieJar"
    }
}
