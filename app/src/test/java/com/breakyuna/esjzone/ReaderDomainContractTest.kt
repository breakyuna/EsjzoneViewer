package com.breakyuna.esjzone

import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.ReaderRuby
import com.breakyuna.esjzone.domain.reader.ReaderScrollSnapshot
import com.breakyuna.esjzone.domain.reader.ReaderSessionController
import com.breakyuna.esjzone.domain.reader.ReaderWindowAnchor
import com.breakyuna.esjzone.domain.reader.shouldLoadNextChapter
import com.breakyuna.esjzone.domain.reader.shouldLoadPreviousChapter
import com.breakyuna.esjzone.domain.reader.trimReaderWindowKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDomainContractTest {

    @Test
    fun astRetainsRubyImageAndLineBreakAsToolkitIndependentBlocks() {
        val blocks = listOf(
            ReaderBlock.Text("漢", ruby = ReaderRuby(base = "漢", reading = "かん")),
            ReaderBlock.LineBreak,
            ReaderBlock.Image("https://example.test/image.jpg")
        )

        assertEquals("かん", (blocks[0] as ReaderBlock.Text).ruby?.reading)
        assertTrue(blocks[1] is ReaderBlock.LineBreak)
        assertEquals("https://example.test/image.jpg", (blocks[2] as ReaderBlock.Image).url)
    }

    @Test
    fun sessionInvalidatesGenerationAndUsesCanonicalOrderAtBoundaries() {
        val first = ReaderChapterRef("One", "/one")
        val second = ReaderChapterRef("Two", "/two")
        val controller = ReaderSessionController(listOf(first, second, second))

        val oldSession = controller.begin(first)
        val newSession = controller.begin(second)

        assertNotEquals(oldSession, newSession)
        assertEquals(second, controller.adjacent(first, 1))
        assertEquals(null, controller.adjacent(second, 1))
        assertEquals(listOf(first, second), controller.chapterOrder)
    }

    @Test
    fun windowPolicyPreservesExistingScrollAndAnchorSemantics() {
        val keys = (1..11).map { "chapter-$it" }
        assertEquals(keys, trimReaderWindowKeys(keys, true, 9, emptySet()))
        assertEquals((3..11).map { "chapter-$it" }, trimReaderWindowKeys(keys, true, 9, setOf("chapter-3")))

        val previous = snapshot(1, 0, "chapter-2", "chapter-3", keys = listOf("chapter-1", "chapter-2", "chapter-3"))
        val current = snapshot(2, 0, "chapter-2", "chapter-3", keys = previous.loadedChapterKeys)
        assertTrue(shouldLoadNextChapter(previous, current, 720))
        assertFalse(shouldLoadPreviousChapter(previous, current, 240))
        assertEquals(setOf("chapter-2", "chapter-3"), ReaderWindowAnchor(setOf("chapter-2"), "chapter-3", true).protectedChapterKeys)
    }

    private fun snapshot(
        firstIndex: Int,
        firstOffset: Int,
        firstKey: String,
        lastKey: String,
        keys: List<String>
    ) = ReaderScrollSnapshot(
        firstVisibleIndex = firstIndex,
        firstVisibleOffset = firstOffset,
        firstVisibleChapterKey = firstKey,
        lastVisibleChapterKey = lastKey,
        distanceToLoadedTail = 0,
        loadedChapterKeys = keys,
        layoutMatchesLoadedWindow = true,
        isScrollInProgress = true,
        isProgrammaticScroll = false
    )
}
