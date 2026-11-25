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
    fun measuredBookLocationReportsZeroForLockedChapterAndWaitsForAnchorOnceUnlocked() {
        val locked = ReaderChapter.passwordRequired(order[1])
        val indices = order.mapIndexed { index, chapter -> chapterIdentity(chapter) to index }.toMap()

        assertTrue(locked.document.contentFingerprint.isBlank())
        val withCatalog = requireNotNull(locked.measuredBookLocation(1f, null, order, indices))
        assertEquals(order[1], withCatalog.chapter)
        assertEquals(1, withCatalog.chapterIndex)
        assertEquals(3, withCatalog.totalChapters)
        assertEquals(0f, withCatalog.chapterProgress, 0f)
        assertEquals(1f / 3f, withCatalog.bookProgress, 0.0001f)
        assertNull(withCatalog.anchor)
        assertEquals(-1f, requireNotNull(withCatalog.contentPosition), 0f)

        val withoutCatalog = requireNotNull(locked.measuredBookLocation(null, null, emptyList(), emptyMap()))
        assertEquals(-1, withoutCatalog.chapterIndex)
        assertEquals(0f, withoutCatalog.chapterProgress, 0f)
        assertNull(withoutCatalog.anchor)

        val window = mutableListOf(locked)
        assertTrue(window.replacePasswordChapter(order[1], detail()))
        assertNull(window.single().measuredBookLocation(0.5f, null, order, indices))
    }

    @Test
    fun historyResumeWaitsForUnlockThenRestoresAnchorOrProgress() {
        val window = mutableListOf(ReaderChapter.passwordRequired(order[1]))
        val body = ChapterBody.fromBlocks(listOf(ReaderBlock.Text("Unlocked body")))
        val unlockedDetail = detail().copy(body = body)
        val anchor = ReaderAnchor(chapterIdentity(order[1]), body.fingerprint, 0, 5)

        assertNull(window.single().resumeLocation(0.75f, anchor))
        assertNull(window.single().resumeLocation(0.6f, null))

        assertTrue(window.replacePasswordChapter(order[1], unlockedDetail))
        val withAnchor = requireNotNull(window.single().resumeLocation(0.75f, anchor))
        assertEquals(order[1], withAnchor.chapter)
        assertEquals(0.75f, withAnchor.chapterProgress, 0f)
        assertSame(anchor, withAnchor.anchor)
        assertTrue(anchor.matches(chapterIdentity(order[1]), window.single().document))

        val progressOnly = requireNotNull(window.single().resumeLocation(0.6f, null))
        assertEquals(0.6f, progressOnly.chapterProgress, 0f)
        assertNull(progressOnly.anchor)
        assertEquals(progressOnly, window.single().copy(isOffline = true).resumeLocation(0.6f, null))
    }

    @Test
    fun deferredResumeCancelsOnlyWhenUserScrollsToANeighborChapter() {
        val lockedKey = chapterIdentity(order[1])
        for (neighbor in listOf(order[0], order[2])) {
            assertTrue(shouldCancelDeferredResume(lockedKey, chapterIdentity(neighbor), userScrollInProgress = true))
        }
        assertFalse(shouldCancelDeferredResume(lockedKey, lockedKey, userScrollInProgress = true))
        assertFalse(shouldCancelDeferredResume(lockedKey, null, userScrollInProgress = true))
        assertFalse(shouldCancelDeferredResume(lockedKey, chapterIdentity(order[0]), userScrollInProgress = false))
    }

    @Test
    fun finishPasswordSubmissionHandlesFailureCancellationAndTrimmedChapters() {
        val key = chapterIdentity(order[1])
        val otherKey = chapterIdentity(order[2])
        val otherState = ReaderChapterPasswordState(submitting = true)
        val states = mutableMapOf(key to ReaderChapterPasswordState(submitting = true), otherKey to otherState)
        val failure = ReaderChapterPasswordState(message = "Rejected fixture submission")

        states.finishPasswordSubmission(key, chapterStillLocked = true, failureState = failure)
        assertEquals(failure, states[key])
        assertFalse(states[key]?.submitting == true)
        assertSame(otherState, states[otherKey])

        states[key] = ReaderChapterPasswordState(submitting = true)
        states.finishPasswordSubmission(key, chapterStillLocked = true, failureState = null)
        assertEquals(ReaderChapterPasswordState(), states[key])

        states[key] = ReaderChapterPasswordState(submitting = true)
        states.finishPasswordSubmission(key, chapterStillLocked = false,
            failureState = ReaderChapterPasswordState(failure = LoadFailureKind.CLIENT))
        assertFalse(states.containsKey(key))
    }

    @Test
    fun replacePasswordChapterUpdatesOnlyMatchingLockedChapterInWindow() {
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

        val unlocked = window[1]
        assertFalse(window.replacePasswordChapter(order[1], detail("Late body")))
        assertSame(unlocked, window[1])

        val trimmedWindow = mutableListOf(ReaderChapter.passwordRequired(order[2]))
        assertFalse(trimmedWindow.replacePasswordChapter(order[1], detail()))
        assertEquals(listOf(order[2]), trimmedWindow.map { it.chapter })
    }
}
