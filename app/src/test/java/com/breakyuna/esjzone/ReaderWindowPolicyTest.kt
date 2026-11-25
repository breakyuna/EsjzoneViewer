package com.breakyuna.esjzone

import com.breakyuna.esjzone.domain.reader.readerBoundaryLoadDirection
import com.breakyuna.esjzone.domain.reader.shouldDeferReaderWindowUpdate
import com.breakyuna.esjzone.domain.reader.shouldFillReaderViewport
import com.breakyuna.esjzone.domain.reader.shouldPrepareReaderBoundary
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.page.ReaderBoundaryVerification
import com.breakyuna.esjzone.ui.page.ReaderBoundaryVerifications
import com.breakyuna.esjzone.ui.page.ReaderScrollSnapshot
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
    fun settledBoundaryPreparationRespectsEdgesAndGuards() {
        val head = snapshot(0, 0, "chapter-2", "chapter-2", 2000, listOf("chapter-2"))
            .copy(isScrollInProgress = false, hasPreviousChapter = true)
        assertTrue(shouldPrepareReaderBoundary(head, false, 240))
        assertFalse(shouldPrepareReaderBoundary(head, true, 720))

        val tail = head.copy(firstVisibleIndex = 20, firstVisibleChapterKey = null, distanceToLoadedTail = 0)
        assertTrue(shouldPrepareReaderBoundary(tail, true, 720))
        assertFalse(shouldPrepareReaderBoundary(tail, false, 240))
        assertFalse(shouldPrepareReaderBoundary(tail.copy(distanceToLoadedTail = 721), true, 720))

        val edge = head.copy(distanceToLoadedTail = 0)
        for (forward in listOf(false, true)) {
            assertFalse(shouldPrepareReaderBoundary(edge.copy(isProgrammaticScroll = true), forward, 720))
            assertFalse(shouldPrepareReaderBoundary(edge.copy(layoutMatchesLoadedWindow = false), forward, 720))
            assertFalse(shouldPrepareReaderBoundary(edge.copy(isScrollInProgress = true), forward, 720))
            assertFalse(shouldPrepareReaderBoundary(edge.copy(
                loadedChapterKeys = listOf("chapter-1", "chapter-2", "chapter-3")), forward, 720))
        }
        assertFalse(shouldPrepareReaderBoundary(edge.copy(isLoadingNext = true), true, 720))
        assertFalse(shouldPrepareReaderBoundary(edge.copy(isLoadingPrevious = true), false, 240))
        assertFalse(shouldPrepareReaderBoundary(edge.copy(hasNextChapter = false), true, 720))
        assertFalse(shouldPrepareReaderBoundary(edge.copy(hasPreviousChapter = false), false, 240))
    }

    @Test
    fun headChangesWaitForTheGestureButOrdinaryAppendRemainsVisible() {
        val window = (1..9).map { "chapter-$it" }
        val trimmed = (2..10).map { "chapter-$it" }
        assertTrue(shouldDeferReaderWindowUpdate(window, trimmed, scrolling = true))
        assertTrue(shouldDeferReaderWindowUpdate(listOf("chapter-5"), listOf("chapter-4", "chapter-5"), scrolling = true))
        assertFalse(shouldDeferReaderWindowUpdate(window, trimmed, scrolling = false))
        assertFalse(shouldDeferReaderWindowUpdate(window, window + "chapter-10", scrolling = true))
        assertFalse(shouldDeferReaderWindowUpdate(window, listOf("chapter-20"), scrolling = true))
        assertFalse(shouldDeferReaderWindowUpdate(window, emptyList(), scrolling = true))
    }

    @Test
    fun outwardEdgeDragLoadsOnlyFromSettledUnexpandedBoundary() {
        val head = snapshot(0, 0, "chapter-2", "chapter-2", 1000, listOf("chapter-2"))
            .copy(canScrollBackward = false, isScrollInProgress = false)
        val tail = head.copy(firstVisibleIndex = 30, firstVisibleChapterKey = null,
            distanceToLoadedTail = 0, canScrollBackward = true, canScrollForward = false)

        assertEquals(false, readerBoundaryLoadDirection(head, 20f))
        assertEquals(true, readerBoundaryLoadDirection(tail, -20f))
        assertNull(readerBoundaryLoadDirection(head, -20f))
        assertNull(readerBoundaryLoadDirection(tail, 20f))
        assertNull(readerBoundaryLoadDirection(head, 0f))

        assertNull(readerBoundaryLoadDirection(tail.copy(isProgrammaticScroll = true), -20f))
        assertNull(readerBoundaryLoadDirection(tail.copy(layoutMatchesLoadedWindow = false), -20f))
        assertNull(readerBoundaryLoadDirection(tail.copy(loadedChapterKeys = listOf("chapter-2", "chapter-3")), -20f))
        assertNull(readerBoundaryLoadDirection(tail.copy(canScrollForward = true), -20f))
        assertNull(readerBoundaryLoadDirection(head.copy(loadedChapterKeys = listOf("chapter-1", "chapter-2")), 20f))
        assertNull(readerBoundaryLoadDirection(head.copy(canScrollBackward = true), 20f))
    }

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
    fun shortViewportFillsOnlyWhenSettledAndCatalogReady() {
        val short = snapshot(0, 0, "chapter-1", "chapter-1", 0, listOf("chapter-1"))
            .copy(canScrollForward = false, canScrollBackward = false, isScrollInProgress = false)

        assertTrue(shouldFillReaderViewport(short))
        assertFalse(shouldFillReaderViewport(short.copy(canScrollForward = true)))
        assertFalse(shouldFillReaderViewport(short.copy(canScrollBackward = true)))
        assertFalse(shouldFillReaderViewport(short.copy(isProgrammaticScroll = true)))
        assertFalse(shouldFillReaderViewport(short.copy(layoutMatchesLoadedWindow = false)))
        assertFalse(shouldFillReaderViewport(short.copy(distanceToLoadedTail = Int.MAX_VALUE)))
        assertFalse(shouldFillReaderViewport(short.copy(lastVisibleChapterKey = null)))
        assertFalse(shouldFillReaderViewport(short.copy(loadedChapterKeys = listOf("chapter-1", "chapter-2"))))
        assertFalse(shouldFillReaderViewport(short.copy(hasNextChapter = false)))
        assertFalse(shouldFillReaderViewport(short.copy(isLoadingNext = true)))
    }

    @Test
    fun nextLoadRequiresTheActualLoadedTail() {
        val previous = snapshot(1, 0, "chapter-2", "chapter-3", 0, listOf("chapter-1", "chapter-2", "chapter-3"))
        val current = snapshot(2, 0, "chapter-2", "chapter-3", 0, previous.loadedChapterKeys)

        assertTrue(shouldLoadNextChapter(previous, current, threshold = 720))
        assertFalse(shouldLoadNextChapter(previous, current.copy(lastVisibleChapterKey = "reader-loading-next"), threshold = 720))
        assertFalse(shouldLoadNextChapter(previous, current.copy(lastVisibleChapterKey = "chapter-2"), threshold = 720))
        assertFalse(shouldLoadNextChapter(previous, current.copy(layoutMatchesLoadedWindow = false), threshold = 720))
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
    fun trimProtectsVisibleAndEdgeChaptersAndIsConservativeWithoutAnchor() {
        val keys = (1..11).map { "chapter-$it" }
        assertEquals(keys, trimReaderWindowKeys(keys, trimFromStart = true, maxSize = 9, protectedKeys = emptySet()))
        assertEquals(
            (3..11).map { "chapter-$it" },
            trimReaderWindowKeys(keys, trimFromStart = true, maxSize = 9, protectedKeys = setOf("chapter-3", "chapter-4"))
        )
        assertEquals(
            (1..9).map { "chapter-$it" },
            trimReaderWindowKeys(keys, trimFromStart = false, maxSize = 9, protectedKeys = setOf("chapter-8", "chapter-9"))
        )
        val ten = (1..10).map { "chapter-$it" }
        assertEquals(ten, trimReaderWindowKeys(ten, trimFromStart = true, maxSize = 9, protectedKeys = setOf("chapter-1")))
        assertEquals(ten, trimReaderWindowKeys(ten, trimFromStart = false, maxSize = 9, protectedKeys = setOf("chapter-10")))
    }

    @Test
    fun bothDirectionsSurviveAndRetryOnlyRemovesItsOwnGate() {
        val previous = Chapter("Previous", "/forum/1/1.html", false)
        val next = Chapter("Next", "/forum/1/3.html", false)
        val gates = ReaderBoundaryVerifications()
        gates.remember(ReaderBoundaryVerification(previous, -1))
        gates.remember(ReaderBoundaryVerification(next, 1))
        gates.markStorageUnavailable(next)
        assertEquals(previous, gates.current?.chapter)
        val retry = gates.take(next)
        assertEquals(next, retry?.chapter)
        assertTrue(retry?.storageUnavailable == true)
        assertEquals(previous, gates.current?.chapter)
        assertNull(gates.take(next))
        gates.remember(ReaderBoundaryVerification(next, 1, storageUnavailable = true))
        gates.resolve(previous.copy(url = "https://www.esjzone.one/forum/1/1.html"))
        assertEquals(next, gates.current?.chapter)
        assertTrue(gates.current?.storageUnavailable == true)
        gates.clear()
        assertNull(gates.current)
        assertFalse(gates.blocks(1))
    }

    @Test
    fun movingTheWindowDropsOnlyGatesThatAreNoLongerAdjacent() {
        val previous = Chapter("Previous", "/forum/1/1.html", false)
        val next = Chapter("Next", "/forum/1/3.html", false)
        val gates = ReaderBoundaryVerifications()
        gates.remember(ReaderBoundaryVerification(previous, -1))
        gates.remember(ReaderBoundaryVerification(next, 1))
        gates.retainTargets(previous, next)
        assertTrue(gates.blocks(-1, previous))
        assertFalse(gates.blocks(-1, next))
        gates.retainTargets(next, next)
        assertFalse(gates.blocks(-1))
        assertEquals(next, gates.current?.chapter)
        assertTrue(gates.blocks(1, next))
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
