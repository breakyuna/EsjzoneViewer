package com.breakyuna.esjzone.ui.page

import androidx.compose.runtime.saveable.SaverScope
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.analyseChapterList
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadChapterSelectionTest {
    @Test
    fun mixedNestedGroupsKeepTheirChaptersInPlaceWhenExpanded() {
        val catalog = parse("""
            <a href="/forum/123/1.html">Loose start</a>
            <details><summary>Volume</summary>
                <a href="/forum/123/2.html">Chapter</a>
                <details><summary>Extras</summary><a href="/forum/123/3.html">Extra</a></details>
            </details>
            <a href="/forum/123/4.html">Loose end</a>
        """)
        val group = catalog.rows[1] as DownloadSelectionRow.Group
        val nested = group.children[1] as DownloadSelectionRow.Group
        assertEquals(3, catalog.visibleRows(emptySet()).size)
        val rows = catalog.visibleRows(setOf(group.id, nested.id))
        assertEquals(listOf("Loose start", "Volume", "Chapter", "Extras", "Extra", "Loose end"),
            rows.map { when (it) {
                is DownloadSelectionRow.Entry -> it.chapter.name
                is DownloadSelectionRow.Group -> it.title
            } })
        assertEquals(listOf(1, 2, 3, 4), rows.filterIsInstance<DownloadSelectionRow.Entry>().map { it.ordinal })
        assertEquals(setOf("/forum/123/2.html", "/forum/123/3.html"), group.urls)
        // Collapsing changes visibility, never the selected range or chapter order.
        assertEquals(group.urls, catalog.range("2", "3"))
    }

    @Test
    fun groupsInitiallyOpenOnTheSiteAreOpenInTheSelectionDialog() {
        val catalog = parse("""
            <details open><summary>Volume</summary>
                <a href="/forum/123/1.html">Chapter</a>
                <details><summary>Extras</summary><a href="/forum/123/2.html">Extra</a></details>
            </details>
        """)
        val group = catalog.rows.single() as DownloadSelectionRow.Group
        assertEquals(setOf(group.id), catalog.defaultExpandedIds)
        assertEquals(listOf("Volume", "Chapter", "Extras"),
            catalog.visibleRows(catalog.defaultExpandedIds).map { when (it) {
                is DownloadSelectionRow.Entry -> it.chapter.name
                is DownloadSelectionRow.Group -> it.title
            } })
    }

    @Test
    fun aliasesShareOneSelectableEntryWhileFragmentsRemainDistinct() {
        val catalog = parse("""
            <details><summary>Volume</summary>
                <a href="/forum/123/1.html">First</a>
                <a href="https://www.esjzone.cc/forum/123/1.html">Alias</a>
                <a href="/forum/123/1.html#extra">Extra</a>
                <a href="https://example.com/unsupported" data-title="External">External</a>
            </details>
        """)
        val group = catalog.rows.single() as DownloadSelectionRow.Group
        assertEquals(2, catalog.chapters.size)
        assertEquals(catalog.urls, group.urls)
        assertEquals(catalog.urls, catalog.range("1", "2"))
        assertEquals(2, group.children.size)
    }

    @Test
    fun largeRangesAreInclusiveAndCanBeAddedOrRemovedWithoutLosingOtherSelections() {
        val chapters = (1..10_000).map { Chapter("Extra $it", "/forum/123/$it.html", false) }
        val catalog = DownloadChapterSelection(NovelChapterList(chapters.map(::ChapterItem)))
        val first = catalog.range("101", "300")!!
        val last = catalog.range("9999", "10000")!!
        assertEquals(200, first.size)
        assertEquals(chapters[100].url, first.first())
        assertEquals(chapters[299].url, first.last())
        assertEquals(202, (first + last).size)
        assertEquals(last, (first + last) - first)
        for ((start, end) in listOf("0" to "1", "2" to "1", "1" to "10001", "" to "3", "999999999999" to "3")) {
            assertNull(catalog.range(start, end))
        }
    }

    @Test
    fun downloadedChaptersStayCheckedWhileRangeChangesOnlyPendingChapters() {
        val chapters = (1..4).map { Chapter("Chapter $it", "/forum/123/$it.html", false) }
        val catalog = DownloadChapterSelection(NovelChapterList(chapters.map(::ChapterItem)))
        val saved = setOf(chapters[1].url, chapters[3].url)
        val all = catalog.range("1", "4")!!

        val pending = catalog.pendingSelection(all, saved)
        assertEquals(setOf(chapters[0].url, chapters[2].url), pending)
        assertEquals(all, pending + saved)
        assertEquals(setOf(chapters[2].url), catalog.applyRange(pending, saved, catalog.range("1", "2")!!, false))
        assertEquals(pending, catalog.applyRange(emptySet(), saved, all, true))
    }

    @Test
    fun savedSelectionRestoresBoundedUrlSetsAndDropsOversizedState() {
        val urls = (1..200).map { "https://www.esjzone.cc/forum/123/$it.html" }.toSet()
        val scope = object : SaverScope { override fun canBeSaved(value: Any): Boolean = true }
        val encoded = with(DownloadChapterSelectionSaver) { scope.save(urls) }!!
        assertTrue(encoded.length < 32_000)
        assertEquals(urls, DownloadChapterSelectionSaver.restore(encoded))
        val oversized = (1..10_000).map { "https://www.esjzone.cc/forum/123/$it.html" }.toSet()
        val overflow = with(DownloadChapterSelectionSaver) { scope.save(oversized) }
        assertNull(overflow)
        val empty = with(DownloadChapterSelectionSaver) { scope.save(emptySet()) }!!
        assertEquals(emptySet<String>(), DownloadChapterSelectionSaver.restore(empty))
    }

    private fun parse(html: String): DownloadChapterSelection = DownloadChapterSelection(
        analyseChapterList(Jsoup.parse("<div id='integration'>$html</div>").selectFirst("#integration")!!)
    )
}
