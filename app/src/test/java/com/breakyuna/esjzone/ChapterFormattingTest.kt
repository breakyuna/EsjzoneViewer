package com.breakyuna.esjzone

import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.data.reader.analyseChapterContent
import com.breakyuna.esjzone.data.reader.withFormatting
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.google.gson.Gson
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ChapterFormattingTest {
    private val source = """
        <p style="text-align: center;"><strong>༺ 真是该死的世界 ༻</strong></p>
        <hr><p>他写下的<ruby>游戏评测<rt>粪作排雷</rt></ruby>洋洋洒洒。</p>
    """.trimIndent()

    private fun body(html: String = source): ChapterBody = ChapterBody.from(
        analyseComponents(Jsoup.parseBodyFragment(html, "https://www.esjzone.cc/forum/1/2.html").body())
    )

    @Test fun oldSnapshotRecoversFormattingWithoutChangingTextOrSavedAnchors() {
        val old = Gson().fromJson(Gson().toJson(body()), ChapterBody::class.java).validate()
        assertNull(old.formatting)
        val before = old.readerBlocks()
        val restored = old.withFormatting(source, "https://www.esjzone.cc/forum/1/2.html")
        assertEquals(mapOf(0 to "center"), restored.formatting!!.alignments)
        assertEquals(listOf(1), restored.formatting!!.dividersAt)
        assertEquals(old.fingerprint, restored.fingerprint)
        assertEquals(before, restored.readerBlocks())
        assertEquals(ReaderUnderlines.sourceSignature(before[1]),
            ReaderUnderlines.sourceSignature(restored.readerBlocks()[1]))
        val document = ReaderChapterDocument(ReaderChapterRef("第一章", "/forum/1/2.html"),
            restored.readerBlocks(), contentFingerprint = restored.fingerprint)
        assertTrue(ReaderAnchor("/forum/1/2.html", old.fingerprint, 1, 2)
            .matches("/forum/1/2.html", document))
        val paragraph = restored.readerBlocks()[1] as ReaderBlock.Paragraph
        assertEquals("粪作排雷", paragraph.parts.first { it.ruby != null }.ruby!!.reading)
        assertEquals("他写下的游戏评测洋洋洒洒。", paragraph.sourceText())
        val reread = Gson().fromJson(Gson().toJson(restored), ChapterBody::class.java).validate()
        assertEquals(restored.formatting, reread.formatting)
    }

    @Test fun newChapterParsesTextAndFormattingInOnePass() {
        val parsed = analyseChapterContent(Jsoup.parseBodyFragment(source).body())
        val existing = body()
        val newBody = ChapterBody.fromBlocks(parsed.blocks).copy(formatting = parsed.formatting)
        assertEquals(existing.readerBlocks(), newBody.readerBlocks())
        assertEquals(existing.fingerprint, newBody.fingerprint)
        assertEquals(mapOf(0 to "center"), newBody.formatting!!.alignments)
        assertEquals(listOf(1), newBody.formatting!!.dividersAt)
    }

    @Test fun onlyMatchingHtmlCanSupplyFormatting() {
        val old = body()
        val changed = source.replace("游戏评测", "不同的正文")
        val result = old.withFormatting(changed, null)
        assertNull(result.formatting)
        assertEquals(old.fingerprint, result.fingerprint)
        assertNull(old.withFormatting(null, null).formatting)
        assertSame(old, old.withFormatting("<p>正文无格式</p>", null))
    }

    @Test fun inheritedAlignmentAndRulesKeepTheirOriginalBlockPositions() {
        val html = "<hr><div style='text-align: right'><p>标题</p></div><hr><hr><p>正文</p><hr>"
        val result = body(html).withFormatting(html, null)
        assertEquals(mapOf(0 to "right"), result.formatting!!.alignments)
        assertEquals(listOf(0, 1, 1, 2), result.formatting!!.dividersAt)
        assertEquals(2, result.blocks.size)
    }
}
