package com.breakyuna.esjzone.network

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

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
 * Small disk-backed cache for server-rendered HTML pages.
 *
 * HTML is cached instead of polymorphic UI models so the parser remains the single source
 * of truth and parser fixes take effect without a database migration.
 */
internal object PageCache {

    private const val FILE_PREFIX = "esj-page-v1"
    private const val MAX_CACHE_BYTES = 64L * 1024L * 1024L

    @Volatile
    private var directory: File? = null

    fun initialize(context: Context) {
        val cacheDirectory = File(context.cacheDir, "page_cache")
        if (cacheDirectory.isDirectory || cacheDirectory.mkdirs()) {
            directory = cacheDirectory
        }
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
            encoded.substring(separator + 1)
        } catch (_: Exception) {
            null
        }
    }

    fun readStale(key: String): String? = read(key, Long.MAX_VALUE)

    fun write(key: String, body: String, nowMillis: Long = System.currentTimeMillis()) {
        val file = fileFor(key) ?: return
        runCatching {
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText("$nowMillis\n$body", StandardCharsets.UTF_8)
            if (!temporary.renameTo(file)) {
                file.writeText("$nowMillis\n$body", StandardCharsets.UTF_8)
                temporary.delete()
            }
            trimToSize()
        }
    }

    fun remove(key: String) {
        val file = fileFor(key) ?: return
        runCatching { file.delete() }
    }

    fun clear() {
        directory?.listFiles()?.forEach { file ->
            if (file.name.startsWith(FILE_PREFIX)) file.delete()
        }
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
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MAX_CACHE_BYTES) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }
}
