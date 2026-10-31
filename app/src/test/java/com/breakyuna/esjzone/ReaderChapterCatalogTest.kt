package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.page.ReaderChapterCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(fragment, catalog.adjacent(duplicate, 1))
        assertEquals(first, catalog.adjacent(fragment, -1))
        assertEquals(second, catalog.adjacent(fragment, 1))
    }

    @Test
    fun knownCatalogBoundariesDoNotFollowUnrelatedNavigation() {
        val first = chapter(1)
        val last = chapter(2)
        val catalog = ReaderChapterCatalog(listOf(first, last))
        assertNull(catalog.adjacent(first, -1))
        assertNull(catalog.adjacent(last, 1))
        assertNull(catalog.adjacent(chapter(9), 1))
        assertNull(catalog.adjacent(chapter(9), -1))
    }

    @Test
    fun partialInventoryCannotSkipMissingChaptersInEitherDirection() {
        val saved = listOf(chapter(10), chapter(15), chapter(18))
        val partial = ReaderChapterCatalog(saved, complete = false)

        assertEquals(saved, partial.chapters)
        assertFalse(partial.isComplete)
        assertNull(partial.adjacent(chapter(10), 1))
        assertNull(partial.adjacent(chapter(18), -1))

        val resolved = ReaderChapterCatalog((10..18).map(::chapter))
        assertTrue(resolved.isComplete)
        assertEquals(chapter(11), resolved.adjacent(chapter(10), 1))
        assertEquals(chapter(17), resolved.adjacent(chapter(18), -1))
    }

    @Test
    fun emptyOrUnsupportedCatalogCannotResolveChapterOrder() {
        val empty = ReaderChapterCatalog(emptyList())
        val unsupported = ReaderChapterCatalog(listOf(
            Chapter("外部", "https://example.test/chapter", false)
        ))
        assertFalse(empty.isComplete)
        assertFalse(unsupported.isComplete)
        assertNull(empty.adjacent(chapter(1), 1))
        assertNull(unsupported.adjacent(chapter(1), 1))
    }

    @Test
    fun replacingCatalogKeepsPublishedOrderAndNeighborsConsistent() {
        val source = mutableListOf(chapter(1), chapter(2))
        val before = ReaderChapterCatalog(source)
        source.add(1, chapter(3))
        val after = ReaderChapterCatalog(source)
        assertEquals(chapter(2), before.adjacent(chapter(1), 1))
        assertEquals(chapter(3), after.adjacent(chapter(1), 1))
        assertEquals(listOf(chapter(1), chapter(2)), before.chapters)
        assertEquals(source, after.chapters)
    }
}
