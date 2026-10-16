package com.breakyuna.esjzone.network

import android.content.Context
import androidx.annotation.Keep
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Keep
internal data class CacheCounters(
    val freshHits: Long = 0, val misses: Long = 0, val forcedRefreshes: Long = 0,
    val staleUses: Long = 0, val updates: Long = 0, val writtenBytes: Long = 0,
    val capacityEvictions: Long = 0, val evictedBytes: Long = 0,
    val otherRemovals: Long = 0, val failures: Long = 0
) {
    fun plus(other: CacheCounters) = CacheCounters(freshHits + other.freshHits, misses + other.misses,
        forcedRefreshes + other.forcedRefreshes, staleUses + other.staleUses, updates + other.updates,
        writtenBytes + other.writtenBytes, capacityEvictions + other.capacityEvictions,
        evictedBytes + other.evictedBytes, otherRemovals + other.otherRemovals, failures + other.failures)

    fun hitRate(): Double? = (freshHits + misses).takeIf { it > 0L }?.let { freshHits.toDouble() / it }
}

@Keep
internal data class CacheSample(
    val startMillis: Long, val endMillis: Long, val durationMillis: Long, val limitMiB: Int,
    val sizeBytes: Long, val peakBytes: Long, val entries: Int,
    val layers: Map<String, CacheCounters>, val capacityPasses: Long = 0,
    val nextLimitMiB: Int? = null,
    val freshHitRates: Map<String, Double?> = layers.mapValues { it.value.hitRate() },
    val capacityEvictionsPerHour: Double? = durationMillis.takeIf { it > 0L }?.let { duration ->
        layers.values.sumOf { it.capacityEvictions } * 3_600_000.0 / duration
    },
    val capacityEvictionsPer1000Lookups: Double? = layers.values.sumOf { it.freshHits + it.misses }
        .takeIf { it > 0L }?.let { count -> layers.values.sumOf { it.capacityEvictions } * 1000.0 / count }
) {
    fun merge(other: CacheSample): CacheSample {
        require(limitMiB == other.limitMiB)
        val latest = if (endMillis > other.endMillis) this else other
        val combined = (layers.keys + other.layers.keys).associateWith { key ->
            (layers[key] ?: CacheCounters()).plus(other.layers[key] ?: CacheCounters())
        }
        return CacheSample(minOf(startMillis, other.startMillis), maxOf(endMillis, other.endMillis),
            durationMillis + other.durationMillis, limitMiB, latest.sizeBytes,
            maxOf(peakBytes, other.peakBytes), latest.entries, combined, capacityPasses + other.capacityPasses)
    }
}

@Keep
internal data class CacheAnalysisData(
    val schemaVersion: Int = 1, val totals: List<CacheSample> = emptyList(),
    val recent: List<CacheSample> = emptyList()
)

/** Bounded recent windows plus permanent per-limit totals; never stores individual cache keys. */
internal class CacheAnalysisJournal(private val directory: File) {
    companion object {
        const val MAX_BYTES = 8L * 1024L * 1024L
        const val MAX_RECENT = 2016
        const val WINDOW_MILLIS = 5L * 60L * 1000L
    }

    private val gson = Gson()
    val file = File(directory, "cache-analysis.json")
    private var loaded: CacheAnalysisData? = null

    fun read(): CacheAnalysisData {
        loaded?.let { return it }
        val data = if (!file.exists()) CacheAnalysisData() else {
            if (file.length() > MAX_BYTES) throw IOException("Cache analysis file exceeds its limit")
            file.reader(Charsets.UTF_8).use { requireNotNull(gson.fromJson(it, CacheAnalysisData::class.java)) }
        }
        require(data.schemaVersion == 1)
        require(data.totals.size <= SettingsDefaults.MAX_PAGE_CACHE_MIB)
        require(data.recent.size <= MAX_RECENT)
        (data.totals + data.recent).forEach { sample ->
            require(sample.limitMiB in SettingsDefaults.MIN_PAGE_CACHE_MIB..SettingsDefaults.MAX_PAGE_CACHE_MIB)
            require(sample.durationMillis >= 0L)
        }
        return data.also { loaded = it }
    }

    fun commit(samples: List<CacheSample>): CacheAnalysisData {
        val old = read()
        val totals = old.totals.associateBy { it.limitMiB }.toMutableMap()
        val recent = old.recent.toMutableList()
        samples.forEach { sample ->
            require(sample.limitMiB in SettingsDefaults.MIN_PAGE_CACHE_MIB..SettingsDefaults.MAX_PAGE_CACHE_MIB)
            totals[sample.limitMiB] = totals[sample.limitMiB]?.merge(sample) ?: sample.copy(nextLimitMiB = null)
            val last = recent.lastOrNull()
            if (last != null && last.limitMiB == sample.limitMiB && last.nextLimitMiB == null &&
                sample.nextLimitMiB == null && last.startMillis / WINDOW_MILLIS == sample.startMillis / WINDOW_MILLIS) {
                recent[recent.lastIndex] = last.merge(sample)
            } else recent += sample
        }
        var data = CacheAnalysisData(totals = totals.values.sortedBy { it.limitMiB }, recent = recent.takeLast(MAX_RECENT))
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cache analysis storage unavailable")
        val temporary = File(directory, "cache-analysis.json.tmp")
        try {
            while (true) {
                temporary.writer(Charsets.UTF_8).use { gson.toJson(data, it) }
                if (temporary.length() <= MAX_BYTES) break
                if (data.recent.isEmpty()) throw IOException("Cache analysis totals exceed storage limit")
                data = data.copy(recent = data.recent.takeLast(data.recent.size / 2))
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            loaded = data
            return data
        } finally { temporary.delete() }
    }
}

internal data class CacheAnalysisState(
    val current: CacheSample? = null, val logBytes: Long = 0L, val storageError: Boolean = false
)

/** Counters are independent of the optional 500-event live trace and AppLogger. */
internal object CacheAnalysis {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val ioLock = Any()
    private var initialized = false
    private var journal: CacheAnalysisJournal? = null
    private var limitMiB = SettingsDefaults.DEFAULT_PAGE_CACHE_MIB
    private var startedAt = 0L
    private var startedNanos = 0L
    private var peakBytes = 0L
    private var capacityPasses = 0L
    private val counters = mutableMapOf<String, CacheCounters>()
    private val pending = mutableMapOf<Int, CacheSample>()
    private val mutableState = MutableStateFlow(CacheAnalysisState())
    val state = mutableState.asStateFlow()

    fun initialize(context: Context) {
        synchronized(ioLock) {
            if (initialized) return
            val storage = CacheAnalysisJournal(File(context.filesDir, "cache_analysis"))
            val data = runCatching { storage.read() }
            val stats = PageCache.stats()
            synchronized(lock) {
                journal = storage
                limitMiB = PageCache.limitMiB.value
                startedAt = System.currentTimeMillis()
                startedNanos = System.nanoTime()
                peakBytes = stats.sizeBytes
                pending[limitMiB] = CacheSample(startedAt, startedAt, 0L, limitMiB,
                    stats.sizeBytes, stats.sizeBytes, stats.entryCount, emptyMap())
                initialized = true
            }
            mutableState.value = CacheAnalysisState(data.getOrNull()?.totals?.firstOrNull { it.limitMiB == limitMiB },
                storage.file.length(), data.isFailure)
        }
        scope.launch {
            while (true) {
                delay(60_000L)
                checkpoint()
            }
        }
    }

    private fun tracked(layer: CacheLayer) = layer == CacheLayer.PAGE || layer == CacheLayer.CHAPTER || layer == CacheLayer.LIST

    fun lookup(layer: CacheLayer, fresh: Boolean, forced: Boolean = false) = synchronized(lock) {
        if (!initialized || !tracked(layer)) return@synchronized
        val old = counters[layer.name] ?: CacheCounters()
        counters[layer.name] = when {
            forced -> old.copy(forcedRefreshes = old.forcedRefreshes + 1L)
            fresh -> old.copy(freshHits = old.freshHits + 1L)
            else -> old.copy(misses = old.misses + 1L)
        }
    }

    fun record(type: CacheEventType, layer: CacheLayer, reason: CacheEventReason, bytes: Long?) = synchronized(lock) {
        if (!initialized || !tracked(layer)) return@synchronized
        val old = counters[layer.name] ?: CacheCounters()
        val size = bytes?.coerceAtLeast(0L) ?: 0L
        counters[layer.name] = when (type) {
            CacheEventType.HIT -> if (reason == CacheEventReason.STALE) old.copy(staleUses = old.staleUses + 1L) else old
            CacheEventType.UPDATE -> old.copy(updates = old.updates + 1L, writtenBytes = old.writtenBytes + size)
            CacheEventType.EVICT -> if (reason == CacheEventReason.CAPACITY) {
                old.copy(capacityEvictions = old.capacityEvictions + 1L, evictedBytes = old.evictedBytes + size)
            } else old.copy(otherRemovals = old.otherRemovals + 1L)
            CacheEventType.FAILURE -> old.copy(failures = old.failures + 1L)
        }
    }

    fun observe(stats: PageCacheStats) = synchronized(lock) {
        if (initialized && stats.maxSizeBytes / (1024L * 1024L) == limitMiB.toLong()) {
            peakBytes = maxOf(peakBytes, stats.sizeBytes)
        }
    }

    fun evictionPass() = synchronized(lock) {
        if (initialized) capacityPasses += 1L
    }

    // Called with the PageCache lock held. No journal IO or reverse PageCache calls here.
    fun limitChanged(value: Int, oldStats: PageCacheStats) = synchronized(lock) {
        if (!initialized) return@synchronized
        val sample = capture(oldStats, nextLimitMiB = value)
        queue(sample)
        limitMiB = value
        peakBytes = oldStats.sizeBytes
        requestCheckpoint()
    }

    private fun capture(stats: PageCacheStats, nextLimitMiB: Int? = null): CacheSample {
        val now = System.currentTimeMillis()
        val nanos = System.nanoTime()
        val sample = CacheSample(startedAt, now, ((nanos - startedNanos) / 1_000_000L).coerceAtLeast(0L),
            limitMiB, stats.sizeBytes, maxOf(peakBytes, stats.sizeBytes), stats.entryCount,
            counters.toMap(), capacityPasses, nextLimitMiB)
        startedAt = now
        startedNanos = nanos
        counters.clear()
        capacityPasses = 0L
        peakBytes = stats.sizeBytes
        return sample
    }

    private fun queue(sample: CacheSample) {
        val old = pending[sample.limitMiB]
        pending[sample.limitMiB] = if (old == null) sample else {
            val next = if (sample.endMillis >= old.endMillis) sample.nextLimitMiB ?: old.nextLimitMiB
                else old.nextLimitMiB ?: sample.nextLimitMiB
            old.merge(sample).copy(nextLimitMiB = next)
        }
    }

    fun requestCheckpoint() { scope.launch { checkpoint() } }

    fun checkpoint(): Boolean = synchronized(ioLock) {
        val stats = PageCache.stats()
        val batch = synchronized(lock) {
            if (!initialized || (pending.isEmpty() && counters.values.all { it == CacheCounters() } && capacityPasses == 0L)) {
                emptyList()
            } else {
                // A limit change may have closed the previous interval after stats() returned.
                val sample = if (stats.maxSizeBytes / (1024L * 1024L) == limitMiB.toLong()) capture(stats) else null
                (pending.values.toList() + listOfNotNull(sample)).sortedBy { it.endMillis }.also { pending.clear() }
            }
        }
        if (batch.isEmpty()) return@synchronized true
        val storage = journal ?: return@synchronized false
        try {
            val data = storage.commit(batch)
            mutableState.value = CacheAnalysisState(data.totals.firstOrNull { it.limitMiB == PageCache.limitMiB.value },
                storage.file.length(), false)
            true
        } catch (_: Exception) {
            synchronized(lock) {
                batch.forEach(::queue)
            }
            mutableState.value = mutableState.value.copy(storageError = true)
            false
        }
    }

    fun export(output: OutputStream) = synchronized(ioLock) {
        if (!checkpoint()) throw IOException("Cache analysis could not be saved")
        val storage = journal ?: throw IOException("Cache analysis is not initialized")
        storage.file.inputStream().use { it.copyTo(output) }
    }
}
