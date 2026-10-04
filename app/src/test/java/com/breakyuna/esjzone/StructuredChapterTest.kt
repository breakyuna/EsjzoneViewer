package com.breakyuna.esjzone

import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.data.repository.toReaderBlocks
import com.breakyuna.esjzone.domain.reader.*
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.ui.reader.ReaderMappedText
import com.google.gson.Gson
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class StructuredChapterTest {
    @Test
    fun roundTripPreservesParagraphsRubyStylesAndUnderlineSignatures() {
        val components = analyseComponents(Jsoup.parseBodyFragment("""
            <div><p>正文<strong>粗体<em>斜体</em></strong><br/>换行<ruby>漢<rt>かん</rt></ruby></p>
            <p>下一段😀</p><img src="/illustration.png"/></div>
        """, "https://www.esjzone.cc/forum/1/1.html").body())
        val original = components.flatMap { it.toReaderBlocks() }
        val encoded = Gson().toJson(ChapterBody.from(components))
        val restored = Gson().fromJson(encoded, ChapterBody::class.java).validate()
        assertEquals(original, restored.readerBlocks())
        original.zip(restored.readerBlocks()).forEach { (a, b) ->
            assertEquals(ReaderUnderlines.signature(a, a.sourceText()), ReaderUnderlines.signature(b, b.sourceText()))
        }
        assertTrue(restored.readerBlocks().any { it is ReaderBlock.Paragraph && it.parts.any { part -> part.ruby?.reading == "かん" } })
        assertEquals(components.filterIsInstance<TextComponent>().joinToString("") { component ->
            component.toAnnotatedString().text
        }.length, restored.textLength)
        assertEquals(restored.fingerprint, ChapterBody.from(restored.components()).fingerprint)
    }

    @Test
    fun localImageResolutionDoesNotChangeContentIdentity() {
        val stored = ChapterBody.from(listOf(TextComponent("正文"), ImageComponent("https://example.test/image.png")))
        assertEquals("file:/new-install/image.png", (stored.readerBlocks { "file:/new-install/image.png" }[1] as ReaderBlock.Image).url)
        assertEquals(stored.fingerprint, Gson().fromJson(Gson().toJson(stored), ChapterBody::class.java).validate().fingerprint)
    }

    @Test
    fun damagedOrFutureSnapshotIsRejected() {
        val body = ChapterBody.from(listOf(TextComponent("正文")))
        assertThrows(IllegalArgumentException::class.java) { body.copy(fingerprint = "0".repeat(64)).validate() }
        assertThrows(IllegalArgumentException::class.java) { body.copy(schemaVersion = 3).validate() }
    }

    @Test
    fun searchAcrossParagraphsReturnsBlockRangesAndSnapshotAnchor() {
        val body = ChapterBody.from(listOf(TextComponent("第一段末尾"), TextComponent("第二段开头😀")))
        val document = ReaderChapterDocument(ReaderChapterRef("标题", "/forum/1/1.html"), body.readerBlocks(), contentFingerprint = body.fingerprint)
        val hit = searchReaderDocument(document, "/forum/1/1.html", "末尾\n第二").single()
        assertEquals(listOf(ReaderHighlight(0, 3, 5), ReaderHighlight(1, 0, 2)), hit.highlights)
        assertEquals(3, hit.anchor.offset)
        assertTrue(hit.anchor.matches("/forum/1/1.html", document))
        assertFalse(hit.anchor.matches("/forum/1/2.html", document))
        assertEquals(hit.anchor, ReaderAnchor.decode(ReaderAnchor.encode(hit.anchor)))
        assertNull(ReaderAnchor.decode("{}"))
        val breakBody = ChapterBody.fromBlocks(listOf(ReaderBlock.LineBreak, ReaderBlock.Text("下一段")))
        val withBreak = document.copy(blocks = breakBody.readerBlocks(), contentFingerprint = breakBody.fingerprint)
        val afterBreak = searchReaderDocument(withBreak, "/forum/1/1.html", "\n\n下一").single()
        assertEquals(1, afterBreak.anchor.blockIndex)
        assertTrue(afterBreak.anchor.matches("/forum/1/1.html", withBreak))
        assertTrue(searchReaderDocument(document, "/forum/1/1.html", "😀").single().highlights.single().let { it.end - it.start == 2 })
    }

    @Test
    fun conversionTracksExpansionContractionAndCopiedRanges() {
        val text = ReaderMappedText("甲乙丙")
        text.replace(1, 2, "乙乙")
        val expanded = text.snapshot()
        assertEquals("甲乙乙丙", expanded.text)
        assertEquals(1, expanded.toSource(2))
        assertEquals(3, expanded.toDisplay(2))
        text.replace(1, 3, "乙")
        text.copy(0, 1, text.length())
        assertEquals("甲乙丙甲", text.snapshot().text)
        assertEquals(0, text.snapshot().sourceStarts.last())
        val copied = CharArray(3) { '-' }
        text.getChars(1, 3, copied, 1)
        assertArrayEquals(charArrayOf('-', '乙', '丙'), copied)
    }

    @Test
    fun sameLengthSubstitutionsPreserveExpandedSourceRanges() {
        val text = ReaderMappedText("甲乙丙")
        text.replace(1, 2, "乙乙")
        val before = text.snapshot()
        text.replace(0, text.length(), "丁戊己庚")
        val after = text.snapshot()
        assertEquals("丁戊己庚", after.text)
        assertArrayEquals(before.sourceStarts, after.sourceStarts)
        assertArrayEquals(before.sourceEnds, after.sourceEnds)
        assertEquals(1, after.toSource(2))
        assertEquals(3, after.toDisplay(2))
    }

    @Test
    fun searchConvertedTextMapsResultBackToSource() {
        val body = ChapterBody.from(listOf(TextComponent("甲乙丙")))
        val document = ReaderChapterDocument(ReaderChapterRef("标题", "/forum/1/1.html"), body.readerBlocks(), contentFingerprint = body.fingerprint)
        val offsets = ReaderMappedText("甲乙丙").apply { replace(1, 2, "乙乙") }.snapshot()
        val hit = searchReaderDocument(document, "/forum/1/1.html", "乙乙") { offsets }.single()
        assertEquals(ReaderHighlight(0, 1, 2), hit.highlights.single())
    }

    @Test
    fun contentProgressUsesSourceCharactersRatherThanParagraphCount() {
        val body = ChapterBody.from(listOf(TextComponent("短"), TextComponent("长".repeat(99))))
        val document = ReaderChapterDocument(ReaderChapterRef("标题", "/forum/1/1.html"), body.readerBlocks(), contentFingerprint = body.fingerprint)
        assertEquals(0.5f, readerChapterProgress(document, ReaderAnchor("/forum/1/1.html", body.fingerprint, 1, 49)), 0.0001f)
        assertEquals(1f, readerChapterProgress(document, ReaderAnchor("/forum/1/1.html", body.fingerprint, 1, kind = "end")), 0f)
    }

    @Test
    fun layoutFallbackUsesSourceCharactersAndPreservesImageFractions() {
        val document = ReaderChapterDocument(ReaderChapterRef("标题", "/forum/1/1.html"), listOf(
            ReaderBlock.Paragraph(listOf(ReaderBlock.Text("甲"), ReaderBlock.Text("😀乙"))),
            ReaderBlock.Image("image.png"), ReaderBlock.Text("下一段")
        ))
        val anchor = ReaderAnchor("/forum/1/1.html", "", 0, 3)
        assertEquals(0.75f, readerContentPosition(document, anchor), 0f)
        assertEquals(1f, readerContentPosition(document, anchor.copy(offset = 10)), 0f)
        assertEquals(1.4f, readerContentPosition(document, anchor.copy(blockIndex = 1, kind = "image", fraction = 0.4f)), 0f)
        assertEquals(-1f, readerContentPosition(document, anchor.copy(blockIndex = -1, kind = "heading")), 0f)
        assertEquals(3f, readerContentPosition(document, anchor.copy(kind = "end")), 0f)
    }

    @Test
    fun repeatedProgressQueriesDoNotTraverseTheDocumentAgain() {
        val values = listOf(
            ReaderBlock.Paragraph(listOf(ReaderBlock.Text("甲"), ReaderBlock.Text("😀"))),
            ReaderBlock.LineBreak, ReaderBlock.Image("image.png"), ReaderBlock.Text("乙".repeat(9))
        )
        var reads = 0
        val blocks = object : AbstractList<ReaderBlock>() {
            override val size: Int get() = values.size
            override fun get(index: Int): ReaderBlock { reads++; return values[index] }
        }
        val document = ReaderChapterDocument(ReaderChapterRef("标题", "/forum/1/1.html"), blocks)
        val anchor = ReaderAnchor("/forum/1/1.html", "", 3, 3)
        assertEquals(0.5f, readerChapterProgress(document, anchor), 0f)
        val initialReads = reads
        repeat(100) { assertEquals(0.5f, readerChapterProgress(document, anchor), 0f) }
        assertEquals(initialReads, reads)
        val changed = document.copy(blocks = listOf(ReaderBlock.Text("甲".repeat(20))))
        assertEquals(0.25f, readerChapterProgress(changed, anchor.copy(blockIndex = 0, offset = 5)), 0f)
    }
}
