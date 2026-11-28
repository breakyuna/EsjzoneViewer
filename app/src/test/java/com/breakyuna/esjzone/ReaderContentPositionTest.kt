package com.breakyuna.esjzone

import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.page.ReaderChapter
import com.breakyuna.esjzone.ui.page.ReaderDisplayItem
import com.breakyuna.esjzone.ui.page.ReaderPaginationSnapshot
import com.breakyuna.esjzone.ui.page.chapterIdentity
import com.breakyuna.esjzone.ui.reader.ReaderPage
import com.breakyuna.esjzone.ui.reader.ReaderScrollAnchor
import com.breakyuna.esjzone.ui.reader.readerBodyListIndex
import com.breakyuna.esjzone.ui.reader.readerInitialBodyIndex
import com.breakyuna.esjzone.ui.reader.readerItemForContentPosition
import com.breakyuna.esjzone.ui.reader.readerRebasedScrollAnchor
import com.breakyuna.esjzone.ui.reader.restoreReaderPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContentPositionTest {
    private val chapterKey = "/forum/1/2.html"
    private val document = ReaderChapterDocument(ReaderChapterRef("Chapter", chapterKey),
        (0 until 100).map { ReaderBlock.Text("Saved paragraph $it") }, contentFingerprint = "a".repeat(64))

    private fun readerChapter(number: Int, text: String = "Body $number"): ReaderChapter {
        val chapter = Chapter("Chapter $number", "/forum/9001/$number.html", false)
        return ReaderChapter(chapter, ReaderChapterDocument(
            ReaderChapterRef(chapter.name, chapter.url), listOf(ReaderBlock.Text(text))))
    }

    private fun paginationSnapshot(layout: List<Any?>, entry: ReaderChapter): ReaderPaginationSnapshot {
        val page = ReaderPage(emptyList(), -1f)
        val key = chapterIdentity(entry.chapter)
        return ReaderPaginationSnapshot(layout + listOf(listOf(entry)), layout,
            mapOf(entry to listOf(page)),
            listOf(ReaderDisplayItem("$key|page:0", key, entry, 0, 1, emptyList(), page)))
    }

    @Test
    fun firstBodyMeasureUsesExactParagraphEvenWhenLegacyProgressDisagrees() {
        val anchor = ReaderAnchor(chapterKey, document.contentFingerprint, 72, 4)
        assertEquals(73, readerInitialBodyIndex(document, chapterKey, 0.1f, anchor))
        assertEquals(75, readerBodyListIndex(readerInitialBodyIndex(document, chapterKey, 0.1f, anchor), true, true))
    }

    @Test
    fun changedContentOrWrongChapterFallsBackToProgressBeforeFirstMeasure() {
        val anchor = ReaderAnchor(chapterKey, document.contentFingerprint, 72, 4)
        assertEquals(50, readerInitialBodyIndex(document, chapterKey, 0.5f, anchor.copy(fingerprint = "b".repeat(64))))
        assertEquals(50, readerInitialBodyIndex(document, chapterKey, 0.5f, anchor.copy(chapterKey = "other")))
        assertEquals(50, readerInitialBodyIndex(document, chapterKey, 0.5f, anchor.copy(blockIndex = 1000)))
    }

    @Test
    fun firstMeasureHandlesHeadingEndImagesEmptyBodyAndInvalidLegacyProgress() {
        val anchor = ReaderAnchor(chapterKey, document.contentFingerprint, -1, kind = "heading")
        assertEquals(0, readerInitialBodyIndex(document, chapterKey, 0.9f, anchor))
        assertEquals(100, readerInitialBodyIndex(document, chapterKey, 0f, anchor.copy(kind = "end")))
        val imageDocument = document.copy(blocks = listOf(ReaderBlock.Text("Text"), ReaderBlock.Image("image")))
        assertEquals(2, readerInitialBodyIndex(imageDocument, chapterKey, 0f,
            anchor.copy(blockIndex = 1, kind = "image", fraction = 0.4f)))
        assertEquals(100, readerInitialBodyIndex(document, chapterKey, 1f, null))
        assertEquals(0, readerInitialBodyIndex(document, chapterKey, Float.NaN, null))
        assertEquals(0, readerInitialBodyIndex(document.copy(blocks = emptyList()), chapterKey, 1f, null))
    }

    @Test
    fun readerItemForContentPositionTracksSourceAcrossModeSwitchRepaginationAndBoundaries() {
        val pages = listOf(-1f, 0.25f, 0.8f, 2f, 5f)
        val scrollBlocks = listOf(-1f, 0f, 1f, 2f, 3f, 4f, 5f)
        assertEquals(3, readerItemForContentPosition(pages, 3.4f))
        assertEquals(4, readerItemForContentPosition(scrollBlocks, 3.4f))
        assertEquals(3, readerItemForContentPosition(listOf(-1f, 0f, 0.5f, 1f, 2f), 1.2f))
        assertEquals(1, readerItemForContentPosition(listOf(-1f, 0f, 2f), 1.2f))
        assertEquals(0, readerItemForContentPosition(listOf(-1f, 0f, 1f), -1f))
        assertEquals(2, readerItemForContentPosition(listOf(-1f, 0f, 1f), 1f))
        assertNull(readerItemForContentPosition(emptyList(), 0f))
    }

    @Test
    fun retryInsertionAndRemovalKeepSeekTargetsAndVisibleBodyMappingAligned() {
        val body = listOf("heading", "paragraph", "chapter-end")
        for (verification in listOf(false, true)) {
            for (retry in listOf(false, true, false)) {
                val rows = buildList {
                    if (verification) add("previous-verification")
                    if (retry) add("previous-retry")
                    addAll(body)
                    add("next-retry")
                }
                val bodyStart = readerBodyListIndex(0, verification, retry)
                body.forEachIndexed { index, key ->
                    // Progress/comment restoration must land on the requested body key.
                    assertEquals(key, rows[readerBodyListIndex(index, verification, retry)])
                    // Continuous loading must recognize the visible items as the loaded window.
                    assertEquals(key, body[rows.indexOf(key) - bodyStart])
                }
            }
        }
    }

    @Test
    fun headTrimAndPrependRebaseVisibleParagraphAndOffset() {
        val longHead = (1..9).flatMap { chapter ->
            (0 until if (chapter == 1) 600 else 40).map { "chapter-$chapter|block:$it" }
        }
        val trimmedLong = longHead.drop(600) + (0 until 40).map { "chapter-10|block:$it" }
        val tailVisible = ReaderScrollAnchor("chapter-9|block:30", longHead.indexOf("chapter-9|block:30"), 73)
        val restoredTail = requireNotNull(readerRebasedScrollAnchor(longHead.first(), trimmedLong.first(),
            tailVisible, trimmedLong.mapIndexed { index, key -> key to index }.toMap(), bodyStartOffset = 0))
        assertNotEquals(tailVisible.key, trimmedLong[tailVisible.index.coerceAtMost(trimmedLong.lastIndex)])
        assertEquals(tailVisible.key, trimmedLong[restoredTail.index])
        assertEquals(tailVisible.index - 600, restoredTail.index)
        assertEquals(73, restoredTail.offset)

        val body = (1..11).flatMap { chapter -> (0 until 160).map { "chapter-$chapter|block:$it" } }
        val visible = ReaderScrollAnchor("chapter-5|block:20", body.indexOf("chapter-5|block:20"), 42)
        val trimmed = body.drop(320)
        val restored = requireNotNull(readerRebasedScrollAnchor(body.first(), trimmed.first(),
            visible, trimmed.mapIndexed { index, key -> key to index }.toMap(), bodyStartOffset = 2))
        assertEquals(visible.key, trimmed[restored.index - 2])
        assertEquals(visible.index - 320 + 2, restored.index)
        assertEquals(visible.offset, restored.offset)

        val prepended = requireNotNull(readerRebasedScrollAnchor(trimmed.first(), body.first(),
            restored, body.mapIndexed { index, key -> key to index }.toMap(), bodyStartOffset = 0))
        assertEquals(visible, prepended)
    }

    @Test
    fun appendAndExplicitChapterReplacementDoNotRestoreAnOldViewport() {
        val visible = ReaderScrollAnchor("current-paragraph", 1, 24)
        val indices = mapOf("heading" to 0, visible.key to 1, "next-heading" to 2)
        assertNull(readerRebasedScrollAnchor("heading", "heading", visible, indices, 0))
        assertNull(readerRebasedScrollAnchor(null, "heading", visible, indices, 0))
        assertNull(readerRebasedScrollAnchor("heading", "replacement", visible,
            mapOf("replacement" to 0), 0))
        assertNull(readerRebasedScrollAnchor("heading", null, visible, emptyMap(), 0))
    }

    @Test
    fun interruptedScrollMutationCanStillRestoreTheParagraph() = runBlocking {
        assertTrue(restoreReaderPosition(
            exact = { throw CancellationException("Scroll mutation interrupted") },
            fallback = { true }
        ))
    }

    @Test
    fun cancellingTheSeekDoesNotMoveTheViewportAgain() = runBlocking {
        var fallbackCalled = false
        val seek = launch {
            val seekContext = currentCoroutineContext()
            restoreReaderPosition(
                exact = {
                    seekContext.cancel()
                    true
                },
                fallback = {
                    fallbackCalled = true
                    true
                }
            )
        }
        seek.join()
        assertTrue(seek.isCancelled)
        assertFalse(fallbackCalled)
    }

    @Test
    fun exactRestoreTimeoutFallsBackToParagraphRestore() = runBlocking {
        var fallbackCalled = false
        val restored = restoreReaderPosition(
            exact = {
                delay(2_000L)
                true
            },
            fallback = {
                fallbackCalled = true
                true
            }
        )
        assertTrue(restored)
        assertTrue(fallbackCalled)
    }

    @Test
    fun restoreCompletesWithinBoundedTimeWhenBothExactAndFallbackTimeOut() = runBlocking {
        var fallbackCalled = false
        val startedMs = System.nanoTime() / 1_000_000
        val restored = restoreReaderPosition(
            exact = {
                delay(2_000L)
                true
            },
            fallback = {
                fallbackCalled = true
                delay(2_000L)
                true
            }
        )
        val elapsedMs = System.nanoTime() / 1_000_000 - startedMs
        assertFalse(restored)
        assertTrue(fallbackCalled)
        assertTrue("Expected bounded timeout < 1500 ms, was $elapsedMs ms", elapsedMs < 1_500L)
    }

    @Test
    fun paginationSnapshotReusesPreparedChapterOnlyUntilLayoutWindowBodyOrUnlockChanges() {
        val layout = listOf<Any?>(390, 844, "original")
        val current = readerChapter(2)
        val prepared = paginationSnapshot(layout, current)
        assertTrue(prepared.hasReadablePage(layout, current, listOf(current, readerChapter(3))))
        assertTrue(prepared.hasReadablePage(layout, current, listOf(readerChapter(1), current)))
        assertFalse(prepared.hasReadablePage(layout, readerChapter(3), listOf(current, readerChapter(3))))
        assertFalse(prepared.hasReadablePage(listOf(844, 390, "original"), current, listOf(current)))
        assertFalse(prepared.hasReadablePage(layout, current, listOf(readerChapter(3))))

        val replacement = readerChapter(2, "Updated body")
        assertFalse(prepared.hasReadablePage(layout, current, listOf(replacement)))
        assertFalse(prepared.hasReadablePage(layout, replacement, listOf(replacement)))

        val locked = ReaderChapter.passwordRequired(current.chapter)
        val lockedSnapshot = paginationSnapshot(layout, locked)
        assertTrue(lockedSnapshot.hasReadablePage(layout, locked, listOf(locked)))
        assertFalse(lockedSnapshot.hasReadablePage(layout, locked, listOf(current)))
        assertFalse(lockedSnapshot.hasReadablePage(layout, current, listOf(current)))
    }
}
