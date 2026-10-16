package com.breakyuna.esjzone.network

import android.content.Context
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.util.storedTextReader
import com.breakyuna.esjzone.util.writeCompressedText
import java.io.File
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.WeakHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal object PageCacheTtl {
    const val PROFILE = 24L * 60L * 60L * 1000L
    const val HOME = 15L * 60L * 1000L
    const val WEEKLY_UPDATE = 15L * 60L * 1000L
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
 * Chapter DTO snapshots share this cache's quota and lifecycle; their timestamps are
 * inherited from the raw response, and a new response invalidates the derived snapshot.
 */
internal object PageCache {

    private const val FILE_PREFIX = "esj-page-v3"
    private val mutableLimitMiB = MutableStateFlow(SettingsDefaults.DEFAULT_PAGE_CACHE_MIB)
    val limitMiB = mutableLimitMiB.asStateFlow()
    private val maxCacheBytes: Long get() = mutableLimitMiB.value.toLong() * 1024L * 1024L
    private const val ACCESS_TOUCH_INTERVAL_MILLIS = 5L * 60L * 1000L

    @Volatile
    private var directory: File? = null

    private val ioLock = Any()
    @Volatile private var epoch = 0L
    val currentEpoch: Long get() = epoch
    // Tokens stay live only while a requester or an in-flight operation owns them.
    private val requests = WeakHashMap<WriteToken, String>()
    private val versions = mutableMapOf<String, Long>()
    private var nextRequest = 0L
    private var nextVersion = 0L

    internal data class WriteToken(val epoch: Long, val request: Long)
    internal data class Entry(val fetchedAt: Long, val body: String, val reference: String)

    fun beginWrite(key: String): WriteToken = synchronized(ioLock) {
        val id = digest(key)
        requests.values.remove(id)
        WriteToken(epoch, ++nextRequest).also { requests[it] = id }
    }

    fun currentToken(key: String): WriteToken = synchronized(ioLock) {
        val id = digest(key)
        requests.entries.firstOrNull { it.value == id }?.key
            ?: WriteToken(epoch, ++nextRequest).also { requests[it] = id }
    }

    fun accepts(key: String, token: WriteToken): Boolean = synchronized(ioLock) {
        token.epoch == epoch && requests[token] == digest(key)
    }

    fun commitIfCurrent(key: String, token: WriteToken, action: () -> Unit): Boolean = synchronized(ioLock) {
        if (!accepts(key, token)) return@synchronized false
        action()
        true
    }

    fun cancelPendingWrites() = synchronized(ioLock) {
        epoch += 1L
        requests.clear()
        versions.clear()
    }

    @Volatile
    private var cachedSizeBytes = 0L

    @Volatile
    private var cachedEntryCount = 0

    fun setLimitMiB(value: Int) = synchronized(ioLock) {
        require(value in SettingsDefaults.MIN_PAGE_CACHE_MIB..SettingsDefaults.MAX_PAGE_CACHE_MIB)
        if (value == mutableLimitMiB.value) return@synchronized
        CacheAnalysis.limitChanged(value, stats())
        mutableLimitMiB.value = value
        if (cachedSizeBytes > maxCacheBytes) trimToSize()
    }

    fun initialize(context: Context) {
        // Novel chapters are useful after an app restart and while offline, so
        // keep their HTML in the app's persistent files area rather than the
        // OS-evictable cache directory.
        initializeDirectory(File(context.filesDir, "novel_page_cache"))

        // The cache used to live under cacheDir.  Remove those old snapshots so
        // an account's authenticated HTML cannot survive logout in the legacy
        // location after the persistent cache is initialized.
        File(context.cacheDir, "page_cache").listFiles()
            ?.filter { it.isFile && it.name.startsWith("esj-page-") }
            ?.forEach { runCatching { it.delete() } }
    }

    internal fun initializeDirectory(cacheDirectory: File): Unit = synchronized(ioLock) {
        if (cacheDirectory.isDirectory || cacheDirectory.mkdirs()) {
            cancelPendingWrites()
            directory = cacheDirectory
            // v3 replaces cookie-derived cache keys with a stable session namespace.
            // Remove older snapshots once so they cannot remain as unreachable files.
            // Count current entries during migration cleanup instead of enumerating the directory twice.
            cachedSizeBytes = 0L
            cachedEntryCount = 0
            cacheDirectory.listFiles().orEmpty().forEach { file ->
                if (file.isFile && file.name.startsWith("esj-page-")) {
                    if (file.name.startsWith(FILE_PREFIX)) {
                        cachedSizeBytes += file.length()
                        cachedEntryCount += 1
                    } else {
                        runCatching { file.delete() }
                    }
                }
            }
            CacheAnalysis.observe(stats())
            if (cachedSizeBytes > maxCacheBytes) trimToSize()
        }
    }

    fun read(key: String, maxAgeMillis: Long, nowMillis: Long = System.currentTimeMillis()): String? =
        readEntry(key, maxAgeMillis, nowMillis)?.body

    fun readEntry(key: String, maxAgeMillis: Long = Long.MAX_VALUE,
                  nowMillis: Long = System.currentTimeMillis(), sourceKey: String = key): Entry? = synchronized(ioLock) {
        if (maxAgeMillis <= 0L) return@synchronized null
        val file = fileFor(key) ?: return@synchronized null
        if (!file.isFile) return@synchronized null
        try {
            file.storedTextReader().use { reader ->
                val fetchedAt = reader.readLine()?.toLongOrNull() ?: return@synchronized null
                if (fetchedAt > nowMillis || nowMillis - fetchedAt > maxAgeMillis) return@synchronized null
                val body = reader.readText()
                if (nowMillis - file.lastModified() >= ACCESS_TOUCH_INTERVAL_MILLIS) file.setLastModified(nowMillis)
                val version = versions.getOrPut(digest(key)) { ++nextVersion }
                val id = digest(sourceKey)
                Entry(fetchedAt, body, "$id:${digest(structuredKey(sourceKey))}:$epoch:${versions[id] ?: version}")
            }
        } catch (_: Exception) {
            CacheDiagnostics.record(CacheEventType.FAILURE, diagnosticLayer(key), CacheEventReason.READ_FAILED, key)
            null
        }
    }

    fun readStale(key: String): String? = read(key, Long.MAX_VALUE)

    internal fun reference(key: String): String? = synchronized(ioLock) {
        val id = digest(key)
        versions[id]?.let { "$id:${digest(structuredKey(key))}:$epoch:$it" }
    }

    internal fun isCurrentReference(reference: String): Boolean = synchronized(ioLock) {
        matchesReference(reference.split(':'))
    }

    internal fun commitIfCurrentReference(reference: String, action: () -> Unit): Boolean = synchronized(ioLock) {
        if (!matchesReference(reference.split(':'))) return@synchronized false
        action()
        true
    }

    /** Record an in-memory hit without decompressing the persisted snapshot. */
    internal fun touch(key: String, nowMillis: Long = System.currentTimeMillis()) = synchronized(ioLock) {
        val file = fileFor(key) ?: return@synchronized
        if (file.isFile && nowMillis - file.lastModified() >= ACCESS_TOUCH_INTERVAL_MILLIS) {
            file.setLastModified(nowMillis)
        }
    }

    private fun matchesReference(parts: List<String>): Boolean =
        parts.size == 4 && parts.take(2).all { it.matches(Regex("[a-f0-9]{64}")) } &&
            parts[2].toLongOrNull() == epoch &&
            parts[3].toLongOrNull()?.let { it == (versions[parts[0]] ?: versions[parts[1]]) } == true

    /** A failed commit keeps both the previous response and its parsed snapshot. */
    fun write(key: String, body: String, nowMillis: Long = System.currentTimeMillis(),
              expected: WriteToken? = null, trim: Boolean = true): Boolean =
        write(key, nowMillis, expected, trim) { it.write(body) }

    internal fun write(key: String, nowMillis: Long = System.currentTimeMillis(),
              expected: WriteToken? = null, trim: Boolean = true,
              writeBody: (Writer) -> Unit): Boolean = synchronized(ioLock) {
        if (expected != null && !accepts(key, expected)) {
            CacheDiagnostics.record(CacheEventType.FAILURE, diagnosticLayer(key), CacheEventReason.SUPERSEDED, key)
            return@synchronized false
        }
        val file = fileFor(key) ?: run {
            CacheDiagnostics.record(CacheEventType.FAILURE, diagnosticLayer(key), CacheEventReason.WRITE_FAILED, key)
            return@synchronized false
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            val existed = file.isFile
            val previousSize = if (existed) file.length() else 0L
            temporary.writeCompressedText { writer ->
                writer.write(nowMillis.toString())
                writer.write("\n")
                writeBody(writer)
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            cachedSizeBytes += file.length() - previousSize
            if (!existed) cachedEntryCount += 1
            val id = digest(key)
            versions[id] = ++nextVersion
            CacheDiagnostics.record(CacheEventType.UPDATE, diagnosticLayer(key),
                if (existed) CacheEventReason.REPLACED else CacheEventReason.STORED, fileId = id, bytes = file.length())
            if (!key.startsWith("chapter-structured-v2|")) remove(structuredKey(key), CacheEventReason.SOURCE_UPDATED)
            CacheAnalysis.observe(stats())
            if (trim && cachedSizeBytes > maxCacheBytes) trimToSize()
            true
        } catch (_: Exception) {
            CacheDiagnostics.record(CacheEventType.FAILURE, diagnosticLayer(key), CacheEventReason.WRITE_FAILED, key)
            false
        } finally { runCatching { temporary.delete() } }
    }

    /** Remove exactly the response used by a completed download, preserving a newer response. */
    fun removeReference(reference: String) = synchronized(ioLock) {
        val parts = reference.split(':')
        if (!matchesReference(parts)) return@synchronized
        val id = parts[0]
        removeId(id, CacheEventReason.DOWNLOADED)
        removeId(parts[1], CacheEventReason.DOWNLOADED, CacheLayer.CHAPTER)
    }

    private fun removeId(id: String, reason: CacheEventReason, layer: CacheLayer = CacheLayer.PAGE) {
        requests.values.remove(id)
        versions.remove(id)
        val file = directory?.let { File(it, "$FILE_PREFIX-$id.html") } ?: return
        val length = file.length()
        if (runCatching { file.delete() }.getOrDefault(false)) {
            cachedSizeBytes = (cachedSizeBytes - length).coerceAtLeast(0L)
            cachedEntryCount = (cachedEntryCount - 1).coerceAtLeast(0)
            CacheDiagnostics.record(CacheEventType.EVICT, layer, reason, fileId = id, bytes = length)
        } else if (file.exists()) {
            CacheDiagnostics.record(CacheEventType.FAILURE, layer, CacheEventReason.DELETE_FAILED, fileId = id)
        }
    }

    internal fun structuredKey(key: String): String = "chapter-structured-v2|$key"

    internal fun writeDerived(key: String, sourceBody: String, json: String): String? = synchronized(ioLock) {
        val source = readEntry(key)?.takeIf { it.body == sourceBody } ?: return@synchronized null
        write(structuredKey(key), json, source.fetchedAt)
        source.reference
    }

    fun remove(key: String, reason: CacheEventReason = CacheEventReason.INVALIDATED) = synchronized(ioLock) {
        removeId(digest(key), reason, diagnosticLayer(key))
        if (!key.startsWith("chapter-structured-v2|")) {
            removeId(digest(structuredKey(key)), reason, CacheLayer.CHAPTER)
        }
    }

    fun clear() {
        synchronized(ioLock) {
            cancelPendingWrites()
            val cacheDirectory = directory ?: return
            val files = cacheDirectory.listFiles()
                ?: throw java.io.IOException("Unable to list page cache files")
            var deletionFailed = false
            files.forEach { file ->
                if (file.name.startsWith(FILE_PREFIX)) {
                    val length = file.length()
                    val id = file.name.removePrefix("$FILE_PREFIX-").removeSuffix(".html")
                    if (file.delete()) {
                        CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.PAGE, CacheEventReason.CLEARED,
                            fileId = id, bytes = length)
                    } else if (file.exists()) {
                        deletionFailed = true
                        CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.PAGE, CacheEventReason.DELETE_FAILED,
                            fileId = id)
                    }
                }
            }
            val remaining = cacheDirectory.listFiles()
                ?.filter { it.isFile && it.name.startsWith(FILE_PREFIX) }
                ?: throw java.io.IOException("Unable to verify page cache clearing")
            cachedSizeBytes = remaining.sumOf { it.length() }
            cachedEntryCount = remaining.size
            if (deletionFailed || remaining.isNotEmpty()) {
                throw java.io.IOException("Unable to delete all page cache files")
            }
        }
    }

    fun stats(): PageCacheStats = synchronized(ioLock) {
        PageCacheStats(cachedEntryCount, cachedSizeBytes, maxCacheBytes)
    }

    private fun fileFor(key: String): File? {
        val cacheDirectory = directory ?: return null
        return File(cacheDirectory, "$FILE_PREFIX-${digest(key)}.html")
    }

    private fun digest(key: String): String = MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun diagnosticLayer(key: String): CacheLayer = when {
        key.startsWith("chapter-structured-v2|") -> CacheLayer.CHAPTER
        key.startsWith("list-snapshot-v1|") -> CacheLayer.LIST
        else -> CacheLayer.PAGE
    }

    fun trim() = synchronized(ioLock) { if (cachedSizeBytes > maxCacheBytes) trimToSize() }

    private fun trimToSize() {
        CacheAnalysis.evictionPass()
        val files = directory?.listFiles()
            ?.filter { it.isFile && it.name.startsWith(FILE_PREFIX) }
            ?.sortedBy { it.lastModified() }
            ?: return
        var total = cachedSizeBytes
        var count = cachedEntryCount
        for (file in files) {
            if (total <= maxCacheBytes) break
            val length = file.length()
            if (file.delete()) {
                val id = file.name.removePrefix("$FILE_PREFIX-").removeSuffix(".html")
                requests.values.remove(id)
                versions.remove(id)
                total -= length
                count -= 1
                CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.PAGE, CacheEventReason.CAPACITY,
                    fileId = id, bytes = length)
            } else if (file.exists()) {
                CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.PAGE, CacheEventReason.DELETE_FAILED)
            }
        }
        cachedSizeBytes = total.coerceAtLeast(0L)
        cachedEntryCount = count.coerceAtLeast(0)
    }

}

data class PageCacheStats(
    val entryCount: Int,
    val sizeBytes: Long,
    val maxSizeBytes: Long
)
