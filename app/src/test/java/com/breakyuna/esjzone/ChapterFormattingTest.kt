package com.breakyuna.esjzone

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.data.reader.analyseChapterContent
import com.breakyuna.esjzone.data.reader.withFormatting
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterFormatting
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.ui.reader.readerChapterLinks
import com.breakyuna.esjzone.ui.reader.readerIndentLayout
import com.google.gson.Gson
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ChapterFormattingTest {
    private val source = """
        <p style="text-align: center;"><strong>༺ 真是该死的世界 ༻</strong></p>
        <hr><p>他写下的<ruby>游戏评测<rt>粪作排雷</rt></ruby>洋洋洒洒。</p>
    """.trimIndent()
    private val leadingBreakChapter = "<br><br>第一段<br>第二段<details><summary>作者的话</summary>附言</details>"

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

    @Test fun leadingBreaksDoNotHideProseBeforeAuthorNote() {
        val parsed = analyseChapterContent(Jsoup.parseBodyFragment(leadingBreakChapter).body())
        assertEquals(listOf("\n\n第一段\n第二段", "作者的话附言"), parsed.blocks.map { it.sourceText() })
        assertEquals(listOf(ReaderBlock.LineBreak),
            analyseChapterContent(Jsoup.parseBodyFragment("<p><br></p>").body()).blocks)
    }

    @Test fun anonymousIndentedProseKeepsParagraphsBlankLinesAndInlineLinks() {
        val html = "<br>\n<br>\n　　第一段。<br>\n　　<a href='/detail/2.html'>第二段</a>。" +
            "<br>\n<br>\n<br>\n<br>\n　　第三段。<br>同一段续行。" +
            "<details><summary>作者的话</summary>附言</details>"
        val document = Jsoup.parseBodyFragment(html, "https://www.esjzone.cc/forum/1/2.html")
        document.outputSettings().prettyPrint(false)
        val container = document.body()
        val parsed = analyseChapterContent(container)

        assertFalse(parsed.degraded)
        assertEquals(listOf("\n", "\n", "　　第一段。", "　　第二段。", "\n", "\n", "\n",
            "　　第三段。\n同一段续行。", "作者的话附言"), parsed.blocks.map { it.sourceText() })
        assertEquals(mapOf(8 to 1), parsed.formatting.disclosureTitleParts)
        assertEquals(listOf(9), parsed.formatting.dividersAt)
        val links = readerChapterLinks(parsed.blocks, container.html(), "https://www.esjzone.cc/forum/1/2.html")
        val link = links.getValue(3).single()
        assertEquals(2, link.start)
        assertEquals(5, link.end)
        assertEquals("https://www.esjzone.cc/detail/2.html", link.url)
    }

    @Test fun anonymousFlowHandlesCommentsTrailingBreaksAndTrimmedWhitespaceNodes() {
        val html = "<a href='/detail/2.html'>第一段</a> \n<br><br>　　<!-- 译注 --><span>第二段</span><br><br>" +
            "<details><summary>注</summary>附言</details>"
        val parsed = analyseChapterContent(Jsoup.parseBodyFragment(html, "https://www.esjzone.cc/forum/1/2.html").body())

        assertFalse(parsed.degraded)
        assertEquals(listOf("第一段", "\n", "　　第二段", "\n", "注附言"), parsed.blocks.map { it.sourceText() })
        val first = parsed.blocks.first() as ReaderBlock.Paragraph
        assertEquals(listOf("第一段"), first.parts.map { it.value })
    }

    @Test fun explicitParagraphKeepsItsHardBreaks() {
        val html = "<p>第一行<br>　　第二行<br><br>空行后正文。</p>"
        val parsed = analyseChapterContent(Jsoup.parseBodyFragment(html).body())
        assertEquals(listOf("第一行\n　　第二行\n\n空行后正文。"), parsed.blocks.map { it.sourceText() })
    }

    @Test fun previouslyMergedChapterRestoresAnAnchorInsideASeparatedParagraph() {
        val key = "/forum/1/2.html"
        val html = "<br>\n　　第一段。<br>\n<br>\n　　第二段的书签位置。"
        val old = body(html).copy(parserVersion = 3)
        val oldOffset = old.readerBlocks().single().sourceText().indexOf("书签位置")
        val saved = ReaderAnchor(key, old.fingerprint, 0, oldOffset)

        val rebuilt = old.rebuildFromHtmlIfOutdated(html, "https://www.esjzone.cc$key").validate()
        val document = ReaderChapterDocument(ReaderChapterRef("章节", key), rebuilt.readerBlocks(),
            contentFingerprint = rebuilt.fingerprint, legacyPositions = rebuilt.legacyPositions)
        val resolved = requireNotNull(saved.resolveIn(key, document))

        assertEquals(ChapterBody.CURRENT_PARSER_VERSION, rebuilt.parserVersion)
        assertEquals(3, resolved.blockIndex)
        assertEquals("书签位置。", document.blocks[resolved.blockIndex].sourceText().substring(resolved.offset))
    }

    @Test fun splitSnapshotPreservesOuterAlignmentAndKeepsExplicitAlignment() {
        val html = "甲<br><br>乙<p style='text-align: right'>尾声</p>"
        val old = body(html).copy(parserVersion = 3,
            formatting = ReaderChapterFormatting(alignments = mapOf(0 to "center", 1 to "center")))

        val rebuilt = old.rebuildFromHtmlIfOutdated(html, "https://www.esjzone.cc/forum/1/2.html").validate()

        assertEquals(listOf("甲", "\n", "乙", "尾声"), rebuilt.readerBlocks().map { it.sourceText() })
        assertEquals(mapOf(0 to "center", 2 to "center", 3 to "right"), rebuilt.formatting!!.alignments)
    }

    @Test fun splitSnapshotRestoresTheSameImageAndItsIntraImageOffset() {
        val key = "/forum/1/2.html"
        val html = "甲<br><br>乙<img src='/same.png'><p>丙<img src='/same.png'></p>"
        val old = body(html).copy(parserVersion = 3)
        val saved = ReaderAnchor(key, old.fingerprint, old.readerBlocks().indexOfLast { it is ReaderBlock.Image },
            kind = "image", fraction = 0.4f)
        val rebuilt = old.rebuildFromHtmlIfOutdated(html, "https://www.esjzone.cc$key").validate()
        val stored = Gson().fromJson(Gson().toJson(rebuilt), ChapterBody::class.java)
        val blocks = stored.readerBlocks { "file:///local/illustration.png" }
        val document = ReaderChapterDocument(ReaderChapterRef("章节", key), blocks,
            contentFingerprint = stored.fingerprint, legacyPositions = stored.legacyPositions)

        val resolved = requireNotNull(saved.resolveIn(key, document))
        assertNotEquals(saved.blockIndex, resolved.blockIndex)
        assertEquals(blocks.indexOfLast { it is ReaderBlock.Image }, resolved.blockIndex)
        assertEquals(saved.fraction, resolved.fraction, 0f)
        assertNull(saved.copy(fingerprint = "unrelated").resolveIn(key, document))

        val imageOnlyOld = ChapterBody.fromBlocks(listOf(ReaderBlock.Image("https://www.esjzone.cc/a.png")))
            .copy(parserVersion = 3)
        val imageOnlyUpgraded = imageOnlyOld.rebuildFromHtmlIfOutdated("<br><img src='/a.png'>", "https://www.esjzone.cc$key")
            .validate()
        assertNotNull(imageOnlyUpgraded.legacyPositions?.imageBlocks)
        val imageChanged = ChapterBody.fromBlocks(listOf(ReaderBlock.Image("https://www.esjzone.cc/b.png")))
            .inheritLegacyPositions(imageOnlyUpgraded)
        assertNull(imageChanged.legacyPositions)
    }

    @Test fun sourceIndentTakesThePlaceOfTheReaderIndentOnlyOnItsOwnHardLine() {
        val style = TextStyle(textIndent = TextIndent(firstLine = 40.sp))
        val source = AnnotatedString("　　第一行\n第二行\n 　　第三行")
        val layout = readerIndentLayout(source, style)
        val lines = layout.text.split('\u2028')
        assertEquals(source.text, lines.joinToString("\n"))
        assertEquals(listOf(0, lines[0].length + lines[1].length + 2), layout.paragraphStyles.map { it.start })
        assertTrue(layout.paragraphStyles.all { it.item.textIndent == TextIndent.None })
        val plain = AnnotatedString("正文。\n后文有　　空格")
        assertSame(plain, readerIndentLayout(plain, style))
    }

    @Test fun oldSnapshotRecoversDroppedProseFromSavedHtml() {
        val container = Jsoup.parseBodyFragment(leadingBreakChapter).body()
        val note = analyseChapterContent(container).blocks.last()
        val old = ChapterBody.fromBlocks(listOf(ReaderBlock.LineBreak, note)).copy(parserVersion = 1)
        val html = container.html()

        val recovered = old.recoverLeadingBreakText(html, "").validate()

        assertEquals("第一段第二段", recovered.readerBlocks().first().sourceText().filterNot(Char::isWhitespace))
        assertEquals(old.blocks.last(), recovered.blocks.last())
        assertNotEquals(old.fingerprint, recovered.fingerprint)
        assertSame(old, old.recoverLeadingBreakText(html.replace("附言", "不同的附言"), ""))
        assertSame(old, old.recoverLeadingBreakText(null, ""))
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

    @Test fun inlineAndManuallySpacedDisclosureBodiesShareTheSameLayout() {
        for (note in listOf("附言一<br><br>附言二", "-<br><br>附言一<br><br>附言二")) {
            val html = "<p>正文</p><details><summary><strong>作者</strong>的话</summary>$note</details><p>尾声</p>"
            val parsed = analyseChapterContent(Jsoup.parseBodyFragment(html).body())

            assertEquals(mapOf(1 to 2), parsed.formatting.disclosureTitleParts)
            assertEquals(listOf(2), parsed.formatting.dividersAt)
            assertEquals(body(html).fingerprint, ChapterBody.fromBlocks(parsed.blocks).fingerprint)
        }
    }

    @Test fun oldDownloadsCanRecoverDisclosureLayoutWithoutMovingTextAnchors() {
        val html = "<details><summary>作者的话</summary><p>第一段</p><p>第二段</p></details><p>尾声</p>"
        val gson = Gson()
        val encoded = gson.toJsonTree(body(html).copy(formatting = ReaderChapterFormatting(parserVersion = 1))).asJsonObject
        encoded.getAsJsonObject("formatting").remove("disclosureTitleParts")
        val old = gson.fromJson(encoded, ChapterBody::class.java).validate()
        val layout = old.withFormatting(Jsoup.parseBodyFragment(html).body().html(), null)

        assertEquals(mapOf(0 to 1), layout.formatting!!.disclosureTitleParts)
        assertEquals(listOf(3), layout.formatting!!.dividersAt)
        assertEquals(old.blocks, layout.blocks)
        assertEquals(old.fingerprint, layout.fingerprint)
    }

    @Test fun inheritedAlignmentAndRulesKeepTheirOriginalBlockPositions() {
        val html = "<hr><div style='text-align: right'><p>标题</p></div><hr><hr><p>正文</p><hr>"
        val result = body(html).withFormatting(html, null)
        assertEquals(mapOf(0 to "right"), result.formatting!!.alignments)
        assertEquals(listOf(0, 1, 1, 2), result.formatting!!.dividersAt)
        assertEquals(2, result.blocks.size)
    }

    @Test fun mixedContainersKeepAncestorAlignmentWithoutOverridingChildParagraphs() {
        val html = "<section style='text-align: center'><div>标题<p style='text-align: right'>正文</p>尾声</div></section>"
        val original = body(html)
        val parsed = analyseChapterContent(Jsoup.parseBodyFragment(html).body())
        assertEquals(listOf("标题", "正文", "尾声"), parsed.blocks.map { it.sourceText() })
        assertEquals(mapOf(0 to "center", 1 to "right", 2 to "center"), parsed.formatting.alignments)
        assertEquals(original.fingerprint, ChapterBody.fromBlocks(parsed.blocks).fingerprint)
        assertEquals(original.readerBlocks(), parsed.blocks)
    }

    @Test fun cachedFormattingWithoutSourceParentSupportIsRecoveredFromMatchingHtml() {
        val html = "<div style='text-align: center'>标题<p style='text-align: right'>正文</p>尾声</div>"
        val gson = Gson()
        val json = gson.toJsonTree(body(html).copy(
            formatting = ReaderChapterFormatting(alignments = mapOf(1 to "right"))
        )).asJsonObject
        json.getAsJsonObject("formatting").remove("parserVersion")
        val old = gson.fromJson(json, ChapterBody::class.java).validate()
        val restored = old.withFormatting(html, null)
        assertEquals(mapOf(0 to "center", 1 to "right", 2 to "center"), restored.formatting!!.alignments)
        assertEquals(old.fingerprint, restored.fingerprint)
        assertEquals(old.readerBlocks(), restored.readerBlocks())
        assertSame(restored, restored.withFormatting(html, null))
        // Without the original HTML, retain the usable formatting already on disk.
        assertSame(old, old.withFormatting(null, null))
    }
}
