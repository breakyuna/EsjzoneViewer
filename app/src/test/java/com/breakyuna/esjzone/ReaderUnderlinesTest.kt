package com.breakyuna.esjzone

import androidx.compose.ui.text.TextRange
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineInfo
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineRange
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.ui.page.resolveUnderlineRows
import com.breakyuna.esjzone.ui.reader.readerUnderlineCharacterRange
import org.junit.Assert.*
import org.junit.Test

class ReaderUnderlinesTest {
    private val signature = ReaderUnderlines.signature(ReaderBlock.Text("这是保存的原始段落"), "这是保存的原始段落")
    private fun mark(start: Int, end: Int, block: Int = 0) = ReaderUnderline(block, signature, start, end)
    /** Quotes slice the displayed block text of each saved range. */
    private fun quotes(text: (Int) -> String?): (ReaderUnderlineRange) -> String? = { range ->
        text(range.blockIndex)?.takeIf { range.end <= it.length }?.substring(range.start, range.end)
    }

    @Test fun savedRangesRoundTripAndRepeatedRestoreDoesNotDuplicate() {
        val rows = listOf(mark(1, 4), mark(6, 9))
        assertEquals(rows, ReaderUnderlines.decode(ReaderUnderlines.encode(rows)))
        assertEquals(rows.toSet(), rows.fold(rows) { result, row -> ReaderUnderlines.update(result, row, false) }.toSet())
    }

    @Test fun chapterInformationSurvivesMergeAndBackupSerialization() {
        val info = ReaderUnderlineInfo(novelId = "123", novelName = "Novel", chapterName = "Chapter 1")
        val saved = mark(1, 4).copy(quote = "文本", info = info)
        assertEquals(saved, ReaderUnderlines.decode(ReaderUnderlines.encode(listOf(saved))).single())
        val merged = ReaderUnderlines.update(listOf(saved), mark(2, 5), false)
        assertEquals(info, merged.single().info)
        val restored = ReaderUnderlines.decode(ReaderUnderlines.encode(merged))
        assertEquals(merged, restored.fold(emptyList<ReaderUnderline>()) { rows, row -> ReaderUnderlines.update(rows, row, false) })
        val legacy = ReaderUnderlines.decode("[{\"blockIndex\":0,\"signature\":\"$signature\",\"start\":1,\"end\":3}]").single()
        assertNull(legacy.info)
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
        assertTrue(ReaderUnderlines.decode(ReaderUnderlines.encode(listOf(mark(-1, 4)))).isEmpty())
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

    @Test fun decodeTreatsMissingAsEmptySkipsInvalidEntriesAndRejectsMalformedRoot() {
        assertTrue(ReaderUnderlines.decode(null).isEmpty())
        assertTrue(ReaderUnderlines.decode("[]").isEmpty())
        for (value in listOf("", " \n\t", "null")) {
            assertThrows(IllegalArgumentException::class.java) { ReaderUnderlines.decode(value) }
        }
        assertThrows(com.google.gson.JsonParseException::class.java) { ReaderUnderlines.decode("{") }
        val valid = mark(1, 4)
        val reordered = mark(0, 2, 2).copy(continuation = listOf(ReaderUnderlineRange(1, signature, 0, 2)))
        val repeated = mark(0, 2).copy(continuation = listOf(ReaderUnderlineRange(0, signature, 1, 3)))
        for (invalid in listOf(reordered, repeated)) {
            assertEquals(listOf(valid), ReaderUnderlines.decode(ReaderUnderlines.encode(listOf(invalid, valid))))
        }
    }

    @Test fun characterSelectionDoesNotExpandToWordsOrSplitSupplementaryCharacters() {
        assertEquals(TextRange(2, 3), readerUnderlineCharacterRange("target", 2))
        assertEquals(TextRange(1, 3), readerUnderlineCharacterRange("甲𫇭乙", 1))
        assertEquals(TextRange(1, 3), readerUnderlineCharacterRange("甲𫇭乙", 2))
        assertEquals(TextRange(3, 4), readerUnderlineCharacterRange("甲𫇭乙", 3))
    }

    @Test fun mergedQuoteContainsEntireSavedRangeAndSurvivesSerialization() {
        val text = "任意选择划线范围"
        val first = ReaderUnderlines.update(emptyList(), mark(1, 3), false, quotes { text })
        val merged = ReaderUnderlines.update(first, mark(2, 6), false, quotes { text })
        assertEquals(listOf(mark(1, 6).copy(quote = text.substring(1, 6))), merged)
        val restored = ReaderUnderlines.decode(ReaderUnderlines.encode(merged))
        assertEquals(merged, restored)
        assertEquals(merged, ReaderUnderlines.update(restored, mark(2, 4), false))
        assertNull(ReaderUnderlines.decode("[{\"blockIndex\":0,\"signature\":\"$signature\",\"start\":1,\"end\":3}]").single().quote)
    }

    @Test fun selectionGroupsConsecutiveParagraphsAndSplitsOnBlankLinesOrNonTextBlocks() {
        val consecutive = listOf(ReaderUnderlineRange(0, signature, 1, 6), ReaderUnderlineRange(1, signature, 0, 3))
        val joined = ReaderUnderlines.selection(listOf(consecutive[0] to "甲。乙\n丙", consecutive[1] to "丁戊己"))
        assertEquals(1, joined.size)
        assertEquals(consecutive, joined.single().ranges())

        val separated = ReaderUnderlines.selection(listOf(
            ReaderUnderlineRange(0, signature, 0, 2) to "甲乙",
            ReaderUnderlineRange(1, signature, 0, 2) to "　\n",
            ReaderUnderlineRange(2, signature, 0, 2) to "丙丁",
            ReaderUnderlineRange(4, signature, 0, 2) to "戊己"))
        assertEquals(listOf(0, 2, 4), separated.map { it.blockIndex })
        assertTrue(separated.all { it.continuation == null })

        val text = "甲乙\r\n　\t\r\n丙丁\n戊己"
        val splitBlock = ReaderUnderlines.selection(listOf(ReaderUnderlineRange(3, signature, 10, 10 + text.length) to text))
        assertEquals(listOf(mark(10, 12, 3), mark(18, 10 + text.length, 3)), splitBlock)

        val boundaryBlank = ReaderUnderlines.selection(listOf(
            ReaderUnderlineRange(0, signature, 0, 3) to "甲乙\n",
            ReaderUnderlineRange(1, signature, 0, 2) to "丙丁"))
        assertEquals(2, boundaryBlank.size)
    }

    @Test fun groupedQuoteRestoreMergeAndRemovalKeepTheEntireMarkTogether() {
        val text = mapOf(0 to "甲乙丙丁", 1 to "戊己庚辛")
        val selection = ReaderUnderlines.selection(listOf(
            ReaderUnderlineRange(0, signature, 1, 4) to "乙丙丁",
            ReaderUnderlineRange(1, signature, 0, 2) to "戊己")).single()
        val saved = ReaderUnderlines.update(emptyList(), selection, false, quotes(text::get))
        assertEquals("乙丙丁\n戊己", saved.single().quote)
        val restored = ReaderUnderlines.decode(ReaderUnderlines.encode(saved))
        assertEquals(saved, restored)
        assertEquals(saved, restored.fold(saved) { rows, row -> ReaderUnderlines.update(rows, row, false) })
        val extended = ReaderUnderlines.update(restored, mark(1, 4, 1), false, quotes(text::get))
        assertEquals(1, extended.size)
        assertEquals("乙丙丁\n戊己庚辛", extended.single().quote)
        assertEquals(listOf(ReaderUnderlineRange(0, signature, 1, 4), ReaderUnderlineRange(1, signature, 0, 4)),
            extended.single().ranges())
        assertTrue(ReaderUnderlines.update(extended, mark(2, 3, 1), true).isEmpty())
    }

    @Test fun oldAndNewMarksResolveIndependentlyFromLocalContext() {
        val chapter = "/forum/123/1.html"
        val other = "/forum/456/2.html"
        val first = ReaderUnderline(1, "a".repeat(64), 1, 3, quote = "第一处")
        val second = ReaderUnderline(5, "b".repeat(64), 0, 2, quote = "第二处")
        val newer = ReaderUnderline(2, "c".repeat(64), 0, 4, quote = "新划线",
            info = ReaderUnderlineInfo(novelId = "456", novelName = "另一本书", chapterName = "第二章"))
        val bookmark = Bookmark(chapter, "123", "测试小说", "第一章")
        val history = LocalReadingActivity("novel:123", "123", "测试小说", "/detail/123.html",
            "/forum/123/9.html", "第九章", 8, 10, 0.5f, 1L, 2L, 10L)

        val rows = resolveUnderlineRows(mapOf(chapter to listOf(first, second), other to listOf(newer)),
            listOf(bookmark), listOf(history), emptyList(), emptyList())
        val old = rows.filter { it.chapterKey == chapter }
        assertEquals(2, old.size)
        assertEquals(listOf("第一章", "第一章"), old.map { it.chapterName })
        assertEquals(listOf("测试小说", "测试小说"), old.map { it.novelName })
        assertEquals(listOf("第一处", "第二处"), old.map { it.mark.quote })
        assertNotEquals(old[0].id, old[1].id)
        assertEquals("另一本书", rows.single { it.chapterKey == other }.novelName)
        assertEquals("第二章", rows.single { it.chapterKey == other }.chapterName)
    }

    @Test fun missingMetadataDoesNotHideTheSavedMark() {
        val mark = ReaderUnderline(0, "a".repeat(64), 0, 1)
        val row = resolveUnderlineRows(mapOf("/forum/123/1.html" to listOf(mark)), emptyList(),
            emptyList(), emptyList(), emptyList()).single()
        assertEquals(mark, row.mark)
        assertEquals("123", row.novelId)
        assertEquals("", row.chapterName)
        assertEquals("/detail/123.html", row.novelUrl)
    }

    @Test fun duplicateSavedRangesProduceOneStableVisibleEntry() {
        val mark = ReaderUnderline(0, "a".repeat(64), 0, 1)
        val rows = resolveUnderlineRows(mapOf("/forum/123/1.html" to listOf(mark, mark.copy(quote = "摘录"))),
            emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(1, rows.size)
        assertEquals("摘录", rows.single().mark.quote)
    }
}
