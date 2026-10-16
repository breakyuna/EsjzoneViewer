package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.CacheAnalysisJournal
import com.breakyuna.esjzone.network.CacheCounters
import com.breakyuna.esjzone.network.CacheSample
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CacheAnalysisJournalTest {
    private fun sample(limit: Int, start: Long, hits: Long = 8L, misses: Long = 2L) = CacheSample(
        start, start + 3_600_000L, 3_600_000L, limit, 1024L, 2048L, 1,
        mapOf("PAGE" to CacheCounters(freshHits = hits, misses = misses, forcedRefreshes = 5L,
            capacityEvictions = 2L, evictedBytes = 512L)), capacityPasses = 1L)

    @Test
    fun comparesLimitsSeparatelyAndExcludesForcedRefreshesFromHitRate() {
        val directory = Files.createTempDirectory("cache-analysis-limits-").toFile()
        try {
            val journal = CacheAnalysisJournal(directory)
            journal.commit(listOf(sample(100, 0L).copy(nextLimitMiB = 50), sample(50, 3_600_000L, 2L, 8L)))
            val restored = CacheAnalysisJournal(directory).read()
            assertEquals(listOf(50, 100), restored.totals.map { it.limitMiB })
            val larger = restored.totals.single { it.limitMiB == 100 }
            assertEquals(0.8, requireNotNull(larger.freshHitRates["PAGE"]), 0.0001)
            assertEquals(2.0, requireNotNull(larger.capacityEvictionsPerHour), 0.0001)
            assertEquals(200.0, requireNotNull(larger.capacityEvictionsPer1000Lookups), 0.0001)
            assertEquals(50, requireNotNull(restored.recent.first().nextLimitMiB))
            assertEquals(0.2, requireNotNull(restored.totals.first().freshHitRates["PAGE"]), 0.0001)
            assertNull(CacheCounters(forcedRefreshes = 10L).hitRate())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun trimsRecentWindowsButPermanentlyRetainsTheirTotalsAcrossReopening() {
        val directory = Files.createTempDirectory("cache-analysis-retention-").toFile()
        try {
            val count = CacheAnalysisJournal.MAX_RECENT + 5
            val samples = (0 until count).map { sample(100, it * 3_600_000L) }
            val journal = CacheAnalysisJournal(directory)
            journal.commit(samples)
            val restored = CacheAnalysisJournal(directory).read()
            assertEquals(CacheAnalysisJournal.MAX_RECENT, restored.recent.size)
            assertEquals(count * 8L, restored.totals.single().layers.getValue("PAGE").freshHits)
            assertEquals(count * 3_600_000L, restored.totals.single().durationMillis)
            assertTrue(journal.file.length() <= CacheAnalysisJournal.MAX_BYTES)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun failedCommitPreservesOldLogAndRetryDoesNotDoubleCount() {
        val directory = Files.createTempDirectory("cache-analysis-retry-").toFile()
        try {
            val journal = CacheAnalysisJournal(directory)
            journal.commit(listOf(sample(100, 0L)))
            val before = journal.file.readText()
            val blockedTemporary = File(directory, "cache-analysis.json.tmp").apply { mkdir() }
            File(blockedTemporary, "blocker").writeText("fixture")
            try {
                journal.commit(listOf(sample(100, 3_600_000L)))
                fail("A failed journal write must report failure")
            } catch (_: IOException) { }
            assertEquals(before, journal.file.readText())
            assertEquals(8L, CacheAnalysisJournal(directory).read().totals.single().layers.getValue("PAGE").freshHits)
            assertTrue(blockedTemporary.deleteRecursively())
            journal.commit(listOf(sample(100, 3_600_000L)))
            assertEquals(16L, CacheAnalysisJournal(directory).read().totals.single().layers.getValue("PAGE").freshHits)
        } finally { directory.deleteRecursively() }
    }
}
