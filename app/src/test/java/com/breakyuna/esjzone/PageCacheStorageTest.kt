package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.StructuredChapterCache
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import org.junit.Assert.*
import org.junit.Test

class PageCacheStorageTest {
    @Test
    fun compressedCachePreservesTextAndCountsPhysicalBytes() {
        val directory = Files.createTempDirectory("page-storage-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val html = "<p>正文与注音 ruby</p>\n".repeat(2000)
            val timestamp = System.currentTimeMillis()
            PageCache.write("fixture", html, timestamp)
            val file = directory.listFiles()!!.single()
            val decoded = GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }
            assertEquals("$timestamp\n$html", decoded)
            assertEquals(html, PageCache.read("fixture", PageCacheTtl.CHAPTER, timestamp))
            assertTrue(file.length() < html.toByteArray(Charsets.UTF_8).size / 5)
            assertEquals(file.length(), PageCache.stats().sizeBytes)
            assertEquals(1, PageCache.stats().entryCount)
            PageCache.remove("fixture")
            assertEquals(0L, PageCache.stats().sizeBytes)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun legacyTextKeepsExpiryAndSupportsDerivedSnapshots() {
        val directory = Files.createTempDirectory("legacy-page-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val key = "fixture"
            val url = "https://www.esjzone.cc/forum/9001/1.html"
            val timestamp = System.currentTimeMillis() - 1000
            val html = "<html><p>旧缓存正文</p></html>"
            PageCache.write(key, html, timestamp)
            directory.listFiles()!!.single().writeText("$timestamp\n$html")
            PageCache.initializeDirectory(directory)
            assertEquals(html, PageCache.read(key, PageCacheTtl.CHAPTER))
            assertNull(PageCache.read(key, PageCacheTtl.CHAPTER, timestamp + PageCacheTtl.CHAPTER + 1))
            assertEquals(html, PageCache.readStale(key))
            StructuredChapterCache.write(key, html, DetailedChapter("标题", listOf(TextComponent("旧缓存正文")),
                null, null, "<p>旧缓存正文</p>", url))
            assertNotNull(StructuredChapterCache.read(key, url))
            assertEquals(directory.listFiles()!!.sumOf { it.length() }, PageCache.stats().sizeBytes)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun failedReplacementPreservesResponseAndDerivedCache() {
        val directory = Files.createTempDirectory("failed-page-commit-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            PageCache.write("page", "old")
            PageCache.writeDerived("page", "old", "parsed-old")
            val source = directory.listFiles()!!.first { file ->
                GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText().endsWith("\nold") }
            }
            val blockedTemporary = java.io.File(directory, "${source.name}.tmp")
            blockedTemporary.mkdirs()
            java.io.File(blockedTemporary, "blocker").writeText("fixture")
            assertFalse(PageCache.write("page", "new"))
            assertEquals("old", PageCache.readStale("page"))
            assertEquals("parsed-old", PageCache.readStale(PageCache.structuredKey("page")))
            assertEquals(2, PageCache.stats().entryCount)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun delayedRequestsCannotOverwriteNewerRequestOrRepopulateClearedCache() {
        val directory = Files.createTempDirectory("page-generations-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val old = PageCache.beginWrite("page")
            val current = PageCache.beginWrite("page")
            assertTrue(PageCache.write("page", "new", expected = current))
            assertFalse(PageCache.write("page", "late-old", expected = old))
            assertEquals("new", PageCache.readStale("page"))
            PageCache.clear()
            assertFalse(PageCache.write("page", "late-new", expected = current))
            assertEquals(0, PageCache.stats().entryCount)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun downloadReclamationPreservesNewerResponseAndRejectsOldInFlightWrites() {
        val directory = Files.createTempDirectory("download-cache-reference-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            PageCache.write("chapter", "old")
            val old = requireNotNull(PageCache.readEntry("chapter")).reference
            PageCache.write("chapter", "new")
            PageCache.removeReference(old)
            assertEquals("new", PageCache.readStale("chapter"))
            PageCache.writeDerived("chapter", "new", "parsed-new")
            val current = requireNotNull(PageCache.readEntry("chapter")).reference
            val pending = PageCache.beginWrite("chapter")
            PageCache.removeReference(current)
            assertNull(PageCache.readStale("chapter"))
            assertNull(PageCache.readStale(PageCache.structuredKey("chapter")))
            assertFalse(PageCache.write("chapter", "late", expected = pending))
            assertEquals(0L, PageCache.stats().sizeBytes)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun removingAndRecreatingAKeyNeverRevivesOldTokensOrReferences() {
        val directory = Files.createTempDirectory("page-key-reuse-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val old = PageCache.currentToken("page")
            assertTrue(PageCache.write("page", "old", expected = old))
            val reference = requireNotNull(PageCache.readEntry("page")).reference
            PageCache.remove("page")
            val current = PageCache.currentToken("page")
            assertNotEquals(old, current)
            assertFalse(PageCache.write("page", "late-old", expected = old))
            assertTrue(PageCache.write("page", "new", expected = current))
            PageCache.removeReference(reference)
            assertEquals("new", PageCache.readStale("page"))
        } finally { directory.deleteRecursively() }
    }

}
