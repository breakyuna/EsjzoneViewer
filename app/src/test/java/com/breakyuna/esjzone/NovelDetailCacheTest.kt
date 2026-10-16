package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.NovelDetailCache
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NovelDetailCacheTest {
    private fun novel(name: String) = DetailedNovel(name, "/detail/9001.html", "", 0, 0, 0,
        "", "", "", emptyList(), false, false, NovelDescription(emptyList()), NovelChapterList(emptyList()))

    private fun withCache(block: () -> Unit) {
        val directory = Files.createTempDirectory("parsed-detail-cache-").toFile()
        PageCache.initializeDirectory(directory)
        NovelDetailCache.clear()
        try { block() } finally {
            NovelDetailCache.clear()
            directory.deleteRecursively()
        }
    }

    @Test
    fun lateOldParsingCannotOverwriteNewlyCachedDetails() = withCache {
        val epoch = NovelDetailCache.currentEpoch
        assertTrue(PageCache.write("detail", "old HTML"))
        val oldSource = requireNotNull(PageCache.readEntry("detail")).reference
        assertTrue(PageCache.write("detail", "new HTML"))
        val newSource = requireNotNull(PageCache.readEntry("detail")).reference
        val newer = novel("New chapters")
        NovelDetailCache.write("detail", newer, newSource, epoch)
        NovelDetailCache.write("detail", novel("Old chapters"), oldSource, epoch)
        assertEquals(newer, NovelDetailCache.read("detail"))
    }

    @Test
    fun parsedCacheClearRejectsPendingParsingWhileKeepingSourceHtml() = withCache {
        assertTrue(PageCache.write("detail", "HTML"))
        val source = requireNotNull(PageCache.readEntry("detail")).reference
        val oldEpoch = NovelDetailCache.currentEpoch
        NovelDetailCache.clear()
        NovelDetailCache.write("detail", novel("Late result"), source, oldEpoch)
        assertNull(NovelDetailCache.read("detail"))
        val fresh = novel("Fresh result")
        NovelDetailCache.write("detail", fresh, source, NovelDetailCache.currentEpoch)
        assertEquals(fresh, NovelDetailCache.read("detail"))
    }

    @Test
    fun removedAndRecreatedHtmlDoesNotRevivePendingParsing() = withCache {
        assertTrue(PageCache.write("detail", "HTML"))
        val source = requireNotNull(PageCache.readEntry("detail")).reference
        PageCache.remove("detail")
        assertTrue(PageCache.write("detail", "HTML"))
        NovelDetailCache.write("detail", novel("Late result"), source, NovelDetailCache.currentEpoch)
        assertNull(NovelDetailCache.read("detail"))
    }
}
