package com.breakyuna.esjzone

import coil3.disk.DiskCache
import com.breakyuna.esjzone.network.CacheImageDiskCache
import com.breakyuna.esjzone.network.CacheStorageEntry
import com.breakyuna.esjzone.network.reclaimCacheEntries
import com.breakyuna.esjzone.network.features.HomeDataCache
import com.breakyuna.esjzone.network.features.HistoryDataCache
import java.io.File
import java.nio.file.Files
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test

class CacheStorageTest {
    @Test
    fun reclaimsAcrossLayersByAccessTimeAndSkipsActiveEntries() {
        val removed = mutableListOf<String>()
        fun entry(name: String, bytes: Long, age: Long, available: Boolean = true) =
            CacheStorageEntry(bytes, age) { if (available) removed.add(name) else false }
        val entries = listOf(entry("page", 100, 4), entry("image", 600, 1, available = false),
            entry("home", 200, 2), entry("history", 100, 3))
        assertEquals(700L, reclaimCacheEntries(entries, 1000, 700))
        assertEquals(listOf("home", "history"), removed)
        removed.clear()
        assertEquals(1000L, reclaimCacheEntries(entries, 1000, 1000))
        assertTrue(removed.isEmpty())
    }

    private fun imageCache(directory: File) = CacheImageDiskCache(DiskCache.Builder()
        .directory(directory.toOkioPath()).maxSizeBytes(Long.MAX_VALUE).build())

    @Test
    fun imageInventorySurvivesRestartAndNeverStoresRawKeys() {
        val directory = Files.createTempDirectory("shared-image-cache-").toFile()
        val key = "https://example.invalid/cover.jpg"
        var cache = imageCache(directory)
        try {
            val editor = requireNotNull(cache.openEditor(key))
            File(editor.metadata.toString()).writeText("metadata")
            File(editor.data.toString()).writeText("image")
            editor.commit()
            assertEquals(1, cache.entryCount)
            assertFalse(File(directory, "cache-keys-v1").readText().contains(key))
            cache.shutdown()
            cache = imageCache(directory)
            requireNotNull(cache.openSnapshot(key)).use { snapshot ->
                assertEquals("image", File(snapshot.data.toString()).readText())
            }
            assertEquals(1, cache.entryCount)
            assertTrue(cache.storageEntries().single().remove())
            assertNull(cache.openSnapshot(key))
            assertEquals(0, cache.entryCount)
        } finally {
            cache.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun capacityEvictionWaitsForImageReadersAndWriters() {
        val directory = Files.createTempDirectory("shared-image-in-use-").toFile()
        val cache = imageCache(directory)
        val key = "https://example.invalid/cover.jpg"
        try {
            val editor = requireNotNull(cache.openEditor(key))
            File(editor.metadata.toString()).writeText("metadata")
            File(editor.data.toString()).writeText("image")
            val snapshot = requireNotNull(editor.commitAndOpenSnapshot())
            assertFalse(cache.storageEntries().single().remove())
            val replacement = requireNotNull(snapshot.closeAndOpenEditor())
            assertFalse(cache.storageEntries().single().remove())
            replacement.abort()
            assertTrue(cache.storageEntries().single().remove())
            assertNull(cache.openSnapshot(key))
        } finally {
            cache.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun globalEvictionRemovesBothSnapshotLayersWithoutTouchingDownloads() {
        val directory = Files.createTempDirectory("shared-snapshots-").toFile()
        try {
            val home = File(directory, "home_data_snapshot.json").apply {
                writeText("{\"domain\":\"example.invalid\"}")
            }
            val histories = File(directory, "history_cache").apply { mkdir() }
            val history = File(histories, "history_snapshot_fixture.json").apply { writeText("{}") }
            val download = File(directory, "downloaded_novels/chapter.json").apply {
                parentFile!!.mkdirs()
                writeText("download")
            }
            HomeDataCache.initializeDirectory(directory)
            HistoryDataCache.initializeDirectory(histories)
            val entries = HomeDataCache.storageEntries() + HistoryDataCache.storageEntries()
            assertEquals(2, entries.size)
            assertEquals(0L, reclaimCacheEntries(entries, entries.sumOf { it.sizeBytes }, 0))
            assertFalse(home.exists())
            assertFalse(history.exists())
            assertEquals("download", download.readText())
            assertTrue(HomeDataCache.storageEntries().isEmpty())
            assertTrue(HistoryDataCache.storageEntries().isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
