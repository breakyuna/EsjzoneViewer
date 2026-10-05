package com.breakyuna.esjzone

import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import androidx.compose.ui.text.TextRange
import com.breakyuna.esjzone.ui.reader.readerUnderlineCharacterRange
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

    @Test fun characterSelectionDoesNotExpandToWordsOrSplitSupplementaryCharacters() {
        assertEquals(TextRange(2, 3), readerUnderlineCharacterRange("target", 2))
        assertEquals(TextRange(1, 3), readerUnderlineCharacterRange("甲𫇭乙", 1))
        assertEquals(TextRange(1, 3), readerUnderlineCharacterRange("甲𫇭乙", 2))
        assertEquals(TextRange(3, 4), readerUnderlineCharacterRange("甲𫇭乙", 3))
    }

    @Test fun mergedQuoteContainsEntireSavedRangeAndSurvivesSerialization() {
        val text = "任意选择划线范围"
        val first = ReaderUnderlines.update(emptyList(), mark(1, 3), false, text)
        val merged = ReaderUnderlines.update(first, mark(2, 6), false, text)
        assertEquals(listOf(mark(1, 6).copy(quote = text.substring(1, 6))), merged)
        val restored = ReaderUnderlines.decode(ReaderUnderlines.encode(merged))
        assertEquals(merged, restored)
        assertEquals(merged, ReaderUnderlines.update(restored, mark(2, 4), false))
        assertNull(ReaderUnderlines.decode("[{\"blockIndex\":0,\"signature\":\"$signature\",\"start\":1,\"end\":3}]").single().quote)
    }
}
