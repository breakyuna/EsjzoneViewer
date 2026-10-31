package com.breakyuna.esjzone

import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.ui.page.ReaderChapter
import com.breakyuna.esjzone.ui.page.ReaderChapterCatalog
import com.breakyuna.esjzone.ui.page.ReaderChapterPasswordState
import com.breakyuna.esjzone.ui.page.chapterIdentity
import com.breakyuna.esjzone.ui.page.finishPasswordSubmission
import com.breakyuna.esjzone.ui.page.measuredBookLocation
import com.breakyuna.esjzone.ui.page.replacePasswordChapter
import com.breakyuna.esjzone.ui.page.resumeLocation
import com.breakyuna.esjzone.ui.page.shouldCancelDeferredResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPasswordChapterTest {
    private val order = (1..3).map { Chapter("Chapter $it", "/forum/9001/$it.html", false) }
    private fun detail(text: String = "Unlocked body") = DetailedChapter(
        order[1].name, listOf(TextComponent(text)), order[0], order[2]
    )

    @Test
    fun lockedChapterRetainsItsIdentityAndBothCatalogNeighborsWithoutInventingBodyText() {
        val locked = ReaderChapter.passwordRequired(order[1])
        val catalog = ReaderChapterCatalog(order)

        assertTrue(locked.requiresPassword)
        assertTrue(locked.document.blocks.isEmpty())
        assertEquals(order[1].url, locked.document.chapter.url)
        assertEquals(order[0], catalog.adjacent(locked.chapter, -1))
        assertEquals(order[2], catalog.adjacent(locked.chapter, 1))
    }

    @Test
    fun lockedChapterRecordsItsOwnZeroProgressWithoutABodyAnchor() {
        val locked = ReaderChapter.passwordRequired(order[1])
        val indices = order.mapIndexed { index, chapter -> chapterIdentity(chapter) to index }.toMap()

        assertTrue(locked.document.contentFingerprint.isBlank())
        val location = requireNotNull(locked.measuredBookLocation(1f, null, order, indices))

        assertEquals(order[1], location.chapter)
        assertEquals(1, location.chapterIndex)
        assertEquals(3, location.totalChapters)
        assertEquals(0f, location.chapterProgress, 0f)
        assertEquals(1f / 3f, location.bookProgress, 0.0001f)
        assertNull(location.anchor)
        assertEquals(-1f, requireNotNull(location.contentPosition), 0f)
    }

    @Test
    fun lockedChapterStillHasALocationWhileCatalogIsUnavailable() {
        val locked = ReaderChapter.passwordRequired(order[1])
        val location = requireNotNull(locked.measuredBookLocation(null, null, emptyList(), emptyMap()))

        assertEquals(order[1], location.chapter)
        assertEquals(-1, location.chapterIndex)
        assertEquals(0f, location.chapterProgress, 0f)
        assertNull(location.anchor)
    }

    @Test
    fun ordinaryChapterStillWaitsForAMeasuredAnchor() {
        val window = mutableListOf(ReaderChapter.passwordRequired(order[1]))
        assertTrue(window.replacePasswordChapter(order[1], detail()))
        val indices = order.mapIndexed { index, chapter -> chapterIdentity(chapter) to index }.toMap()

        assertNull(window.single().measuredBookLocation(0.5f, null, order, indices))
    }

    @Test
    fun historyResumeWaitsForUnlockThenRestoresSavedProgressAndExactAnchor() {
        val window = mutableListOf(ReaderChapter.passwordRequired(order[1]))
        val body = ChapterBody.fromBlocks(listOf(ReaderBlock.Text("Unlocked body")))
        val unlockedDetail = detail().copy(body = body)
        val anchor = ReaderAnchor(chapterIdentity(order[1]), body.fingerprint, 0, 5)

        assertNull(window.single().resumeLocation(0.75f, anchor))

        assertTrue(window.replacePasswordChapter(order[1], unlockedDetail))
        val restored = requireNotNull(window.single().resumeLocation(0.75f, anchor))
        assertEquals(order[1], restored.chapter)
        assertEquals(0.75f, restored.chapterProgress, 0f)
        assertSame(anchor, restored.anchor)
        assertTrue(anchor.matches(chapterIdentity(order[1]), window.single().document))
    }

    @Test
    fun historyWithoutAnAnchorResumesByProgressOnlyAfterUnlock() {
        val window = mutableListOf(ReaderChapter.passwordRequired(order[1]))
        assertNull(window.single().resumeLocation(0.6f, null))

        assertTrue(window.replacePasswordChapter(order[1], detail()))
        val restored = requireNotNull(window.single().resumeLocation(0.6f, null))
        assertEquals(0.6f, restored.chapterProgress, 0f)
        assertNull(restored.anchor)

        val offline = window.single().copy(isOffline = true)
        assertEquals(restored, offline.resumeLocation(0.6f, null))
    }

    @Test
    fun leavingPasswordChapterInEitherDirectionCancelsDeferredHistoryResume() {
        val lockedKey = chapterIdentity(order[1])

        for (neighbor in listOf(order[0], order[2])) {
            assertTrue(shouldCancelDeferredResume(lockedKey, chapterIdentity(neighbor), userScrollInProgress = true))
        }
    }

    @Test
    fun scrollingWithinPasswordChapterKeepsHistoryForUnlock() {
        val locked = ReaderChapter.passwordRequired(order[1])
        val lockedKey = chapterIdentity(locked.chapter)

        assertFalse(shouldCancelDeferredResume(lockedKey, lockedKey, userScrollInProgress = true))
        assertNull(locked.resumeLocation(0.75f, null))
        val window = mutableListOf(locked)
        assertTrue(window.replacePasswordChapter(order[1], detail()))
        assertEquals(0.75f, requireNotNull(window.single().resumeLocation(0.75f, null)).chapterProgress, 0f)
    }

    @Test
    fun missingLayoutAndChangesWithoutUserScrollingKeepDeferredHistoryResume() {
        val lockedKey = chapterIdentity(order[1])

        assertFalse(shouldCancelDeferredResume(lockedKey, null, userScrollInProgress = true))
        assertFalse(shouldCancelDeferredResume(lockedKey, chapterIdentity(order[0]), userScrollInProgress = false))
    }

    @Test
    fun lateFailureClearsSubmittingStateForATrimmedChapterSoItCanBeRetried() {
        val key = chapterIdentity(order[1])
        val states = mutableMapOf(key to ReaderChapterPasswordState(submitting = true))
        val window = mutableListOf(ReaderChapter.passwordRequired(order[2]))

        states.finishPasswordSubmission(key,
            window.any { chapterIdentity(it.chapter) == key && it.requiresPassword },
            ReaderChapterPasswordState(failure = LoadFailureKind.CLIENT))

        assertFalse(states.containsKey(key))
        window.add(0, ReaderChapter.passwordRequired(order[1]))
        assertTrue(window.first().requiresPassword)
        assertFalse(states[key]?.submitting == true)
    }

    @Test
    fun completedFailureKeepsItsMessageAndOtherChapterStatusWhileAllowingRetry() {
        val key = chapterIdentity(order[1])
        val otherKey = chapterIdentity(order[2])
        val otherState = ReaderChapterPasswordState(submitting = true)
        val states = mutableMapOf(key to ReaderChapterPasswordState(submitting = true), otherKey to otherState)
        val failure = ReaderChapterPasswordState(message = "Rejected fixture submission")

        states.finishPasswordSubmission(key, chapterStillLocked = true, failureState = failure)

        assertEquals(failure, states[key])
        assertFalse(states[key]?.submitting == true)
        assertSame(otherState, states[otherKey])
    }

    @Test
    fun cancelledSubmissionDoesNotKeepALockedChapterDisabled() {
        val key = chapterIdentity(order[1])
        val states = mutableMapOf(key to ReaderChapterPasswordState(submitting = true))

        states.finishPasswordSubmission(key, chapterStillLocked = true, failureState = null)

        assertEquals(ReaderChapterPasswordState(), states[key])
    }

    @Test
    fun unlockReplacesOnlyItsOwnChapterAndKeepsTheRestOfTheWindow() {
        val previous = ReaderChapter.passwordRequired(order[0])
        val next = ReaderChapter.passwordRequired(order[2])
        val window = mutableListOf(previous, ReaderChapter.passwordRequired(order[1]), next)
        val mirrorTarget = order[1].copy(url = "https://www.esjzone.one/forum/9001/2.html")

        assertTrue(window.replacePasswordChapter(mirrorTarget, detail()))
        assertEquals(order, window.map { it.chapter })
        assertSame(previous, window[0])
        assertSame(next, window[2])
        assertFalse(window[1].requiresPassword)
        assertEquals(listOf(ReaderBlock.Paragraph(listOf(ReaderBlock.Text("Unlocked body")))), window[1].document.blocks)
        assertEquals(order[0], window[1].fallbackPrevious)
        assertEquals(order[2], window[1].fallbackNext)

        // A late completion must not overwrite the body already accepted.
        val unlocked = window[1]
        assertFalse(window.replacePasswordChapter(order[1], detail("Late body")))
        assertSame(unlocked, window[1])
    }

    @Test
    fun lateUnlockDoesNotReinsertAChapterTrimmedFromTheReadingWindow() {
        val window = mutableListOf(ReaderChapter.passwordRequired(order[2]))
        val snapshot = window.toList()
        assertFalse(window.replacePasswordChapter(order[1], detail()))
        assertEquals(snapshot, window)
    }
}
