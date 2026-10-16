package com.breakyuna.esjzone.network

import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel

/**
 * Small process-local cache for parsed novel details.
 *
 * The disk cache stores raw HTML so parser changes remain migration-free. This layer only
 * avoids repeatedly parsing the same document while navigating between lists, history,
 * favorites, and the detail page during one process lifetime.
 */
internal object NovelDetailCache {

    private const val MAX_ENTRIES = 128
    private const val MAX_AGE_MILLIS = 30L * 60L * 1000L
    private val lock = Any()
    @Volatile private var epoch = 0L
    val currentEpoch: Long get() = epoch
    private val entries = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
            val remove = size > MAX_ENTRIES
            if (remove && eldest != null) {
                CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.DETAIL, CacheEventReason.CAPACITY, eldest.key)
            }
            return remove
        }
    }

    fun read(key: String, nowMillis: Long = System.currentTimeMillis()): DetailedNovel? =
        synchronized(lock) {
            val entry = entries[key] ?: return@synchronized null
            if (nowMillis - entry.storedAtMillis > MAX_AGE_MILLIS) {
                entries.remove(key)
                CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.DETAIL, CacheEventReason.EXPIRED, key)
                null
            } else {
                entry.novel
            }
        }

    fun write(key: String, novel: DetailedNovel, sourceReference: String, expectedEpoch: Long) {
        // Keep the source check and publication atomic with respect to response replacement.
        val accepted = PageCache.commitIfCurrentReference(sourceReference) {
            synchronized(lock) {
                if (expectedEpoch == epoch) {
                    val existed = entries.containsKey(key)
                    entries[key] = Entry(novel, System.currentTimeMillis())
                    CacheDiagnostics.record(CacheEventType.UPDATE, CacheLayer.DETAIL,
                        if (existed) CacheEventReason.REPLACED else CacheEventReason.STORED, key)
                } else {
                    CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.DETAIL, CacheEventReason.SUPERSEDED, key)
                }
            }
        }
        if (!accepted) CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.DETAIL, CacheEventReason.SUPERSEDED, key)
    }

    fun remove(key: String) {
        synchronized(lock) {
            if (entries.remove(key) != null) {
                CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.DETAIL, CacheEventReason.INVALIDATED, key)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            epoch += 1L
            entries.keys.forEach { key ->
                CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.DETAIL, CacheEventReason.CLEARED, key)
            }
            entries.clear()
        }
    }

    private data class Entry(
        val novel: DetailedNovel,
        val storedAtMillis: Long
    )
}
