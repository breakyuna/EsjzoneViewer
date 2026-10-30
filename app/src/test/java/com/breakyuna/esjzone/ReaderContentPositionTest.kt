package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.readerBodyListIndex
import com.breakyuna.esjzone.ui.reader.readerItemForContentPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
