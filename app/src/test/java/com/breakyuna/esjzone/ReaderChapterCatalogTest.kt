package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.page.ReaderChapterCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderChapterCatalogTest {
    private fun chapter(id: Int) = Chapter("第${id}章", "/forum/1/$id.html", false)

    @Test
    fun neighborsUseCanonicalKeysAndKeepDistinctFragments() {
        val first = chapter(1)
        val duplicate = first.copy(url = "https://www.esjzone.one/forum/1/1.html")
        val fragment = first.copy(url = first.url + "#part2")
        val second = chapter(2)
        val catalog = ReaderChapterCatalog(listOf(first, duplicate, fragment,
            Chapter("外部", "https://example.test/chapter", false), second))
        assertEquals(listOf(first, fragment, second), catalog.chapters)
        assertEquals(fragment, catalog.adjacent(duplicate, 1, null))
        assertEquals(first, catalog.adjacent(fragment, -1, null))
        assertEquals(second, catalog.adjacent(fragment, 1, null))
    }

    @Test
    fun knownCatalogBoundariesDoNotFollowUnrelatedNavigation() {
        val first = chapter(1)
        val last = chapter(2)
        val catalog = ReaderChapterCatalog(listOf(first, last))
        assertNull(catalog.adjacent(first, -1, chapter(10)))
        assertNull(catalog.adjacent(last, 1, chapter(10)))
        assertEquals(last, catalog.adjacent(chapter(9), 1, last))
        assertNull(catalog.adjacent(chapter(9), 1, Chapter("外部", "https://example.test/chapter", false)))
    }

    @Test
    fun replacingCatalogKeepsPublishedOrderAndNeighborsConsistent() {
        val source = mutableListOf(chapter(1), chapter(2))
        val before = ReaderChapterCatalog(source)
        source.add(1, chapter(3))
        val after = ReaderChapterCatalog(source)
        assertEquals(chapter(2), before.adjacent(chapter(1), 1, null))
        assertEquals(chapter(3), after.adjacent(chapter(1), 1, null))
        assertEquals(listOf(chapter(1), chapter(2)), before.chapters)
        assertEquals(source, after.chapters)
    }
}
