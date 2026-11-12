package com.breakyuna.esjzone

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.backup.LocalBackup
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderBookmarkPosition
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets
import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.ui.page.readerPageIndexForAnchor
import com.breakyuna.esjzone.ui.reader.ReaderNameReplace
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceRule
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceSettings
import com.breakyuna.esjzone.ui.reader.ReaderPage
import com.breakyuna.esjzone.ui.reader.ReaderPageSegment
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBookmarkPositionTest {
    private val key = "/forum/9001/1.html"
    private val fingerprint = "a".repeat(64)
    private val source = "前言。艾莉絲說這裡很安靜。"
    private val document = ReaderChapterDocument(ReaderChapterRef("Chapter", key), listOf(
        ReaderBlock.Image("cover.jpg"),
        ReaderBlock.Text(source),
        ReaderBlock.Text("   "),
        ReaderBlock.Paragraph(listOf(ReaderBlock.Text("第二段"), ReaderBlock.Text("繼續")))
    ), contentFingerprint = fingerprint)

    /** Stands in for ICU: one display character per source character, like Traditional→Simplified. */
    private fun simplified(block: ReaderBlock): ReaderTextOffsets {
        val text = block.sourceText().map { mapOf('說' to '说', '這' to '这', '裡' to '里', '靜' to '静', '繼' to '继', '續' to '续')[it] ?: it }
            .joinToString("")
        return ReaderTextOffsets(text, IntArray(text.length) { it }, IntArray(text.length) { it + 1 }, text.length)
    }

    private val names = ReaderNameReplaceSettings(enabled = true,
        rules = listOf(ReaderNameReplaceRule(source = "艾莉絲", target = "爱丽丝大人")))

    private fun displayed(block: ReaderBlock) = ReaderNameReplace.blockMapping(block, names, ::simplified)

    @Test
    fun anchorUsesSourceCoordinatesThroughScriptConversionAndNameReplacement() {
        val display = displayed(document.blocks[1])
        assertEquals("前言。爱丽丝大人说这里很安静。", display.text)
        // The reader stores the source offset of the first displayed character on the page/line.
        val saidDisplay = display.text.indexOf('说')
        val anchor = ReaderAnchor(key, fingerprint, 1, display.toSource(saidDisplay))
        assertEquals(source.indexOf('說'), anchor.offset)
        assertEquals(saidDisplay, display.toDisplay(anchor.offset))
        // The same anchor reads correctly with and without display transforms.
        assertEquals("说这里很安静。", ReaderBookmarkPosition.excerpt(document, anchor, ::displayed))
        assertEquals("說這裡很安靜。", ReaderBookmarkPosition.excerpt(document, anchor, { null }))
        val nameStart = ReaderAnchor(key, fingerprint, 1, display.toSource(display.text.indexOf('爱')))
        assertEquals(source.indexOf('艾'), nameStart.offset)
        assertEquals("爱丽丝大人说这里很安静。", ReaderBookmarkPosition.excerpt(document, nameStart, ::displayed))
    }

    @Test
    fun excerptSkipsImagesAndBlankBlocksAndIsShortAndCodePointSafe() {
        val heading = ReaderAnchor(key, fingerprint, -1, kind = "heading")
        assertEquals("前言。爱丽丝大人说这里很安静。", ReaderBookmarkPosition.excerpt(document, heading, ::displayed))
        val image = ReaderAnchor(key, fingerprint, 0, kind = "image", fraction = 0.5f)
        assertEquals("前言。艾莉絲說這裡很安靜。", ReaderBookmarkPosition.excerpt(document, image, { null }))
        val atEnd = ReaderAnchor(key, fingerprint, 1, source.length)
        assertEquals("第二段继续", ReaderBookmarkPosition.excerpt(document, atEnd, ::displayed))
        assertNull(ReaderBookmarkPosition.excerpt(document.copy(blocks = listOf(ReaderBlock.Text(" \n "))),
            ReaderAnchor(key, fingerprint, 0), { null }))

        val long = "😀" + "字".repeat(60) + "\n\n  尾"
        val shortened = ReaderBookmarkPosition.excerpt(document.copy(blocks = listOf(ReaderBlock.Text(long))),
            ReaderAnchor(key, fingerprint, 0), { null })!!
        assertEquals(ReaderBookmarkPosition.EXCERPT_LENGTH + 1, shortened.codePointCount(0, shortened.length))
        assertEquals("😀", shortened.substring(0, 2))
        assertEquals('…', shortened.last())
        assertEquals("a b", ReaderBookmarkPosition.excerpt(document.copy(blocks = listOf(ReaderBlock.Text(" a \n\t b "))),
            ReaderAnchor(key, fingerprint, 0), { null }))
    }

    private fun line(blockIndex: Int, startOffset: Int) =
        ReaderPageSegment.TextLine(AnnotatedString("x"), emptyMap(), 0.dp, blockIndex, "", startOffset)

    @Test
    fun pagedModeFindsThePageContainingTheAnchorForAnyPagination() {
        val anchor = ReaderAnchor(key, fingerprint, 3, 7)
        // Default layout: heading, block 1 split twice, block 3 split at display offset 5.
        val defaultPages = listOf(ReaderPage(emptyList(), -1f), ReaderPage(listOf(line(1, 0)), 1f),
            ReaderPage(listOf(line(1, 6)), 1.4f), ReaderPage(listOf(line(3, 0)), 3f), ReaderPage(listOf(line(3, 5)), 3.5f))
        assertEquals(4, readerPageIndexForAnchor(defaultPages, anchor) { it })
        // A larger font or first-line indent moves line breaks; the anchor still selects its own page.
        val largerPages = listOf(ReaderPage(emptyList(), -1f), ReaderPage(listOf(line(1, 0)), 1f),
            ReaderPage(listOf(line(3, 0)), 3f), ReaderPage(listOf(line(3, 3)), 3.3f), ReaderPage(listOf(line(3, 8)), 3.8f))
        assertEquals(3, readerPageIndexForAnchor(largerPages, anchor) { it })
        // Page starts are display offsets; a longer replacement name must be mapped back to source.
        val display = displayed(document.blocks[1])
        val nameAnchor = ReaderAnchor(key, fingerprint, 1, source.indexOf('說'))
        val replacedPages = listOf(ReaderPage(emptyList(), -1f), ReaderPage(listOf(line(1, 0)), 1f),
            ReaderPage(listOf(line(1, display.text.indexOf('说'))), 1.5f), ReaderPage(listOf(line(1, display.text.indexOf('很'))), 1.8f))
        assertEquals(2, readerPageIndexForAnchor(replacedPages, nameAnchor, display::toSource))
        // Comparing display page starts with the source offset would land one page early.
        assertEquals(1, readerPageIndexForAnchor(replacedPages, nameAnchor) { it })
        assertEquals(0, readerPageIndexForAnchor(listOf(null, null), anchor) { it })
    }

    @Test
    fun dividerPageKeepsItsActualPositionAtTheEndOfThePreviousBlock() {
        val pages = listOf(ReaderPage(listOf(line(1, 0)), 1f),
            ReaderPage(listOf(line(1, 5)), 1.5f),
            ReaderPage(listOf(ReaderPageSegment.Divider(1, 10)), 1.999f))
        assertEquals(1, readerPageIndexForAnchor(pages, ReaderAnchor(key, fingerprint, 1, 7)) { it })
        assertEquals(2, readerPageIndexForAnchor(pages, ReaderAnchor(key, fingerprint, 1, 10)) { it })
    }

    @Test
    fun changedBodyKeepsTheParagraphIndexClampedToTheCurrentChapter() {
        val exact = ReaderAnchor(key, fingerprint, 1, 6)
        assertNull(ReaderBookmarkPosition.fallbackContentPosition(document, key, exact))
        assertNull(ReaderBookmarkPosition.fallbackContentPosition(document, key, null))
        val changed = exact.copy(fingerprint = "b".repeat(64))
        assertEquals(1f + 6f / source.length, ReaderBookmarkPosition.fallbackContentPosition(document, key, changed)!!, 0.0001f)
        assertEquals(3f, ReaderBookmarkPosition.fallbackContentPosition(document, key, changed.copy(blockIndex = 99, offset = 0))!!, 0f)
        assertEquals(3.999f, ReaderBookmarkPosition.fallbackContentPosition(document, key, changed.copy(blockIndex = 3, offset = 999))!!, 0.0001f)
        assertEquals(-1f, ReaderBookmarkPosition.fallbackContentPosition(document, key,
            changed.copy(blockIndex = -1, kind = "heading"))!!, 0f)
        assertNull(ReaderBookmarkPosition.fallbackContentPosition(document.copy(blocks = emptyList()), key, changed))
    }

    @Test
    fun chapterOnlyAndUnreadableBookmarkPositionsFallBackToTheChapterStart() {
        val legacy = Bookmark("/forum/9001/1.html", "9001", "Novel", "Chapter 1", 10L)
        assertNull(legacy.anchor)
        assertNull(legacy.excerpt)
        assertNull(ReaderAnchor.decode(legacy.anchor))

        val anchor = ReaderAnchor.encode(ReaderAnchor(key, fingerprint, 1, 6))
        val rows = LocalBackup.decodeBookmarks(JsonParser.parseString("""[
            {"chapterUrl":"/forum/9001/1.html","novelId":"9001","novelName":"Novel","chapterName":"Old","createdAt":1},
            {"chapterUrl":"/forum/9001/2.html","novelId":"9001","novelName":"Novel","chapterName":"Bad","createdAt":2,
             "anchor":"{\"chapterKey\":\"x\",\"fingerprint\":\"short\",\"blockIndex\":1}","excerpt":"stale"},
            {"chapterUrl":"/forum/9001/3.html","novelId":"9001","novelName":"Novel","chapterName":"New","createdAt":3,
             "anchor":${Gson().toJson(anchor)},"excerpt":"说这里很安静。"}
        ]"""))
        assertNull(rows[0].anchor)
        assertNull(rows[0].excerpt)
        assertNull(rows[1].anchor)
        assertNull(rows[1].excerpt)
        assertEquals(rows.size, rows.map { it.id }.distinct().size)
        assertTrue(rows.all { it.id.isNotBlank() })
        assertEquals(anchor, rows[2].anchor)
        assertEquals("说这里很安静。", rows[2].excerpt)
        // New rows round-trip through the same Gson field names used by export.
        assertEquals(rows[2], LocalBackup.decodeBookmarks(Gson().toJsonTree(listOf(rows[2]))).single())
    }
}
