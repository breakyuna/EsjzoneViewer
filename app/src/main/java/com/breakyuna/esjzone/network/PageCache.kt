package com.breakyuna.esjzone.network

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal object PageCacheTtl {
    const val PROFILE = 24L * 60L * 60L * 1000L
    const val HOME = 15L * 60L * 1000L
    const val CATEGORIES = 24L * 60L * 60L * 1000L
    const val DETAIL = 6L * 60L * 60L * 1000L
    const val CHAPTER = 30L * 24L * 60L * 60L * 1000L
    const val LIST = 30L * 60L * 1000L
    const val SEARCH = 15L * 60L * 1000L
    const val ACCOUNT_LIST = 5L * 60L * 1000L
    const val COMMUNITY = 5L * 60L * 1000L
}

/**
 * In-memory generations for mutations that make a specific account page
 * stale.  A generation is deliberately narrower than clearing every page:
 * changing a favorite must not invalidate history or the user's profile.
 */
internal object PageCacheInvalidation {
    private val _favoriteGeneration = MutableStateFlow(0L)
    val favoriteGeneration: StateFlow<Long> = _favoriteGeneration.asStateFlow()

    fun favoritesChanged() {
        _favoriteGeneration.update { it + 1L }
    }
}

/**
 * Small disk-backed cache for server-rendered HTML pages.
 *
 * HTML is cached instead of polymorphic UI models so the parser remains the single source
 * of truth and parser fixes take effect without a database migration.
 */
internal object PageCache {

    private const val FILE_PREFIX = "esj-page-v3"
    private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L
    private const val ACCESS_TOUCH_INTERVAL_MILLIS = 5L * 60L * 1000L

    @Volatile
    private var directory: File? = null

    private val ioLock = Any()

    @Volatile
    private var cachedSizeBytes = 0L

    @Volatile
    private var cachedEntryCount = 0

    fun initialize(context: Context) {
        // Novel chapters are useful after an app restart and while offline, so
        // keep their HTML in the app's persistent files area rather than the
        // OS-evictable cache directory.
        val cacheDirectory = File(context.filesDir, "novel_page_cache")
        if (cacheDirectory.isDirectory || cacheDirectory.mkdirs()) {
            directory = cacheDirectory
            // v3 replaces cookie-derived cache keys with a stable session namespace.
            // Remove older snapshots once so they cannot remain as unreachable files.
            cacheDirectory.listFiles()
                ?.filter {
                    it.isFile && it.name.startsWith("esj-page-") &&
                        !it.name.startsWith(FILE_PREFIX)
                }
                ?.forEach { runCatching { it.delete() } }
            refreshStats(cacheDirectory)
        }

        // The cache used to live under cacheDir.  Remove those old snapshots so
        // an account's authenticated HTML cannot survive logout in the legacy
        // location after the persistent cache is initialized.
        File(context.cacheDir, "page_cache").listFiles()
            ?.filter { it.isFile && it.name.startsWith("esj-page-") }
            ?.forEach { runCatching { it.delete() } }
    }

    fun read(key: String, maxAgeMillis: Long, nowMillis: Long = System.currentTimeMillis()): String? {
        if (maxAgeMillis <= 0L) return null
        val file = fileFor(key) ?: return null
        if (!file.isFile) return null
        return try {
            val encoded = file.readText(StandardCharsets.UTF_8)
            val separator = encoded.indexOf('\n')
            if (separator <= 0) return null
            val fetchedAt = encoded.substring(0, separator).toLongOrNull() ?: return null
            if (fetchedAt > nowMillis || nowMillis - fetchedAt > maxAgeMillis) return null
            if (nowMillis - file.lastModified() >= ACCESS_TOUCH_INTERVAL_MILLIS) {
                file.setLastModified(nowMillis)
            }
            encoded.substring(separator + 1)
        } catch (_: Exception) {
            null
        }
    }

    fun readStale(key: String): String? = read(key, Long.MAX_VALUE)

    fun write(key: String, body: String, nowMillis: Long = System.currentTimeMillis()) {
        val file = fileFor(key) ?: return
        runCatching {
            synchronized(ioLock) {
                val existed = file.isFile
                val previousSize = if (existed) file.length() else 0L
                val temporary = File(file.parentFile, "${file.name}.tmp")
                temporary.writeText("$nowMillis\n$body", StandardCharsets.UTF_8)
                try {
                    Files.move(
                        temporary.toPath(),
                        file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: Exception) {
                    Files.move(
                        temporary.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
                cachedSizeBytes += file.length() - previousSize
                if (!existed) cachedEntryCount += 1
                if (cachedSizeBytes > MAX_CACHE_BYTES) trimToSize()
            }
        }
    }

    fun remove(key: String) {
        val file = fileFor(key) ?: return
        runCatching {
            synchronized(ioLock) {
                val length = file.length()
                if (file.delete()) {
                    cachedSizeBytes = (cachedSizeBytes - length).coerceAtLeast(0L)
                    cachedEntryCount = (cachedEntryCount - 1).coerceAtLeast(0)
                }
            }
        }
    }

    fun clear() {
        synchronized(ioLock) {
            val cacheDirectory = directory ?: return
            cacheDirectory.listFiles()?.forEach { file ->
                if (file.name.startsWith(FILE_PREFIX)) file.delete()
            }
            val remaining = cacheDirectory.listFiles()
                ?.filter { it.isFile && it.name.startsWith(FILE_PREFIX) }
                .orEmpty()
            cachedSizeBytes = remaining.sumOf { it.length() }
            cachedEntryCount = remaining.size
        }
    }

    fun stats(): PageCacheStats = synchronized(ioLock) {
        PageCacheStats(cachedEntryCount, cachedSizeBytes, MAX_CACHE_BYTES)
    }

    private fun fileFor(key: String): File? {
        val cacheDirectory = directory ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return File(cacheDirectory, "$FILE_PREFIX-$digest.html")
    }

    private fun trimToSize() {
        val files = directory?.listFiles()
            ?.filter { it.isFile && it.name.startsWith(FILE_PREFIX) }
            ?.sortedBy { it.lastModified() }
            ?: return
        var total = cachedSizeBytes
        var count = cachedEntryCount
        for (file in files) {
            if (total <= MAX_CACHE_BYTES) break
            val length = file.length()
            if (file.delete()) {
                total -= length
                count -= 1
            }
        }
        cachedSizeBytes = total.coerceAtLeast(0L)
        cachedEntryCount = count.coerceAtLeast(0)
    }

    private fun refreshStats(cacheDirectory: File) {
        synchronized(ioLock) {
            val files = cacheDirectory.listFiles()
                ?.filter { it.isFile && it.name.startsWith(FILE_PREFIX) }
                .orEmpty()
            cachedSizeBytes = files.sumOf { it.length() }
            cachedEntryCount = files.size
            if (cachedSizeBytes > MAX_CACHE_BYTES) trimToSize()
        }
    }
}

data class PageCacheStats(
    val entryCount: Int,
    val sizeBytes: Long,
    val maxSizeBytes: Long
)
