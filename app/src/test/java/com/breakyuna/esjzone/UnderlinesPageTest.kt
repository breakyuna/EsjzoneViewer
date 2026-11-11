package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineInfo
import com.breakyuna.esjzone.ui.page.resolveUnderlineRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UnderlinesPageTest {
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
