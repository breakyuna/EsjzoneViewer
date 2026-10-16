package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.CacheDiagnostics
import com.breakyuna.esjzone.network.CacheEventReason
import com.breakyuna.esjzone.network.CacheEventType
import com.breakyuna.esjzone.network.CacheLayer
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.PageKind
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CacheDiagnosticsTest {
    private fun withTrace(block: () -> Unit) {
        CacheDiagnostics.setRecording(false)
        CacheDiagnostics.clear()
        try { block() } finally {
            CacheDiagnostics.setRecording(false)
            CacheDiagnostics.clear()
        }
    }

    private fun hit(key: String) = CacheDiagnostics.record(
        CacheEventType.HIT, CacheLayer.PAGE, CacheEventReason.FRESH, key)

    @Test
    fun recordingCanBePausedAndClearedWithoutChangingCacheContent() = withTrace {
        val directory = Files.createTempDirectory("cache-trace-toggle-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            hit("ignored")
            assertTrue(CacheDiagnostics.state.value.events.isEmpty())
            CacheDiagnostics.setRecording(true)
            assertTrue(PageCache.write("fixture", "cached content"))
            val recorded = CacheDiagnostics.state.value.events
            CacheDiagnostics.setRecording(false)
            hit("ignored while paused")
            assertEquals(recorded, CacheDiagnostics.state.value.events)
            CacheDiagnostics.setRecording(true)
            CacheDiagnostics.clear()
            assertTrue(CacheDiagnostics.state.value.recording)
            assertTrue(CacheDiagnostics.state.value.events.isEmpty())
            assertEquals("cached content", PageCache.readStale("fixture"))
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun retainsOnlyTheLatest500EventsWithUniqueIncreasingIds() = withTrace {
        CacheDiagnostics.setRecording(true)
        repeat(510) { hit("fixture-$it") }
        val events = CacheDiagnostics.state.value.events
        assertEquals(500, events.size)
        assertTrue(events.zipWithNext().all { (first, second) -> first.id < second.id })
        val lastId = events.last().id
        CacheDiagnostics.clear()
        hit("after clear")
        assertTrue(CacheDiagnostics.state.value.events.single().id > lastId)
    }

    @Test
    fun onlyDigestsAreRetainedAndFileIdsMatchKeyIds() = withTrace {
        CacheDiagnostics.setRecording(true)
        val key = "fixture-account|https://example.invalid/tags?keyword=fixture-search"
        hit(key)
        val first = CacheDiagnostics.state.value.events.single()
        assertTrue(requireNotNull(first.entryId).matches(Regex("[a-f0-9]{12}")))
        assertFalse(first.toString().contains("fixture-account"))
        assertFalse(first.toString().contains("fixture-search"))
        CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.PAGE, CacheEventReason.CAPACITY,
            fileId = first.entryId + "0".repeat(52))
        assertEquals(first.entryId, CacheDiagnostics.state.value.events.last().entryId)
        CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.PAGE, CacheEventReason.DELETE_FAILED,
            fileId = "untrusted/raw/path")
        assertNull(CacheDiagnostics.state.value.events.last().entryId)
    }

    @Test
    fun capacityReclamationRecordsTheDeletedFileAndItsSize() = withTrace {
        val directory = Files.createTempDirectory("cache-trace-capacity-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            assertTrue(PageCache.write("fixture", "cached content"))
            val file = directory.listFiles()!!.single()
            val oversize = PageCache.stats().maxSizeBytes + 1L
            RandomAccessFile(file, "rw").use { it.setLength(oversize) }
            CacheDiagnostics.setRecording(true)
            PageCache.initializeDirectory(directory)
            val event = CacheDiagnostics.state.value.events.single()
            assertEquals(CacheEventType.EVICT, event.type)
            assertEquals(CacheEventReason.CAPACITY, event.reason)
            assertEquals(oversize, requireNotNull(event.bytes))
            assertFalse(file.exists())
            assertEquals(0L, PageCache.stats().sizeBytes)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun recordsOnlyCommittedUpdatesAndSuccessfulHitsAndReclamation() = withTrace {
        val directory = Files.createTempDirectory("cache-trace-events-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val authorization = Authorization()
            val url = "https://www.esjzone.cc/list-01"
            val key = EsjzoneClient.pageCacheKey(authorization, url)
            val html = "<html><head><title>ESJZone</title></head><body><main>" +
                "<a href='/detail/9001.html'>Novel</a></main></body></html>"
            CacheDiagnostics.setRecording(true)
            assertTrue(PageCache.write(key, html))
            assertEquals(html, EsjzoneClient.getPage(authorization, url, 60_000L, pageKind = PageKind.LIST))
            val reference = requireNotNull(PageCache.readEntry(key)).reference
            val file = directory.listFiles()!!.single()
            val blockedTemporary = File(directory, "${file.name}.tmp").apply { mkdir() }
            File(blockedTemporary, "blocker").writeText("fixture")
            assertFalse(PageCache.write(key, "replacement"))
            assertEquals(html, PageCache.readStale(key))
            assertTrue(blockedTemporary.deleteRecursively())
            PageCache.removeReference(reference)
            val events = CacheDiagnostics.state.value.events
            assertEquals(listOf(CacheEventType.UPDATE, CacheEventType.HIT, CacheEventType.FAILURE, CacheEventType.EVICT),
                events.map { it.type })
            assertEquals(CacheEventReason.WRITE_FAILED, events[2].reason)
            assertEquals(CacheEventReason.DOWNLOADED, events[3].reason)
            assertTrue(requireNotNull(events.first().bytes) > 0L)
            assertEquals(events.first().entryId, events.last().entryId)
            assertNull(PageCache.readStale(key))
        } finally { directory.deleteRecursively() }
    }
}
