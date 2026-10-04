package com.breakyuna.esjzone

import com.breakyuna.esjzone.offline.*
import org.junit.Assert.*
import org.junit.Test

class DownloadManifestMergeTest {
    private fun record(number: Int, downloaded: Boolean = false) = DownloadedChapterRecord(number - 1, "Chapter $number",
        "/forum/9001/$number.html", "chapter-$number.json", downloaded, textLength = if (downloaded) number * 10 else null,
        bodyAvailable = downloaded)
    private fun manifest(records: List<DownloadedChapterRecord>) = DownloadedNovelManifest(name = "Novel", url = "/detail/9001.html",
        coverUrl = "", views = 0, likes = 0, words = 0, type = "", author = "", forumUrl = "", tags = emptyList(),
        isAdult = false, description = "", sourceUrl = null, updatedAt = null, chapters = records,
        downloadedAt = 1, complete = false, catalogComplete = true)

    @Test
    fun lateCompletionRebasesOnLatestCatalogAndPreservesOtherWrites() {
        val current = manifest(listOf(record(1, true), record(2), record(3, true)))
        val staleTask = manifest(listOf(record(1), record(2, true)))
        val merged = mergeDownloadManifest(current, staleTask, false)
        assertEquals(listOf(1, 2, 3).map { record(it).url }, merged.chapters.map { it.url })
        assertTrue(merged.chapters.all { it.downloaded })
        assertEquals(listOf(10, 20, 30), merged.chapters.map { it.textLength })
    }

    @Test
    fun refreshedCatalogRetainsLocalSnapshotsWithoutCountingThemInCatalog() {
        val old = manifest(listOf(record(1, true), record(2, true)))
        val incoming = manifest(listOf(record(3), record(1)))
        val merged = mergeDownloadManifest(old, incoming, true)
        assertEquals(listOf(3, 1, 2).map { record(it).url }, merged.chapters.map { it.url })
        assertTrue(merged.chapters.last().localOnly)
        assertFalse(merged.chapters.first().localOnly)
        assertEquals("chapter-1.json", merged.chapters[1].fileName)
        assertTrue(merged.chapters[1].downloaded)
    }

    @Test
    fun fullBookPercentageRequiresAllLengthsAndWeightsByTextSize() {
        val complete = manifest(listOf(record(1, true), record(2, true)))
        val urls = complete.chapters.map { it.url }
        assertEquals(2f / 3f, complete.textBookProgress(urls, NovelDownloadStore.chapterKey(urls[1]), 0.5f)!!, 0.0001f)
        assertNull(complete.copy(chapters = listOf(record(1, true), record(2))).textBookProgress(urls, NovelDownloadStore.chapterKey(urls[0]), 1f))
        assertNull(complete.textBookProgress(urls + record(3).url, NovelDownloadStore.chapterKey(urls[0]), 1f))
    }

    @Test
    fun sliderSelectsChapterUsingTheDisplayedTextWeights() {
        val book = manifest(listOf(record(1, true).copy(textLength = 10), record(2, true).copy(textLength = 90)))
        val urls = book.chapters.map { it.url }
        assertEquals(0, book.chapterIndexAtTextProgress(urls, 0f))
        assertEquals(0, book.chapterIndexAtTextProgress(urls, 0.09f))
        assertEquals(1, book.chapterIndexAtTextProgress(urls, 0.1f))
        assertEquals(1, book.chapterIndexAtTextProgress(urls, 0.5f))
        assertEquals(1, book.chapterIndexAtTextProgress(urls, 1f))
        val chapterStart = book.textBookProgress(urls, NovelDownloadStore.chapterKey(urls[1]), 0f)!!
        assertEquals(0.1f, chapterStart, 0.0001f)
        assertEquals(1, book.chapterIndexAtTextProgress(urls, chapterStart))
    }

    @Test
    fun sliderAndDisplayBothFallBackWhenAnyLengthIsUnknown() {
        val book = manifest(listOf(record(1, true), record(2)))
        val urls = book.chapters.map { it.url }
        assertNull(book.chapterIndexAtTextProgress(urls, 0.5f))
        assertNull(book.textBookProgress(urls, NovelDownloadStore.chapterKey(urls[0]), 0.5f))
        val emptyText = book.copy(chapters = book.chapters.map { it.copy(textLength = 0) })
        assertNull(emptyText.chapterIndexAtTextProgress(urls, 0.5f))
    }

    @Test
    fun preparedProgressIndexSkipsEmptyChaptersAndKeepsItsManifestSnapshot() {
        val book = manifest(listOf(0, 10, 0, 90).mapIndexed { index, length ->
            record(index + 1, true).copy(textLength = length)
        })
        val urls = book.chapters.map { it.url }
        val index = book.textProgressIndex(urls)!!
        assertEquals(1, index.chapterIndex(0f))
        assertEquals(3, index.chapterIndex(0.1f))
        assertEquals(3, index.chapterIndex(1f))
        assertEquals(0.05f, index.progress(NovelDownloadStore.chapterKey(urls[1]), 0.5f)!!, 0f)
        assertEquals(0.55f, index.progress(3, 0.5f), 0.0001f)
        val updated = book.copy(chapters = book.chapters.mapIndexed { position, chapter ->
            chapter.copy(textLength = listOf(0, 90, 0, 10)[position])
        }).textProgressIndex(urls)!!
        assertEquals(1, updated.chapterIndex(0.1f))
        assertEquals(3, index.chapterIndex(0.1f))
        assertNull(book.copy(chapters = book.chapters.map { it.copy(textLength = null) }).textProgressIndex(urls))
    }
}
