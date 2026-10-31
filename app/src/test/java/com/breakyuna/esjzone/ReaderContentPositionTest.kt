package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.readerBodyListIndex
import com.breakyuna.esjzone.ui.reader.readerItemForContentPosition
import com.breakyuna.esjzone.ui.reader.ReaderScrollAnchor
import com.breakyuna.esjzone.ui.reader.readerRebasedScrollAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderContentPositionTest {
    @Test
    fun modeSwitchKeepsTheSourceParagraphAcrossUnequalPageLengths() {
        val pages = listOf(-1f, 0.25f, 0.8f, 2f, 5f)
        val scrollBlocks = listOf(-1f, 0f, 1f, 2f, 3f, 4f, 5f)
        assertEquals(3, readerItemForContentPosition(pages, 3.4f))
        assertEquals(4, readerItemForContentPosition(scrollBlocks, 3.4f))
    }

    @Test
    fun repaginationUsesSourcePositionRatherThanTheOldPageNumber() {
        assertEquals(3, readerItemForContentPosition(listOf(-1f, 0f, 0.5f, 1f, 2f), 1.2f))
        assertEquals(1, readerItemForContentPosition(listOf(-1f, 0f, 2f), 1.2f))
    }

    @Test
    fun headingImagesAndEmptyLayoutHaveStableTargets() {
        assertEquals(0, readerItemForContentPosition(listOf(-1f, 0f, 1f), -1f))
        assertEquals(2, readerItemForContentPosition(listOf(-1f, 0f, 1f), 1f))
        assertNull(readerItemForContentPosition(emptyList(), 0f))
    }

    @Test
    fun previousVerificationOffsetsBothTheFirstAndLastBodyItems() {
        assertEquals(0, readerBodyListIndex(0, false, false))
        assertEquals(1, readerBodyListIndex(0, true, false))
        assertEquals(31, readerBodyListIndex(30, true, false))
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
    fun trimmingLongHeadChapterKeepsTheSameParagraphAndPixelOffset() {
        val oldBody = (1..9).flatMap { chapter ->
            (0 until if (chapter == 1) 600 else 40).map { "chapter-$chapter|block:$it" }
        }
        val newBody = oldBody.drop(600) + (0 until 40).map { "chapter-10|block:$it" }
        val visible = ReaderScrollAnchor("chapter-9|block:30", oldBody.indexOf("chapter-9|block:30"), 73)
        val indices = newBody.mapIndexed { index, key -> key to index }.toMap()

        val restored = requireNotNull(readerRebasedScrollAnchor(oldBody.first(), newBody.first(),
            visible, indices, bodyStartOffset = 0))

        // Keeping the old numeric index would clamp the viewport to chapter 10's end.
        assertNotEquals(visible.key, newBody[visible.index.coerceAtMost(newBody.lastIndex)])
        assertEquals(visible.key, newBody[restored.index])
        assertEquals(visible.index - 600, restored.index)
        assertEquals(73, restored.offset)
    }

    @Test
    fun trimmingMultipleChaptersAndPrependingBothPreserveTheVisibleBody() {
        val body = (1..11).flatMap { chapter -> (0 until 160).map { "chapter-$chapter|block:$it" } }
        val visible = ReaderScrollAnchor("chapter-5|block:20", body.indexOf("chapter-5|block:20"), 42)
        val trimmed = body.drop(320)
        val trimmedIndices = trimmed.mapIndexed { index, key -> key to index }.toMap()
        val restored = requireNotNull(readerRebasedScrollAnchor(body.first(), trimmed.first(),
            visible, trimmedIndices, bodyStartOffset = 2))

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
}
