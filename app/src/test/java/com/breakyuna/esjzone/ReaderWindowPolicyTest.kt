package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.ReaderScrollSnapshot
import com.breakyuna.esjzone.ui.page.ReaderWindowAnchor
import com.breakyuna.esjzone.ui.page.chapterProgressFor
import com.breakyuna.esjzone.ui.page.shouldLoadNextChapter
import com.breakyuna.esjzone.ui.page.shouldLoadPreviousChapter
import com.breakyuna.esjzone.ui.page.trimReaderWindowKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWindowPolicyTest {

    @Test
    fun nextLoadRequiresTheActualLoadedTail() {
        val previous = snapshot(1, 0, "chapter-2", "chapter-3", 0, listOf("chapter-1", "chapter-2", "chapter-3"))
        val current = snapshot(2, 0, "chapter-2", "chapter-3", 0, previous.loadedChapterKeys)

        assertTrue(shouldLoadNextChapter(previous, current, threshold = 720))
        assertFalse(
            shouldLoadNextChapter(
                previous,
                current.copy(lastVisibleChapterKey = "reader-loading-next"),
                threshold = 720
            )
        )
        assertFalse(
            shouldLoadNextChapter(
                previous,
                current.copy(lastVisibleChapterKey = "chapter-2"),
                threshold = 720
            )
        )
        assertFalse(
            shouldLoadNextChapter(
                previous,
                current.copy(layoutMatchesLoadedWindow = false),
                threshold = 720
            )
        )
    }

    @Test
    fun dataWindowChangeDoesNotLookLikeAUserGesture() {
        val previous = snapshot(1, 0, "chapter-2", "chapter-3", 0, listOf("chapter-1", "chapter-2", "chapter-3"))
        val appended = snapshot(2, 0, "chapter-2", "chapter-4", 0, listOf("chapter-1", "chapter-2", "chapter-3", "chapter-4"))
        val prepended = snapshot(0, 0, "chapter-0", "chapter-2", 0, listOf("chapter-0", "chapter-1", "chapter-2", "chapter-3"))

        assertFalse(shouldLoadNextChapter(previous, appended, threshold = 720))
        assertFalse(shouldLoadPreviousChapter(previous, prepended, threshold = 240))
    }

    @Test
    fun programmaticSeekCannotTriggerEitherDirection() {
        val previous = snapshot(1, 0, "chapter-2", "chapter-3", 0, listOf("chapter-1", "chapter-2", "chapter-3"))
        val seek = snapshot(0, 0, "chapter-1", "chapter-2", 0, previous.loadedChapterKeys, programmatic = true)

        assertFalse(shouldLoadNextChapter(previous, seek, threshold = 720))
        assertFalse(shouldLoadPreviousChapter(previous, seek, threshold = 240))
    }

    @Test
    fun trimProtectsVisibleNineChapterWindowAndIsConservativeWithoutAnchor() {
        val keys = (1..11).map { "chapter-$it" }
        assertEquals(
            keys,
            trimReaderWindowKeys(keys, trimFromStart = true, maxSize = 9, protectedKeys = emptySet())
        )
        assertEquals(
            (3..11).map { "chapter-$it" },
            trimReaderWindowKeys(
                keys,
                trimFromStart = true,
                maxSize = 9,
                protectedKeys = setOf("chapter-3", "chapter-4")
            )
        )
        assertEquals(
            (1..9).map { "chapter-$it" },
            trimReaderWindowKeys(
                keys,
                trimFromStart = false,
                maxSize = 9,
                protectedKeys = setOf("chapter-8", "chapter-9")
            )
        )
    }

    @Test
    fun protectedEdgePreventsDeletingTheCurrentChapter() {
        val keys = (1..10).map { "chapter-$it" }
        assertEquals(
            keys,
            trimReaderWindowKeys(
                keys,
                trimFromStart = true,
                maxSize = 9,
                protectedKeys = setOf("chapter-1")
            )
        )
        assertEquals(
            keys,
            trimReaderWindowKeys(
                keys,
                trimFromStart = false,
                maxSize = 9,
                protectedKeys = setOf("chapter-10")
            )
        )
    }

    @Test
    fun layoutAnchorOnlyProtectsKeysAfterACompletedLayout() {
        assertEquals(
            emptySet<String>(),
            ReaderWindowAnchor(setOf("chapter-2"), "chapter-2", layoutReady = false)
                .protectedChapterKeys
        )
        assertEquals(
            setOf("chapter-2", "chapter-3"),
            ReaderWindowAnchor(setOf("chapter-2"), "chapter-3", layoutReady = true)
                .protectedChapterKeys
        )
    }

    @Test
    fun missingLayoutGeometryDoesNotResetProgressToZero() {
        assertNull(chapterProgressFor(itemOffset = null, itemSize = null))
        assertNull(chapterProgressFor(itemOffset = 0, itemSize = 0))
        assertEquals(0.5f, chapterProgressFor(itemOffset = -50, itemSize = 100) ?: -1f, 0.0001f)
    }

    @Test
    fun latestAsyncAnchorWinsOverTheAnchorAtRequestStart() {
        val keys = (1..10).map { "chapter-$it" }
        val atRequestStart = setOf("chapter-1")
        val whenRequestCompletes = setOf("chapter-4", "chapter-5")

        val retained = trimReaderWindowKeys(
            keys,
            trimFromStart = true,
            maxSize = 9,
            protectedKeys = whenRequestCompletes
        )

        assertEquals(keys, trimReaderWindowKeys(keys, true, 9, atRequestStart))
        assertEquals((2..10).map { "chapter-$it" }, retained)
        assertTrue("chapter-4" in retained)
        assertTrue("chapter-5" in retained)
    }

    private fun snapshot(
        firstIndex: Int,
        firstOffset: Int,
        firstKey: String,
        lastKey: String,
        tailDistance: Int,
        keys: List<String>,
        programmatic: Boolean = false
    ) = ReaderScrollSnapshot(
        firstVisibleIndex = firstIndex,
        firstVisibleOffset = firstOffset,
        firstVisibleChapterKey = firstKey,
        lastVisibleChapterKey = lastKey,
        distanceToLoadedTail = tailDistance,
        loadedChapterKeys = keys,
        layoutMatchesLoadedWindow = true,
        isScrollInProgress = true,
        isProgrammaticScroll = programmatic
    )
}
