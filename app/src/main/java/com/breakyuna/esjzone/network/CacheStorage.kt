package com.breakyuna.esjzone.network

import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.network.features.HistoryDataCache
import com.breakyuna.esjzone.network.features.HomeDataCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A committed disk entry. Removal must recheck that it has not been read or replaced. */
internal data class CacheStorageEntry(
    val sizeBytes: Long,
    val accessedAt: Long,
    val remove: () -> Boolean
)

internal data class CacheStorageStats(val entryCount: Int, val sizeBytes: Long, val maxSizeBytes: Long)

/** The four disk caches shown in storage management share one quota and one LRU order. */
internal object CacheStorage {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val trimLock = Any()
    private val mutableLimitMiB = MutableStateFlow(SettingsDefaults.DEFAULT_PAGE_CACHE_MIB)
    val limitMiB = mutableLimitMiB.asStateFlow()
    @Volatile private var ready = false
    @Volatile private var images: CacheImageDiskCache? = null
    @Volatile var initialized = false
        private set

    init {
        scope.launch {
            for (ignored in requests) {
                delay(100L)
                runCatching { trim() }
            }
        }
    }

    fun initialize(imageCache: CacheImageDiskCache, limit: Int) {
        require(limit in SettingsDefaults.MIN_PAGE_CACHE_MIB..SettingsDefaults.MAX_PAGE_CACHE_MIB)
        images = imageCache
        mutableLimitMiB.value = if (BuildConfig.DEBUG) limit else SettingsDefaults.DEFAULT_PAGE_CACHE_MIB
        initialized = true
    }

    fun start() {
        ready = true
        trim()
    }

    fun touchImage(key: String) { images?.touch(key) }

    fun requestTrim() {
        if (ready) requests.trySend(Unit)
    }

    fun setLimitMiB(value: Int) = synchronized(trimLock) {
        require(value in SettingsDefaults.MIN_PAGE_CACHE_MIB..SettingsDefaults.MAX_PAGE_CACHE_MIB)
        val effectiveLimit = if (BuildConfig.DEBUG) value else SettingsDefaults.DEFAULT_PAGE_CACHE_MIB
        if (effectiveLimit != mutableLimitMiB.value) {
            CacheAnalysis.limitChanged(effectiveLimit, stats())
            mutableLimitMiB.value = effectiveLimit
        }
        trim()
    }

    fun stats(): CacheStorageStats {
        val pages = PageCache.stats()
        val snapshots = HomeDataCache.storageEntries() + HistoryDataCache.storageEntries()
        val imageCache = images
        return CacheStorageStats(pages.entryCount + snapshots.size + (imageCache?.entryCount ?: 0),
            pages.sizeBytes + snapshots.sumOf { it.sizeBytes } + (imageCache?.storageSizeBytes ?: 0L),
            limitMiB.value * 1024L * 1024L)
    }

    fun trim() = synchronized(trimLock) {
        var current = stats()
        CacheAnalysis.observe(current)
        if (current.sizeBytes > current.maxSizeBytes) {
            CacheAnalysis.evictionPass()
            val entries = PageCache.storageEntries() + HomeDataCache.storageEntries() +
                HistoryDataCache.storageEntries() + images?.storageEntries().orEmpty()
            try {
                reclaimCacheEntries(entries, current.sizeBytes, current.maxSizeBytes)
            } finally { images?.flushIndex() }
            current = stats()
        }
        CacheAnalysis.observe(current)
    }
}

/** Skip entries still in use; reclaim only enough committed bytes to satisfy the shared quota. */
internal fun reclaimCacheEntries(entries: List<CacheStorageEntry>, sizeBytes: Long, limitBytes: Long): Long {
    var remaining = sizeBytes
    for (entry in entries.sortedBy { it.accessedAt }) {
        if (remaining <= limitBytes) break
        if (entry.remove()) remaining -= entry.sizeBytes
    }
    return remaining.coerceAtLeast(0L)
}
