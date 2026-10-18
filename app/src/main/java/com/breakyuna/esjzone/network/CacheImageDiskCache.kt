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
internal class CacheImageDiskCache(private val delegate: DiskCache) : DiskCache by delegate {
    private data class Entry(val bytes: Long, val accessedAt: Long)
    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()
    private val active = mutableMapOf<String, Int>()
    private val index = File(directory.toString(), "cache-keys-v1")
    private var loaded = false
    private var indexDirty = false
    private var indexRecords = 0
    private var indexAppendFailed = false

    private fun load() {
        if (loaded) return
        // The former Coil cache has no reversible key inventory. Invalidate it once when
        // adopting opaque delegate keys so every surviving image can be reclaimed safely.
        if (!index.isFile) {
            delegate.clear()
        } else {
            val accessedByKey = mutableMapOf<String, Long>()
            index.forEachLine { line ->
                val parts = line.split(' ')
                val key = parts.firstOrNull()?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
                val accessed = parts.getOrNull(1)?.toLongOrNull()
                if (key != null && accessed != null) {
                    accessedByKey[key] = accessed
                }
            }
            accessedByKey.forEach { (key, accessed) ->
                delegate.openSnapshot(key)?.use { snapshot ->
                    entries[key] = Entry(bytes(snapshot), accessed)
                }
            }
        }
        if (entries.values.sumOf { it.bytes } != delegate.size) {
            delegate.clear()
            entries.clear()
        }
        loaded = true
        saveIndex()
    }

    private fun saveIndex() {
        index.parentFile?.mkdirs()
        val temporary = File(index.parentFile, "${index.name}.tmp")
        try {
            temporary.bufferedWriter().use { writer ->
                entries.forEach { (key, entry) -> writer.appendLine("$key ${entry.accessedAt}") }
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

    /** Register the key before Coil commits, so a crash cannot leave an unindexed image. */
    private fun appendIndex(id: String, accessedAt: Long) {
        if (indexAppendFailed || indexRecords >= maxOf(64, entries.size * 2)) saveIndex()
        try {
            index.appendText("$id $accessedAt\n")
            indexRecords++
            indexDirty = true
        } catch (error: Exception) {
            // Replace a possibly truncated tail before the next append.
            indexAppendFailed = true
            throw error
        }
    }

    private fun bytes(snapshot: DiskCache.Snapshot): Long =
        (fileSystem.metadataOrNull(snapshot.data)?.size ?: 0L) +
            (fileSystem.metadataOrNull(snapshot.metadata)?.size ?: 0L)

    val entryCount: Int get() = synchronized(lock) { load(); entries.size }
    val storageSizeBytes: Long get() = synchronized(lock) {
        load()
        // Coil's size excludes its journal and our opaque-key index.
        delegate.size + index.length() + File(directory.toString(), "journal").length()
    }

    override fun openSnapshot(key: String): DiskCache.Snapshot? = synchronized(lock) {
        load()
        val id = key.encodeUtf8().sha256().hex()
        delegate.openSnapshot(id)?.let { snapshot ->
            entries[id] = Entry(bytes(snapshot), System.currentTimeMillis())
            wrapSnapshot(id, snapshot)
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
        if (count == 0) active.remove(id) else active[id] = count
        CacheStorage.requestTrim()
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

    private fun committed(id: String, snapshot: DiskCache.Snapshot, accessedAt: Long) {
        val existed = entries.containsKey(id)
        entries[id] = Entry(bytes(snapshot), accessedAt)
        CacheDiagnostics.record(CacheEventType.UPDATE, CacheLayer.IMAGE,
            if (existed) CacheEventReason.REPLACED else CacheEventReason.STORED,
            fileId = id, bytes = entries.getValue(id).bytes)
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
                    try {
                        appendIndex(id, accessedAt)
                    } catch (error: Exception) {
                        editor.abort()
                        throw error
                    }
                    editor.commitAndOpenSnapshot()?.let { snapshot ->
                        try {
                            committed(id, snapshot, accessedAt)
                            wrapSnapshot(id, snapshot)
                        } catch (error: Exception) {
                            snapshot.close()
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
        removeId(key.encodeUtf8().sha256().hex(), CacheEventReason.INVALIDATED)
    }

    private fun removeId(id: String, reason: CacheEventReason, persistIndex: Boolean = true): Boolean {
        val entry = entries[id] ?: return false
        if (reason == CacheEventReason.CAPACITY && (active[id] ?: 0) > 0) return false
        if (!delegate.remove(id)) return false
        entries.remove(id)
        indexDirty = true
        if (persistIndex) flushIndex()
        CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.IMAGE, reason, fileId = id, bytes = entry.bytes)
        return true
    }

    override fun clear() = synchronized(lock) {
        load()
        try {
            entries.keys.toList().forEach { removeId(it, CacheEventReason.CLEARED, persistIndex = false) }
            delegate.clear() // Also invalidate edits that have not published their first entry yet.
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

    fun storageEntries(): List<CacheStorageEntry> = synchronized(lock) {
        load()
        entries.map { (id, entry) ->
            CacheStorageEntry(entry.bytes, entry.accessedAt) {
                synchronized(lock) {
                    if (entries[id] !== entry) false else removeId(id, CacheEventReason.CAPACITY, persistIndex = false)
                }
            }
        }
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
