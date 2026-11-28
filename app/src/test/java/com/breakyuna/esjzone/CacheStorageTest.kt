package com.breakyuna.esjzone

import coil3.disk.DiskCache
import com.breakyuna.esjzone.network.CacheImageDiskCache
import com.breakyuna.esjzone.network.CacheStorageEntry
import com.breakyuna.esjzone.network.reclaimCacheEntries
import com.breakyuna.esjzone.network.features.HomeDataCache
import com.breakyuna.esjzone.network.features.HistoryDataCache
import java.io.File
import java.nio.file.Files
import okio.ByteString.Companion.encodeUtf8
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

    private fun imageCache(directory: File, maxSizeBytes: Long = 16L * 1024L * 1024L) =
        CacheImageDiskCache(DiskCache.Builder()
            .directory(directory.toOkioPath()).maxSizeBytes(maxSizeBytes).build())

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

    @Test
    fun reclaimCacheEntriesPurgesAlreadyEvictedImagesWithoutCountingPhantomBytes() {
        val directory = Files.createTempDirectory("shared-image-native-evict-").toFile()
        val disk = DiskCache.Builder().directory(directory.toOkioPath()).maxSizeBytes(16L * 1024L * 1024L).build()
        val cache = CacheImageDiskCache(disk)
        try {
            fun put(key: String, payload: String) {
                val editor = requireNotNull(cache.openEditor(key))
                File(editor.metadata.toString()).writeText("metadata")
                File(editor.data.toString()).writeText(payload)
                editor.commit()
            }
            put("evicted-by-coil", "old-image-payload")
            put("retained", "active-image-payload")
            assertEquals(2, cache.entryCount)

            assertTrue(disk.remove("evicted-by-coil".encodeUtf8().sha256().hex()))
            requireNotNull(cache.openSnapshot("retained")).use {
                val entries = cache.storageEntries()
                val totalBytes = entries.sumOf { it.sizeBytes }
                assertEquals(2, entries.size)
                assertEquals(totalBytes, reclaimCacheEntries(entries, totalBytes, 0L))
            }
            assertEquals(1, cache.entryCount)
            requireNotNull(cache.openSnapshot("retained")).use {
                assertEquals("active-image-payload", File(it.data.toString()).readText())
            }
        } finally {
            cache.shutdown()
            directory.deleteRecursively()
        }
    }
}
