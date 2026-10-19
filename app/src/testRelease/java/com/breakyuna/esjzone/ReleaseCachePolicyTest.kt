package com.breakyuna.esjzone

import coil3.disk.DiskCache
import com.breakyuna.esjzone.network.CacheDiagnostics
import com.breakyuna.esjzone.network.CacheEventReason
import com.breakyuna.esjzone.network.CacheEventType
import com.breakyuna.esjzone.network.CacheImageDiskCache
import com.breakyuna.esjzone.network.CacheLayer
import com.breakyuna.esjzone.network.CacheStorage
import com.breakyuna.esjzone.network.PageCache
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test

class ReleaseCachePolicyTest {
    @Test
    fun recordingCannotBeEnabled() {
        CacheDiagnostics.clear()
        try {
            CacheDiagnostics.setRecording(true)
            CacheDiagnostics.record(CacheEventType.HIT, CacheLayer.PAGE, CacheEventReason.FRESH, "fixture")
            assertFalse(CacheDiagnostics.state.value.recording)
            assertTrue(CacheDiagnostics.state.value.events.isEmpty())
        } finally {
            CacheDiagnostics.setRecording(false)
            CacheDiagnostics.clear()
        }
    }

    @Test
    fun quotaStaysAt128MiBAndStillEvictsWhileRecordingIsDisabled() {
        val directory = Files.createTempDirectory("release-cache-quota-").toFile()
        val images = CacheImageDiskCache(DiskCache.Builder()
            .directory(File(directory, "images").toOkioPath()).maxSizeBytes(Long.MAX_VALUE).build())
        try {
            PageCache.initializeDirectory(File(directory, "pages"))
            CacheStorage.initialize(images, 256)
            assertEquals(128, CacheStorage.limitMiB.value)
            assertEquals(128L * 1024L * 1024L, CacheStorage.stats().maxSizeBytes)
            assertTrue(PageCache.write("fixture", "cached content"))
            val page = File(directory, "pages").listFiles()!!.single()
            RandomAccessFile(page, "rw").use { it.setLength(128L * 1024L * 1024L + 1L) }
            PageCache.initializeDirectory(File(directory, "pages"))

            CacheStorage.setLimitMiB(512)

            assertEquals(128, CacheStorage.limitMiB.value)
            assertFalse(page.exists())
            assertEquals(0L, CacheStorage.stats().sizeBytes)
            assertFalse(CacheDiagnostics.state.value.recording)
        } finally {
            images.shutdown()
            directory.deleteRecursively()
        }
    }
}
