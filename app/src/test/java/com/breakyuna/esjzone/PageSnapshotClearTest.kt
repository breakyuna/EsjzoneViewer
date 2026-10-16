package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.features.HistoryDataCache
import com.breakyuna.esjzone.network.features.HomeDataCache
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PageSnapshotClearTest {
    private val authorization = Authorization("fixture-key", "fixture-token", "www.esjzone.cc")

    private fun withCache(block: (File) -> Unit) {
        val root = Files.createTempDirectory("page-snapshot-clear-").toFile()
        PageCache.initializeDirectory(File(root, "novel_page_cache"))
        HistoryDataCache.initializeDirectory(File(root, "history_cache").apply { mkdirs() })
        val scope = EsjzoneClient.accountScope(authorization)
        File(root, "home_data_snapshot.json").writeText("""{"domain":"$scope"}""")
        HomeDataCache.initializeDirectory(root)
        try { block(root) } finally {
            HomeDataCache.clearSnapshot()
            HistoryDataCache.clearAll()
            root.deleteRecursively()
        }
    }

    @Test
    fun pageClearRemovesSnapshotMemoryAndFilesAndRejectsLateHistoryWrites() = withCache { root ->
        assertNotNull(HomeDataCache.readSnapshot(authorization))
        HistoryDataCache.writeSnapshot(authorization, emptyList())
        assertNotNull(HistoryDataCache.readSnapshot(authorization))
        assertTrue(PageCache.write("page", "HTML"))
        File(root, "home_data_snapshot.json.tmp").writeText("temporary snapshot")
        val download = File(root, "downloaded_novels/chapter.txt").apply {
            parentFile!!.mkdirs()
            writeText("downloaded chapter")
        }
        val pendingEpoch = PageCache.currentEpoch
        EsjzoneClient.clearPageCache()
        assertNull(HomeDataCache.readSnapshot(authorization))
        assertNull(HistoryDataCache.readSnapshot(authorization))
        assertFalse(File(root, "home_data_snapshot.json").exists())
        assertFalse(File(root, "home_data_snapshot.json.tmp").exists())
        assertTrue(File(root, "history_cache").listFiles()!!.isEmpty())
        assertEquals(0, PageCache.stats().entryCount)
        assertEquals("downloaded chapter", download.readText())
        HistoryDataCache.writeSnapshot(authorization, emptyList(), pendingEpoch)
        assertNull(HistoryDataCache.readSnapshot(authorization))
        HistoryDataCache.writeSnapshot(authorization, emptyList(), PageCache.currentEpoch)
        assertNotNull(HistoryDataCache.readSnapshot(authorization))
    }

    @Test
    fun snapshotDeletionFailureIsReportedAndOtherCachesAreStillCleared() = withCache { root ->
        HistoryDataCache.writeSnapshot(authorization, emptyList())
        assertTrue(PageCache.write("page", "HTML"))
        val blockedTemporary = File(root, "home_data_snapshot.json.tmp").apply { mkdir() }
        File(blockedTemporary, "blocker").writeText("fixture")
        try {
            try {
                EsjzoneClient.clearPageCache()
                fail("A snapshot deletion failure must reach the caller")
            } catch (_: IOException) { }
            assertEquals(0, PageCache.stats().entryCount)
            assertNull(HomeDataCache.readSnapshot(authorization))
            assertNull(HistoryDataCache.readSnapshot(authorization))
            assertTrue(File(root, "history_cache").listFiles()!!.isEmpty())
        } finally { blockedTemporary.deleteRecursively() }
    }
}
