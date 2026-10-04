package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.StructuredChapterCache
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class StructuredChapterCacheTest {
    @Test
    fun parsedCacheInheritsResponseAgeAndIsInvalidatedByNewResponse() {
        val directory = Files.createTempDirectory("chapter-cache-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val key = "fixture|/forum/1/1.html"
            val url = "https://www.esjzone.cc/forum/1/1.html"
            val html = "<html><p>正文</p></html>"
            val fetchedAt = System.currentTimeMillis() - 1000L
            PageCache.write(key, html, fetchedAt)
            StructuredChapterCache.write(key, html, DetailedChapter("标题", listOf(TextComponent("正文")), null, null, "<p>正文</p>", url))
            val cached = requireNotNull(StructuredChapterCache.read(key, url))
            assertNotNull(cached.body)
            assertEquals(2, PageCache.stats().entryCount)
            assertNull(PageCache.read(PageCache.structuredKey(key), PageCacheTtl.CHAPTER, fetchedAt + PageCacheTtl.CHAPTER + 1))
            PageCache.write(key, "<html><p>新的正文</p></html>")
            assertNull(StructuredChapterCache.read(key, url))
            // A delayed parse of the old response cannot replace a newer cache generation.
            StructuredChapterCache.write(key, html, cached)
            assertNull(StructuredChapterCache.read(key, url))
            val newer = "<html><p>新的正文</p></html>"
            StructuredChapterCache.write(key, newer, cached)
            assertNotNull(StructuredChapterCache.read(key, url))
            PageCache.remove(key)
            assertNull(StructuredChapterCache.read(key, url))
            PageCache.clear()
            assertEquals(0, PageCache.stats().entryCount)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun chapterCacheIsAccountScopedAndMirrorSpecific() {
        val directory = Files.createTempDirectory("chapter-scope-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val key = "fixture-account-a|/forum/1/1.html"
            val url = "https://www.esjzone.cc/forum/1/1.html"
            PageCache.write(key, "html")
            StructuredChapterCache.write(key, "html", DetailedChapter("标题", listOf(TextComponent("正文")), null, null, sourceUrl = url))
            assertNull(StructuredChapterCache.read("fixture-account-b|/forum/1/1.html", url))
            assertNull(StructuredChapterCache.read(key, "https://www.esjzone.one/forum/1/1.html"))
        } finally { directory.deleteRecursively() }
    }
}
