package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.discovery.LibrarySource
import com.breakyuna.esjzone.ui.discovery.librarySourceOf
import com.breakyuna.esjzone.ui.discovery.matchesLibrarySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySourceTest {
    @Test
    fun mixedLocalLibraryKeepsOverlappingNumericIdsSeparate() {
        val ids = listOf("1414", "wenku8:1414", "wenku8:2552", "2552")
        assertEquals(ids, ids.filter { matchesLibrarySource(null, novelId = it) })
        assertEquals(listOf("1414", "2552"), ids.filter { matchesLibrarySource(LibrarySource.ESJZONE, novelId = it) })
        assertEquals(listOf("wenku8:1414", "wenku8:2552"), ids.filter { matchesLibrarySource(LibrarySource.WENKU8, novelId = it) })
    }

    @Test
    fun urlOnlyDownloadsAndOlderChapterBookmarksUseTheirOriginalSource() {
        val detail = "https://www.wenku8.net/book/1414.htm"
        val chapter = "https://www.wenku8.net/novel/1/1414/100.htm"
        assertEquals(LibrarySource.WENKU8, librarySourceOf(url = detail))
        assertTrue(matchesLibrarySource(LibrarySource.WENKU8, novelId = "1414", url = chapter))
        assertFalse(matchesLibrarySource(LibrarySource.ESJZONE, url = chapter))
        assertEquals(LibrarySource.ESJZONE, librarySourceOf(url = "https://www.esjzone.cc/detail/1414.html"))
    }

    @Test
    fun siteNameInAnUnrelatedUrlDoesNotBecomeWenkuContent() {
        assertFalse(matchesLibrarySource(LibrarySource.WENKU8, url = "https://www.wenku8.net.example.com/book/1414.htm"))
        assertFalse(matchesLibrarySource(LibrarySource.WENKU8, url = "https://example.com/book/1414.htm?source=wenku8"))
    }
}
