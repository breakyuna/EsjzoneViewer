package com.breakyuna.esjzone.network

import coil3.EventListener
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import okio.ByteString.Companion.encodeUtf8

/** Keeps only opaque keys in the eviction index; all data mutations use Coil's public API. */
internal class CacheImageDiskCache(private var delegate: DiskCache) : DiskCache {
    override val directory = delegate.directory
    override val fileSystem = delegate.fileSystem
    override val maxSize = delegate.maxSize
    override val size: Long get() = synchronized(lock) { delegate.size }

    private data class Entry(val bytes: Long, val accessedAt: Long)
    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()
    private val active = mutableMapOf<String, Int>()
    private val deferredTrimIds = mutableSetOf<String>()
    private val index = File(directory.toString(), "cache-keys-v1")
    private var loaded = false
    private var indexDirty = false
    private var indexRecords = 0
    private var indexAppendFailed = false
    private var nativeTrimDeferred = false

    private fun load() {
        if (loaded) return
        if (index.isFile) {
            runCatching {
                index.forEachLine { line ->
                    val parts = line.split(' ')
                    val key = parts.firstOrNull()?.takeIf { KEY_PATTERN.matches(it) }
                    val accessed = parts.getOrNull(1)?.toLongOrNull()
                    val entryBytes = parts.getOrNull(2)?.toLongOrNull()?.takeIf { it >= 0L } ?: UNKNOWN_BYTES
                    if (key != null && accessed != null) {
                        entries[key] = Entry(entryBytes, accessed)
                    }
                }
            }.onFailure {
                entries.clear()
                indexDirty = true
            }
        }
        loaded = true
    }

    private fun saveIndex() {
        index.parentFile?.mkdirs()
        val temporary = File(index.parentFile, "${index.name}.tmp")
        try {
            temporary.bufferedWriter().use { writer ->
                entries.forEach { (key, entry) ->
                    val line = if (entry.bytes >= 0L) "$key ${entry.accessedAt} ${entry.bytes}"
                    else "$key ${entry.accessedAt}"
                    writer.appendLine(line)
                }
            }
            try {
                Files.move(temporary.toPath(), index.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), index.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            indexDirty = false
            indexRecords = entries.size
            indexAppendFailed = false
        } finally { temporary.delete() }
    }

    /** Append a committed key record without scanning or rewriting the entire index on each write. */
    private fun appendIndex(id: String, accessedAt: Long, entryBytes: Long) {
        if (indexAppendFailed || indexRecords >= maxOf(64, entries.size * 2)) {
            saveIndex()
            return
        }
        try {
            index.parentFile?.mkdirs()
            index.appendText("$id $accessedAt $entryBytes\n")
            indexRecords++
        } catch (error: Exception) {
            // Replace a possibly truncated tail before the next append.
            indexAppendFailed = true
            throw error
        }
    }

    private fun bytes(snapshot: DiskCache.Snapshot): Long =
        (fileSystem.metadataOrNull(snapshot.data)?.size ?: 0L) +
            (fileSystem.metadataOrNull(snapshot.metadata)?.size ?: 0L)

    private fun measureEntryBytesLocked(id: String): Long? = try {
        val snapshot = delegate.openSnapshot(id)
        if (snapshot == null) {
            if (entries.remove(id) != null) indexDirty = true
            null
        } else {
            snapshot.use { bytes(it) }
        }
    } catch (_: Exception) {
        purgeCorruptEntryLocked(id, CacheEventReason.READ_FAILED)
        null
    }

    private fun purgeCorruptEntryLocked(id: String, reason: CacheEventReason) {
        runCatching { delegate.remove(id) }
        if (entries.remove(id) != null) indexDirty = true
        CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.IMAGE, reason, fileId = id)
    }

    val entryCount: Int get() = synchronized(lock) { load(); entries.size }
    val storageSizeBytes: Long get() = synchronized(lock) {
        load()
        // Coil's size excludes its journal and our opaque-key index.
        delegate.size + index.length() + File(directory.toString(), "journal").length()
    }

    override fun openSnapshot(key: String): DiskCache.Snapshot? = synchronized(lock) {
        load()
        val id = key.encodeUtf8().sha256().hex()
        var snapshot: DiskCache.Snapshot? = null
        try {
            snapshot = delegate.openSnapshot(id)
            if (snapshot == null) {
                if (entries.remove(id) != null) indexDirty = true
                return@synchronized null
            }
            val entryBytes = bytes(snapshot)
            val existing = entries[id]
            if (existing == null || existing.bytes != entryBytes) {
                indexDirty = true
            }
            entries[id] = Entry(entryBytes, System.currentTimeMillis())
            wrapSnapshot(id, snapshot)
        } catch (_: Exception) {
            runCatching { snapshot?.close() }
            purgeCorruptEntryLocked(id, CacheEventReason.READ_FAILED)
            null
        }
    }

    override fun openEditor(key: String): DiskCache.Editor? = synchronized(lock) {
        load()
        val id = key.encodeUtf8().sha256().hex()
        delegate.openEditor(id)?.let { wrapEditor(id, it) }
    }

    private fun retain(id: String) { active[id] = (active[id] ?: 0) + 1 }
    private fun release(id: String) {
        val count = (active[id] ?: 1) - 1
        if (count <= 0) {
            active.remove(id)
            val retryNativeTrim = active.isEmpty() && nativeTrimDeferred
            if (retryNativeTrim) nativeTrimDeferred = false
            if (deferredTrimIds.remove(id) || retryNativeTrim) CacheStorage.requestTrim()
        } else {
            active[id] = count
        }
    }

    private fun wrapSnapshot(id: String, snapshot: DiskCache.Snapshot): DiskCache.Snapshot {
        retain(id)
        return object : DiskCache.Snapshot by snapshot {
            private var closed = false
            override fun close() = synchronized(lock) {
                if (!closed) {
                    closed = true
                    try { snapshot.close() } finally { release(id) }
                }
            }

            override fun closeAndOpenEditor(): DiskCache.Editor? = synchronized(lock) {
                check(!closed)
                closed = true
                try { snapshot.closeAndOpenEditor()?.let { wrapEditor(id, it) } }
                finally { release(id) }
            }
        }
    }

    fun touch(key: String) = synchronized(lock) {
        if (!loaded) return@synchronized
        val id = key.encodeUtf8().sha256().hex()
        entries[id]?.let { entries[id] = it.copy(accessedAt = System.currentTimeMillis()) }
    }

    private fun committed(id: String, snapshot: DiskCache.Snapshot, accessedAt: Long): Long {
        val existed = entries.containsKey(id)
        val entryBytes = bytes(snapshot)
        entries[id] = Entry(entryBytes, accessedAt)
        CacheDiagnostics.record(CacheEventType.UPDATE, CacheLayer.IMAGE,
            if (existed) CacheEventReason.REPLACED else CacheEventReason.STORED,
            fileId = id, bytes = entryBytes)
        return entryBytes
    }

    private fun wrapEditor(id: String, editor: DiskCache.Editor): DiskCache.Editor {
        retain(id)
        return object : DiskCache.Editor by editor {
            private var completed = false
            override fun commit() {
                commitAndOpenSnapshot()?.close()
            }

            override fun commitAndOpenSnapshot(): DiskCache.Snapshot? = synchronized(lock) {
                check(!completed)
                completed = true
                try {
                    val accessedAt = System.currentTimeMillis()
                    editor.commitAndOpenSnapshot()?.let { snapshot ->
                        try {
                            val entryBytes = committed(id, snapshot, accessedAt)
                            runCatching { appendIndex(id, accessedAt, entryBytes) }
                                .onFailure {
                                    indexAppendFailed = true
                                    indexDirty = true
                                }
                            wrapSnapshot(id, snapshot)
                        } catch (error: Exception) {
                            runCatching { snapshot.close() }
                            throw error
                        }
                    }
                } catch (error: Exception) {
                    CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.IMAGE, CacheEventReason.WRITE_FAILED, fileId = id)
                    throw error
                } finally { release(id) }
            }

            override fun abort() = synchronized(lock) {
                if (!completed) {
                    completed = true
                    try { editor.abort() } finally { release(id) }
                }
            }
        }
    }

    override fun remove(key: String): Boolean = synchronized(lock) {
        load()
        val id = key.encodeUtf8().sha256().hex()
        if (entries.containsKey(id)) {
            removeId(id, CacheEventReason.INVALIDATED)
        } else {
            val removed = runCatching { delegate.remove(id) }.getOrDefault(false)
            if (removed) {
                CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.IMAGE, CacheEventReason.INVALIDATED, fileId = id)
            }
            removed
        }
    }

    private fun removeId(id: String, reason: CacheEventReason, persistIndex: Boolean = true): Boolean {
        val entry = entries[id] ?: return false
        if (reason == CacheEventReason.CAPACITY && (active[id] ?: 0) > 0) {
            deferredTrimIds.add(id)
            return false
        }
        val removedFromDelegate = runCatching { delegate.remove(id) }.getOrDefault(false)
        entries.remove(id)
        deferredTrimIds.remove(id)
        indexDirty = true
        if (persistIndex) flushIndex()
        if (!removedFromDelegate) return false
        CacheDiagnostics.record(
            CacheEventType.EVICT,
            CacheLayer.IMAGE,
            reason,
            fileId = id,
            bytes = entry.bytes.coerceAtLeast(0L)
        )
        return true
    }

    override fun clear() = synchronized(lock) {
        load()
        try {
            entries.keys.toList().forEach { removeId(it, CacheEventReason.CLEARED, persistIndex = false) }
            delegate.clear() // Also invalidate edits that have not published their first entry yet.
            entries.clear()
            deferredTrimIds.clear()
            indexRecords = 0
            indexDirty = true
        } finally { flushIndex() }
        CacheStorage.requestTrim()
    }

    /** Persist one index for an entire eviction pass, including a partially failed pass. */
    internal fun flushIndex() = synchronized(lock) {
        if (indexDirty) saveIndex()
    }

    override fun shutdown() = synchronized(lock) {
        if (loaded) saveIndex()
        delegate.shutdown()
    }

    /** Use Coil's LRU when the shared quota needs to reclaim committed bytes with unknown keys. */
    fun reclaimUnindexedBytes(excessBytes: Long): Long? = synchronized(lock) {
        load()
        val before = delegate.size
        val indexedBytes = entries.values.sumOf { it.bytes.coerceAtLeast(0L) }
        val reclaimBytes = minOf(excessBytes, before - indexedBytes)
        if (reclaimBytes <= 0L) return@synchronized 0L
        if (active.isNotEmpty()) {
            nativeTrimDeferred = true
            return@synchronized null
        }

        val target = before - reclaimBytes
        if (target == 0L) {
            delegate.clear()
        } else {
            fun openCache(limit: Long) = DiskCache.Builder().directory(directory)
                .fileSystem(fileSystem).maxSizeBytes(limit).build()

            delegate.shutdown()
            try {
                val limited = openCache(target)
                try {
                    limited.size // Initialize the journal before shutdown applies the lower limit.
                } finally {
                    limited.shutdown()
                }
            } finally {
                delegate = openCache(maxSize)
            }
        }
        val reclaimed = (before - delegate.size).coerceAtLeast(0L)
        if (reclaimed > 0L) {
            for ((id, entry) in entries.toList()) {
                val actualBytes = measureEntryBytesLocked(id) ?: continue
                if (actualBytes != entry.bytes) {
                    entries[id] = entry.copy(bytes = actualBytes)
                    indexDirty = true
                }
            }
            CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.IMAGE, CacheEventReason.CAPACITY, bytes = reclaimed)
        }
        reclaimed
    }

    fun storageEntries(): List<CacheStorageEntry> = synchronized(lock) {
        load()
        val result = ArrayList<CacheStorageEntry>(entries.size)
        for (id in entries.keys.toList()) {
            val current = entries[id] ?: continue
            val entry = if (current.bytes >= 0L) {
                current
            } else {
                if (id in active) {
                    deferredTrimIds.add(id)
                    continue
                }
                val resolvedBytes = measureEntryBytesLocked(id) ?: continue
                current.copy(bytes = resolvedBytes).also {
                    entries[id] = it
                    indexDirty = true
                }
            }
            result += CacheStorageEntry(entry.bytes, entry.accessedAt) {
                synchronized(lock) {
                    if (entries[id] !== entry) false else removeId(id, CacheEventReason.CAPACITY, persistIndex = false)
                }
            }
        }
        result
    }

    private companion object {
        private const val UNKNOWN_BYTES = -1L
        private val KEY_PATTERN = Regex("[a-f0-9]{64}")
    }
}

/** Count completed remote-image requests once, including memory hits that skip disk lookup. */
internal class CacheImageEventListener : EventListener() {
    private fun remote(request: ImageRequest) = request.data.toString().let {
        it.startsWith("https://") || it.startsWith("http://")
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        if (!remote(request)) return
        val hit = result.dataSource == DataSource.MEMORY_CACHE || result.dataSource == DataSource.DISK
        CacheAnalysis.lookup(CacheLayer.IMAGE, fresh = hit, forced = !request.diskCachePolicy.readEnabled)
        if (hit) CacheStorage.touchImage(result.diskCacheKey ?: request.diskCacheKey ?: request.data.toString())
        if (hit) CacheDiagnostics.record(CacheEventType.HIT, CacheLayer.IMAGE,
            if (result.dataSource == DataSource.MEMORY_CACHE) CacheEventReason.MEMORY else CacheEventReason.DISK,
            request.diskCacheKey ?: request.data.toString())
    }

    override fun onError(request: ImageRequest, result: ErrorResult) {
        if (remote(request)) CacheAnalysis.lookup(CacheLayer.IMAGE, fresh = false,
            forced = !request.diskCachePolicy.readEnabled)
    }
}
