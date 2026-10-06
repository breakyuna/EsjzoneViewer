package com.breakyuna.esjzone

import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineRange
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
        val first = ReaderUnderlines.update(emptyList(), mark(1, 3), false) { text }
        val merged = ReaderUnderlines.update(first, mark(2, 6), false) { text }
        assertEquals(listOf(mark(1, 6).copy(quote = text.substring(1, 6))), merged)
        val restored = ReaderUnderlines.decode(ReaderUnderlines.encode(merged))
        assertEquals(merged, restored)
        assertEquals(merged, ReaderUnderlines.update(restored, mark(2, 4), false))
        assertNull(ReaderUnderlines.decode("[{\"blockIndex\":0,\"signature\":\"$signature\",\"start\":1,\"end\":3}]").single().quote)
    }

    @Test fun consecutiveParagraphsAndOrdinaryNewlinesAreOneMark() {
        val ranges = listOf(ReaderUnderlineRange(0, signature, 1, 6), ReaderUnderlineRange(1, signature, 0, 3))
        val result = ReaderUnderlines.selection(listOf(ranges[0] to "甲。乙\n丙", ranges[1] to "丁戊己"))
        assertEquals(1, result.size)
        assertEquals(ranges, result.single().ranges())
    }

    @Test fun blankParagraphsAndNonTextBlocksSeparateMarks() {
        val result = ReaderUnderlines.selection(listOf(
            ReaderUnderlineRange(0, signature, 0, 2) to "甲乙",
            ReaderUnderlineRange(1, signature, 0, 2) to "　\n",
            ReaderUnderlineRange(2, signature, 0, 2) to "丙丁",
            ReaderUnderlineRange(4, signature, 0, 2) to "戊己"))
        assertEquals(listOf(0, 2, 4), result.map { it.blockIndex })
        assertTrue(result.all { it.continuation == null })
    }

    @Test fun whitespaceOnlyBlankLinesWithinOneBlockSeparateCharacterRanges() {
        val text = "甲乙\r\n　\t\r\n丙丁\n戊己"
        val result = ReaderUnderlines.selection(listOf(ReaderUnderlineRange(3, signature, 10, 10 + text.length) to text))
        assertEquals(listOf(mark(10, 12, 3), mark(18, 10 + text.length, 3)), result)
    }

    @Test fun blankLineAtParagraphBoundarySeparatesMarks() {
        val result = ReaderUnderlines.selection(listOf(
            ReaderUnderlineRange(0, signature, 0, 3) to "甲乙\n",
            ReaderUnderlineRange(1, signature, 0, 2) to "丙丁"))
        assertEquals(2, result.size)
    }

    @Test fun groupedQuoteRestoreMergeAndRemovalKeepTheEntireMarkTogether() {
        val text = mapOf(0 to "甲乙丙丁", 1 to "戊己庚辛")
        val selection = ReaderUnderlines.selection(listOf(
            ReaderUnderlineRange(0, signature, 1, 4) to "乙丙丁",
            ReaderUnderlineRange(1, signature, 0, 2) to "戊己")).single()
        val saved = ReaderUnderlines.update(emptyList(), selection, false, text::get)
        assertEquals("乙丙丁\n戊己", saved.single().quote)
        val restored = ReaderUnderlines.decode(ReaderUnderlines.encode(saved))
        assertEquals(saved, restored)
        assertEquals(saved, restored.fold(saved) { rows, row -> ReaderUnderlines.update(rows, row, false) })
        val extended = ReaderUnderlines.update(restored, mark(1, 4, 1), false, text::get)
        assertEquals(1, extended.size)
        assertEquals("乙丙丁\n戊己庚辛", extended.single().quote)
        assertEquals(listOf(ReaderUnderlineRange(0, signature, 1, 4), ReaderUnderlineRange(1, signature, 0, 4)),
            extended.single().ranges())
        assertTrue(ReaderUnderlines.update(extended, mark(2, 3, 1), true).isEmpty())
    }
}
