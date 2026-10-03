package com.breakyuna.esjzone

import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import org.junit.Assert.*
import org.junit.Test

class ReaderUnderlinesTest {
    private val signature = ReaderUnderlines.signature(ReaderBlock.Text("这是保存的原始段落"), "这是保存的原始段落")
    private fun mark(start: Int, end: Int, block: Int = 0) = ReaderUnderline(block, signature, start, end)

    @Test fun savedRangesRoundTripAndRepeatedRestoreDoesNotDuplicate() {
        val rows = listOf(mark(1, 4), mark(6, 9))
        assertEquals(rows, ReaderUnderlines.decode(ReaderUnderlines.encode(rows)))
        assertEquals(rows.toSet(), rows.fold(rows) { result, row -> ReaderUnderlines.update(result, row, false) }.toSet())
    }

    @Test fun addingAcrossTwoMarksMergesOnlySameBlockAndSource() {
        val otherBlock = mark(1, 7, 1)
        val changedSource = mark(1, 7).copy(signature = "a".repeat(64))
        val result = ReaderUnderlines.update(listOf(mark(1, 4), mark(5, 9), otherBlock, changedSource), mark(3, 6), false)
        assertEquals(setOf(mark(1, 9), otherBlock, changedSource), result.toSet())
    }

    @Test fun removalPreservesUnselectedMarksAndChapterTextChangesInvalidateIdentity() {
        assertEquals(listOf(mark(6, 9)), ReaderUnderlines.update(listOf(mark(1, 4), mark(6, 9)), mark(2, 3), true))
        assertNotEquals(signature, ReaderUnderlines.signature(ReaderBlock.Text("原文已修改"), "原文已修改"))
        assertThrows(IllegalArgumentException::class.java) {
            ReaderUnderlines.decode(ReaderUnderlines.encode(listOf(mark(-1, 4))))
        }
    }

    @Test fun changedRenderedTextDoesNotReuseOffsetsFromAnotherScript() {
        val block = ReaderBlock.Text("蔿后文")
        val original = ReaderUnderlines.signature(block, "蔿后文")
        val simplified = ReaderUnderlines.signature(block, "𫇭后文")
        assertNotEquals(original, simplified)
        assertEquals(original, ReaderUnderlines.signature(block, "蔿后文"))
        assertFalse(ReaderUnderlines.overlaps(
            ReaderUnderline(0, original, 1, 2), ReaderUnderline(0, simplified, 1, 2)))
    }

    @Test fun missingRecordIsEmptyButInvalidStoredValuesAreRejected() {
        assertTrue(ReaderUnderlines.decode(null).isEmpty())
        assertTrue(ReaderUnderlines.decode("[]").isEmpty())
        for (value in listOf("", " \n\t", "null")) {
            assertThrows(IllegalArgumentException::class.java) { ReaderUnderlines.decode(value) }
        }
        assertThrows(com.google.gson.JsonParseException::class.java) { ReaderUnderlines.decode("{") }
    }
}
