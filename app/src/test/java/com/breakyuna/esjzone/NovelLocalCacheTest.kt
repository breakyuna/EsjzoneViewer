package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.NovelDetailCache
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.external.wenkuChapterCacheKey
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NovelLocalCacheTest {
    private val authorization = Authorization()
    private val detailUrl = "https://www.esjzone.cc/detail/9001.html"
    private val chapterUrl = "https://www.esjzone.cc/forum/9001/1.html"
    private val wenkuUrl = "https://www.wenku8.net/novel/1/9001/1.htm"

    private fun withCache(block: () -> Unit) {
        val directory = Files.createTempDirectory("novel-local-cache-").toFile()
        PageCache.initializeDirectory(directory)
        NovelDetailCache.clear()
        try { block() } finally {
            NovelDetailCache.clear()
            directory.deleteRecursively()
        }
    }

    @Test
    fun cleanupRemovesOnlyThisBooksPagesAndRejectsPendingWrites() = withCache {
        val detailKey = EsjzoneClient.pageCacheKey(authorization, detailUrl)
        val chapterKey = EsjzoneClient.pageCacheKey(authorization, chapterUrl)
        val wenkuKey = wenkuChapterCacheKey(wenkuUrl)
        val otherBookKey = EsjzoneClient.pageCacheKey(authorization, "https://www.esjzone.cc/detail/9002.html")
        val otherAccountKey = "other-account|${detailKey.substringAfter('|')}"
        val urls = listOf(detailUrl.replace(".cc", ".one"), chapterUrl, "$wenkuUrl#chapter")
        assertFalse(EsjzoneClient.hasCachedNovelPages(authorization, urls))
        listOf(detailKey, chapterKey, wenkuKey, otherBookKey, otherAccountKey).forEach {
            assertTrue(PageCache.write(it, "HTML"))
        }
        PageCache.writeDerived(chapterKey, "HTML", "parsed ESJ chapter")
        PageCache.writeDerived(wenkuKey, "HTML", "parsed Wenku chapter")
        val detail = DetailedNovel("Book", detailUrl, "", 0, 0, 0, "", "", "", emptyList(),
            false, true, NovelDescription(emptyList()), NovelChapterList(emptyList()))
        NovelDetailCache.write(detailKey, detail, requireNotNull(PageCache.readEntry(detailKey)).reference,
            NovelDetailCache.currentEpoch)
        val pending = PageCache.beginWrite(chapterKey)

        assertTrue(EsjzoneClient.hasCachedNovelPages(authorization, urls))
        assertTrue(EsjzoneClient.clearCachedNovelPages(authorization, urls))
        assertFalse(EsjzoneClient.hasCachedNovelPages(authorization, urls))
        assertNull(NovelDetailCache.read(detailKey))
        assertNull(PageCache.readStale(PageCache.structuredKey(chapterKey)))
        assertNull(PageCache.readStale(PageCache.structuredKey(wenkuKey)))
        assertFalse(PageCache.write(chapterKey, "late response", expected = pending))
        assertEquals("HTML", PageCache.readStale(otherBookKey))
        assertEquals("HTML", PageCache.readStale(otherAccountKey))
    }

    @Test
    fun detectsAndClearsDerivedChapterWithoutRawHtml() = withCache {
        val chapterKey = EsjzoneClient.pageCacheKey(authorization, chapterUrl)
        assertTrue(PageCache.write(PageCache.structuredKey(chapterKey), "parsed chapter"))
        assertTrue(EsjzoneClient.hasCachedNovelPages(authorization, listOf(chapterUrl)))
        assertTrue(EsjzoneClient.clearCachedNovelPages(authorization, listOf(chapterUrl)))
        assertEquals(0, PageCache.stats().entryCount)
        assertFalse(EsjzoneClient.hasCachedNovelPages(authorization, listOf(chapterUrl)))
    }
}
