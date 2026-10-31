package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.ReaderScrollSnapshot
import com.breakyuna.esjzone.ui.page.ReaderWindowAnchor
import com.breakyuna.esjzone.ui.page.chapterProgressFor
import com.breakyuna.esjzone.ui.page.shouldLoadNextChapter
import com.breakyuna.esjzone.ui.page.shouldLoadPreviousChapter
import com.breakyuna.esjzone.ui.page.trimReaderWindowKeys
import com.breakyuna.esjzone.domain.reader.shouldFillReaderViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWindowPolicyTest {

    @Test
    fun restoredScrollableTailCannotAutomaticallyChainChapterLoads() {
        val tail = snapshot(300, 0, "chapter-9", "chapter-10", 0,
            (2..10).map { "chapter-$it" }).copy(
            canScrollForward = false, canScrollBackward = true,
            isScrollInProgress = false
        )

        assertFalse(shouldFillReaderViewport(tail))
        assertFalse(shouldFillReaderViewport(tail.copy(isScrollInProgress = true)))
        assertFalse(shouldLoadNextChapter(tail, tail, threshold = 720))
        // A subsequent deliberate scroll near the tail can still load the next chapter.
        assertTrue(shouldLoadNextChapter(tail.copy(firstVisibleOffset = 0),
            tail.copy(firstVisibleOffset = 20, isScrollInProgress = true), threshold = 720))
    }

    @Test
    fun shortViewportFillsOnlyWithACompleteSettledBodyLayout() {
        val short = snapshot(0, 0, "chapter-1", "chapter-1", 0, listOf("chapter-1"))
            .copy(canScrollForward = false, canScrollBackward = false, isScrollInProgress = false)

        assertTrue(shouldFillReaderViewport(short))
        assertFalse(shouldFillReaderViewport(short.copy(canScrollForward = true)))
        assertFalse(shouldFillReaderViewport(short.copy(isProgrammaticScroll = true)))
        assertFalse(shouldFillReaderViewport(short.copy(layoutMatchesLoadedWindow = false)))
        assertFalse(shouldFillReaderViewport(short.copy(distanceToLoadedTail = Int.MAX_VALUE)))
        assertFalse(shouldFillReaderViewport(short.copy(lastVisibleChapterKey = null)))
        assertFalse(shouldFillReaderViewport(short.copy(loadedChapterKeys = listOf("chapter-1", "chapter-2"))))
    }

    @Test
    fun catalogReadinessReevaluatesShortViewportWithoutScrollOrLayoutChanges() {
        val waiting = snapshot(0, 0, "chapter-1", "chapter-1", 0, listOf("chapter-1"))
            .copy(canScrollForward = false, canScrollBackward = false,
                isScrollInProgress = false, hasNextChapter = false, isLoadingNext = true)
        assertFalse(shouldFillReaderViewport(waiting))
        val catalogPublished = waiting.copy(hasNextChapter = true)
        assertTrue(waiting != catalogPublished)
        assertFalse(shouldFillReaderViewport(catalogPublished))
        val ready = catalogPublished.copy(isLoadingNext = false)
        assertTrue(catalogPublished != ready)
        assertTrue(shouldFillReaderViewport(ready))
        assertFalse(shouldFillReaderViewport(ready.copy(hasNextChapter = false)))
        assertFalse(shouldFillReaderViewport(ready.copy(canScrollBackward = true)))
    }

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
    fun finishingWindowReanchorDoesNotCountAsNewUserMovement() {
        val restoring = snapshot(1, 0, "chapter-1", "chapter-3", 0,
            listOf("chapter-1", "chapter-2", "chapter-3"), programmatic = true)
        val forward = restoring.copy(firstVisibleIndex = 2, isProgrammaticScroll = false)
        val backward = restoring.copy(firstVisibleIndex = 0, isProgrammaticScroll = false)

        assertFalse(shouldLoadNextChapter(restoring, forward, threshold = 720))
        assertFalse(shouldLoadPreviousChapter(restoring, backward, threshold = 240))
        assertFalse(shouldLoadNextChapter(restoring.copy(isProgrammaticScroll = false,
            layoutMatchesLoadedWindow = false), forward, threshold = 720))
        assertFalse(shouldLoadPreviousChapter(restoring.copy(isProgrammaticScroll = false,
            layoutMatchesLoadedWindow = false), backward, threshold = 240))

        assertTrue(shouldLoadNextChapter(forward, forward.copy(firstVisibleOffset = 20), threshold = 720))
        assertTrue(shouldLoadPreviousChapter(backward.copy(firstVisibleOffset = 20), backward, threshold = 240))
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
        isProgrammaticScroll = programmatic,
        hasNextChapter = true
    )
}
