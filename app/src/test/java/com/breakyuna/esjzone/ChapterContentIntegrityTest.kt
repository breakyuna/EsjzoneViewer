package com.breakyuna.esjzone

import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.data.reader.ParsedChapterContent
import com.breakyuna.esjzone.data.reader.analyseChapterContent
import com.breakyuna.esjzone.data.reader.extractBlocksContentSnapshot
import com.breakyuna.esjzone.data.reader.extractDomContentSnapshot
import com.breakyuna.esjzone.data.reader.isContentConserved
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterFormatting
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.ReaderRuby
import com.breakyuna.esjzone.domain.reader.ReaderTextStyle
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.network.features.selectChapterContentElement
import com.breakyuna.esjzone.novellibrary.component.NewLineComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.data.repository.toReaderBlocks
import com.breakyuna.esjzone.ui.page.InAppBrowserActivity
import com.breakyuna.esjzone.ui.reader.ReaderBookmarkMarker
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceRule
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceSettings
import com.breakyuna.esjzone.ui.reader.readerUnderlineAnchors
import com.breakyuna.esjzone.ui.reader.readerUnderlinePosition
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChapterContentIntegrityTest {

    @Test
    fun faultInjectionTriggersLosslessFallbackWhenPrimaryParserDropsProse() {
        val html = "<br><br>第一段正文内容<br>第二段正文内容<details><summary>作者的话</summary>附言内容</details>"
        val container = Jsoup.parseBodyFragment(html, "https://www.esjzone.cc/forum/1/101.html").body()

        // Deliberately drop the opening prose in the primary parser to verify conservation fallback.
        val recovered = analyseChapterContent(container) {
            val onlyNote = listOf(ReaderBlock.LineBreak, ReaderBlock.Text("作者的话附言内容"))
            ParsedChapterContent(
                components = ChapterBody.fromBlocks(onlyNote).components(),
                blocks = onlyNote,
                formatting = ReaderChapterFormatting(parserVersion = ReaderChapterFormatting.CURRENT_PARSER_VERSION)
            )
        }

        assertTrue(recovered.degraded)
        assertTrue(recovered.formatting.degraded)
        val expected = extractDomContentSnapshot(container)
        val actual = extractBlocksContentSnapshot(recovered.blocks)
        assertTrue(isContentConserved(expected, actual))
        val joined = recovered.blocks.joinToString("") { it.sourceText() }
        assertTrue(joined.contains("第一段正文内容"))
        assertTrue(joined.contains("第二段正文内容"))
        assertTrue(joined.contains("附言内容"))
    }

    @Test
    fun primaryExceptionFallsBackAndLogsTheConservationRecheck() {
        val container = Jsoup.parseBodyFragment(
            """<p>前<ruby><span>漢<img src="/ruby.png">字</span><rt>かんじ</rt></ruby>后</p>""",
            "https://www.esjzone.cc/forum/1/2.html"
        ).body()
        val previousLogId = AppLogger.logsFlow.value.lastOrNull()?.id ?: 0L

        val parsed = analyseChapterContent(container) { error("Rich parser failed") }

        assertTrue(parsed.degraded)
        assertTrue(parsed.formatting.degraded)
        assertEquals(listOf("前漢", "", "字后"), parsed.blocks.map { it.sourceText() })
        assertEquals(ReaderBlock.Image("https://www.esjzone.cc/ruby.png"), parsed.blocks[1])
        assertTrue(isContentConserved(extractDomContentSnapshot(container), extractBlocksContentSnapshot(parsed.blocks)))
        assertTrue(AppLogger.logsFlow.value.any {
            it.id > previousLogId && it.tag == "ChapterIntegrity" &&
                it.message == "Lossless fallback passed content conservation"
        })
    }

    @Test
    fun primaryCancellationIsPropagated() {
        val container = Jsoup.parseBodyFragment("<p>正文</p>").body()
        val cancellation = CancellationException("Chapter loading cancelled")

        val result = runCatching { analyseChapterContent(container) { throw cancellation } }

        assertSame(cancellation, result.exceptionOrNull())
    }

    @Test
    fun misplacedImageTriggersFallbackEvenWhenTextAndImageListsMatch() {
        val image = ReaderBlock.Image("https://img.example/art.png")
        val container = Jsoup.parseBodyFragment("""<p>前文<img src="${image.url}">后文</p>""").body()
        val misplaced = listOf(ReaderBlock.Text("前文后文"), image)

        assertFalse(isContentConserved(extractDomContentSnapshot(container), extractBlocksContentSnapshot(misplaced)))
        val parsed = analyseChapterContent(container) {
            ParsedChapterContent(emptyList(), misplaced, ReaderChapterFormatting())
        }

        assertTrue(parsed.degraded)
        assertEquals(
            listOf(
                ReaderBlock.Paragraph(listOf(ReaderBlock.Text("前文"))),
                image,
                ReaderBlock.Paragraph(listOf(ReaderBlock.Text("后文")))
            ),
            parsed.blocks
        )
    }

    @Test
    fun rubyConservationAcceptsStyledSplitsAndRejectsExtraOrMovedReadings() {
        val container = Jsoup.parseBodyFragment("<p>前<ruby><b>漢</b>字<rt>かんじ</rt></ruby>后</p>").body()
        val expected = extractDomContentSnapshot(container)
        val splitRuby = listOf(
            ReaderBlock.Text("前"),
            ReaderBlock.Text("漢", setOf(ReaderTextStyle.Bold), ReaderRuby("漢", "かんじ")),
            ReaderBlock.Text("字", ruby = ReaderRuby("字", "かんじ")),
            ReaderBlock.Text("后")
        )
        val extra = splitRuby.dropLast(1) + ReaderBlock.Text("后", ruby = ReaderRuby("后", "かんじ"))
        val moved = listOf(
            ReaderBlock.Text("前漢", ruby = ReaderRuby("前漢", "かんじ")),
            ReaderBlock.Text("字后")
        )
        val missing = listOf(ReaderBlock.Text("前漢字后"))

        assertTrue(isContentConserved(expected, extractBlocksContentSnapshot(listOf(ReaderBlock.Paragraph(splitRuby)))))
        assertFalse(isContentConserved(expected, extractBlocksContentSnapshot(extra)))
        assertFalse(isContentConserved(expected, extractBlocksContentSnapshot(moved)))
        assertFalse(isContentConserved(expected, extractBlocksContentSnapshot(missing)))
    }

    @Test
    fun multiRtRubyPreservesEveryReadingInOrder() {
        val html = "<p>发动<ruby>魔<rt>ま</rt>法<rt>ほう</rt></ruby>与<ruby><rb>剑</rb><rt>けん</rt><rb>技</rb><rt>ぎ</rt></ruby></p>"
        val container = Jsoup.parseBodyFragment(html, "https://www.esjzone.cc/forum/1/2.html").body()
        val parsed = analyseChapterContent(container)

        assertFalse(parsed.degraded)
        val paragraph = parsed.blocks.single() as ReaderBlock.Paragraph
        val rubies = paragraph.parts.mapNotNull { it.ruby }.map { it.base to it.reading }
        assertEquals(listOf("魔" to "ま", "法" to "ほう", "剑" to "けん", "技" to "ぎ"), rubies)
        assertTrue(isContentConserved(extractDomContentSnapshot(container), extractBlocksContentSnapshot(parsed.blocks)))
    }

    @Test
    fun nestedTableAndBlockInsideInlinePreserveParagraphBoundaries() {
        val tableHtml = "<table><tbody><tr><td>技能</td><td>火焰球</td></tr><tr><td>等级</td><td>Lv.9</td></tr></tbody></table>"
        val tableParsed = analyseChapterContent(Jsoup.parseBodyFragment(tableHtml).body())
        assertEquals(listOf("技能", "火焰球", "等级", "Lv.9"), tableParsed.blocks.map { it.sourceText() })
        assertFalse(tableParsed.degraded)

        val wrappedHtml = "<span><p>第一段</p><p>第二段</p></span><center><p>居中段</p></center>"
        val wrappedParsed = analyseChapterContent(Jsoup.parseBodyFragment(wrappedHtml).body())
        assertEquals(listOf("第一段", "第二段", "居中段"), wrappedParsed.blocks.map { it.sourceText() })
        assertFalse(wrappedParsed.degraded)
    }

    @Test
    fun broaderInlineTagsAndHexColorsArePreserved() {
        val html = "<p><b>粗体</b><i>斜体</i><del>删除</del><font color=\"#ff0000\">红字</font><span style=\"color:#0f0;\">绿字</span></p>"
        val parsed = analyseChapterContent(Jsoup.parseBodyFragment(html).body())
        assertFalse(parsed.degraded)
        val parts = (parsed.blocks.single() as ReaderBlock.Paragraph).parts
        assertTrue(ReaderTextStyle.Bold in parts[0].styles)
        assertTrue(ReaderTextStyle.Italic in parts[1].styles)
        assertTrue(ReaderTextStyle.StrikeThrough in parts[2].styles)
        assertTrue(ReaderTextStyle.ForegroundColor(255, 0, 0) in parts[3].styles)
        assertTrue(ReaderTextStyle.ForegroundColor(0, 255, 0) in parts[4].styles)
    }

    @Test
    fun newLineComponentWithExtrasNeverDropsTextDuringAdapterConversion() {
        val leadingBreak = NewLineComponent().apply {
            append(TextComponent("绝不能丢失的正文"))
        }
        val blocks = leadingBreak.toReaderBlocks()
        assertEquals(1, blocks.size)
        assertTrue(blocks.single().sourceText().contains("绝不能丢失的正文"))
    }

    @Test
    fun selectChapterContentElementPrefersUniqueMt3EvenInsideAnOrdinaryContainer() {
        val doc = Jsoup.parse(
            """
            <div class="forum-content">
              <p>公告</p><img src="/banner1.png"><img src="/banner2.png">
              <div class="forum-content mt-3" id="chapter"><p>章节正文</p></div>
            </div>
            <div class="comments-section">
              <div class="forum-content mt-3"><img src="/comment.png">评论区</div>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/forum/1/2.html"
        )

        assertEquals("chapter", selectChapterContentElement(doc)?.id())
    }

    @Test
    fun selectChapterContentElementScoresTextAndUsableImagesWithinThePreferredTier() {
        for (candidateClass in listOf("forum-content mt-3", "forum-content")) {
            val doc = Jsoup.parse(
                """
                <div class="$candidateClass" id="empty-images">比正文更长的文字<img><img src=" "><img src="javascript:void(0)"></div>
                <div class="$candidateClass" id="small">短文<img src="/art.png"></div>
                <div class="$candidateClass" id="chapter">真正的章节正文<img data-src="/lazy.png"></div>
                """.trimIndent(),
                "https://www.esjzone.cc/forum/1/2.html"
            )
            assertEquals("chapter", selectChapterContentElement(doc)?.id())
        }
    }

    @Test
    fun currentDegradedChapterBodyIsNotRebuiltFromHtml() {
        val saved = ChapterBody.fromBlocks(listOf(ReaderBlock.Text("甲乙"))).copy(
            formatting = ReaderChapterFormatting(
                parserVersion = ReaderChapterFormatting.CURRENT_PARSER_VERSION,
                degraded = true
            )
        )
        val html = "<table><tbody><tr><td>甲</td><td>乙</td></tr></tbody></table>"

        assertSame(saved, saved.rebuildFromHtmlIfOutdated(html, "https://www.esjzone.cc/forum/1/2.html"))
    }

    @Test
    fun outdatedChapterBodyRebuildsFromHtmlWithoutSpecialCaseMigrations() {
        val html = "<table><tbody><tr><td>甲</td><td>乙</td></tr></tbody></table>"
        // Simulate an older parser version that collapsed the whole table into a single paragraph.
        val old = ChapterBody.fromBlocks(listOf(ReaderBlock.Text("甲乙"))).copy(parserVersion = 1)

        val rebuilt = old.rebuildFromHtmlIfOutdated(html, "https://www.esjzone.cc/forum/1/2.html").validate()
        assertEquals(ChapterBody.CURRENT_PARSER_VERSION, rebuilt.parserVersion)
        assertEquals(listOf("甲", "乙"), rebuilt.readerBlocks().map { it.sourceText() })
        assertNotEquals(old.fingerprint, rebuilt.fingerprint)

        // Unrelated HTML must never overwrite an existing stored chapter.
        val rejected = old.rebuildFromHtmlIfOutdated("<p>完全无关的新内容</p>", "https://www.esjzone.cc/forum/1/2.html")
        assertEquals(old, rejected)
    }

    @Test
    fun textContextReanchorsBookmarksAndUnderlinesAcrossParserUpgrades() {
        val key = "/forum/1/101.html"
        val oldBlocks = listOf(
            ReaderBlock.Text("前情提要。主角推开大门。"),
            ReaderBlock.Text("重复台词。主角看见巨龙。重复台词。")
        )
        val oldBody = ChapterBody.fromBlocks(oldBlocks)
        val oldDoc = ReaderChapterDocument(ReaderChapterRef("第101章", key), oldBlocks, contentFingerprint = oldBody.fingerprint)

        // New parser version splits paragraphs differently, changing blockIndex and fingerprint.
        val newBlocks = listOf(
            ReaderBlock.Text("前情提要。"),
            ReaderBlock.Text("主角推开大门。"),
            ReaderBlock.Text("重复台词。"),
            ReaderBlock.Text("主角看见巨龙。"),
            ReaderBlock.Text("重复台词。")
        )
        val newBody = ChapterBody.fromBlocks(newBlocks)
        val newDoc = ReaderChapterDocument(ReaderChapterRef("第101章", key), newBlocks, contentFingerprint = newBody.fingerprint)

        val anchor = ReaderAnchor(key, oldBody.fingerprint, 1, "重复台词。".length).withTextContext(oldDoc)
        val resolved = anchor.resolveIn(key, newDoc)
        assertNotNull(resolved)
        assertEquals(newBody.fingerprint, resolved!!.fingerprint)
        assertEquals(3, resolved.blockIndex)
        assertEquals(0, resolved.offset)
        assertEquals(3, ReaderBookmarkMarker.paragraph(newDoc, key, anchor))

        val oldSig = ReaderUnderlines.sourceSignature(oldBlocks[1])
        val mark = ReaderUnderline(1, oldSig, 5, 12, quote = "主角看见巨龙。")
        val reanchored = ReaderUnderlines.reanchorByQuote(mark, newBlocks)
        assertNotNull(reanchored)
        assertEquals(3, reanchored!!.blockIndex)
        assertEquals(0, reanchored.start)
        assertEquals(7, reanchored.end)

        val jump = readerUnderlinePosition(newDoc, key, mark, { it }, { null })
        assertNotNull(jump.anchor)
        assertEquals(3, jump.anchor!!.blockIndex)
    }

    @Test
    fun upgradeRestoresPositionsWithoutQuotesAndKeepsTheSecondIdenticalUnderline() {
        val key = "/forum/1/101.html"
        val repeated = ReaderBlock.Text("重复。再说：重复。")
        val old = ChapterBody.fromBlocks(listOf(ReaderBlock.Text("前 文"), repeated)).copy(parserVersion = 2)
        val html = "<p>前</p><p>文</p><p><b>重复。</b>再说：重复。</p>"
        val upgraded = old.rebuildFromHtmlIfOutdated(html, "https://www.esjzone.cc$key")
        val gson = Gson()
        val saved = gson.fromJson(gson.toJson(upgraded), ChapterBody::class.java).validate()
        val doc = ReaderChapterDocument(ReaderChapterRef("章节", key), saved.readerBlocks(),
            contentFingerprint = saved.fingerprint, legacyPositions = saved.legacyPositions)

        val second = repeated.value.lastIndexOf("重复。")
        val savedAnchor = ReaderAnchor(key, old.fingerprint, 1, second)
        val restored = requireNotNull(savedAnchor.resolveIn(key, doc))
        assertEquals(2, restored.blockIndex)
        assertEquals(second, restored.offset)
        assertEquals(2, ReaderBookmarkMarker.paragraph(doc, key, savedAnchor))
        val afterWhitespace = requireNotNull(ReaderAnchor(key, old.fingerprint, 0, 2).resolveIn(key, doc))
        assertEquals(1, afterWhitespace.blockIndex)
        assertEquals(0, afterWhitespace.offset)

        val mark = ReaderUnderline(1, ReaderUnderlines.sourceSignature(repeated), second,
            repeated.value.length, quote = "重复。")
        val ambiguous = ReaderUnderlines.migrateToSource(listOf(mark), doc.blocks) { emptyList() }
        assertEquals(listOf(mark), ambiguous)
        val mapped = requireNotNull(readerUnderlineAnchors(doc, ReaderNameReplaceSettings()).resolve(mark))
        assertEquals(2, mapped.blockIndex)
        assertEquals(second, mapped.start)
        assertEquals(repeated.value.length, mapped.end)
        val refreshed = ChapterBody.fromBlocks(doc.blocks).inheritLegacyPositions(saved)
        assertEquals(saved.legacyPositions, refreshed.legacyPositions)
    }

    @Test
    fun displayedNameQuoteCanReanchorAcrossNewBlocksInSourceOffsets() {
        val old = ReaderBlock.Text("前文：阿贝多来了")
        val blocks = listOf(ReaderBlock.Text("前文："), ReaderBlock.Text("阿贝多"), ReaderBlock.Text("来了"))
        val doc = ReaderChapterDocument(ReaderChapterRef("章节", "/forum/1/101.html"), blocks)
        val mark = ReaderUnderline(0, ReaderUnderlines.sourceSignature(old), 3, old.value.length, quote = "Albedo来了")
        val settings = ReaderNameReplaceSettings(enabled = true, rules = listOf(ReaderNameReplaceRule(
            source = "阿贝多", target = "Albedo"
        )))

        assertNull(ReaderUnderlines.reanchorByQuote(mark, blocks))
        val reanchored = requireNotNull(readerUnderlineAnchors(doc, settings).resolve(mark))
        assertEquals(listOf(1, 2), reanchored.ranges().map { it.blockIndex })
        assertEquals(listOf(0 to 3, 0 to 2), reanchored.ranges().map { it.start to it.end })
    }

    @Test
    fun simplifiedQuoteCanReanchorInTraditionalSource() {
        val old = ReaderBlock.Text("前文：漢字")
        val blocks = listOf(ReaderBlock.Text("前文："), ReaderBlock.Text("漢字"))
        val doc = ReaderChapterDocument(ReaderChapterRef("章节", "/forum/1/101.html"), blocks)
        val mark = ReaderUnderline(0, ReaderUnderlines.sourceSignature(old), 3, old.value.length, quote = "汉字")

        val reanchored = requireNotNull(readerUnderlineAnchors(doc, ReaderNameReplaceSettings()).resolve(mark))
        assertEquals(1, reanchored.blockIndex)
        assertEquals(0, reanchored.start)
        assertEquals(2, reanchored.end)
    }

    @Test
    fun offlineHtmlUsesTheDownloadImageKeyEvenWithResponsiveSources() {
        val sourceUrl = "https://www.esjzone.cc/forum/1/101.html"
        val imageUrl = "https://www.esjzone.cc/illustration.jpg"
        val html = """
            <picture><source srcset="https://img.example/retina.jpg">
            <img data-src="https://www.esjzone.one/illustration.jpg" srcset="https://img.example/retina.jpg"></picture>
        """.trimIndent()

        val document = Jsoup.parse(InAppBrowserActivity.buildSafeChapterHtmlDocument(
            "章节", html, false, 18f, sourceUrl, mapOf(imageUrl to "file:///illustration.jpg")
        ))
        assertEquals(imageUrl, document.selectFirst("img")?.attr("src"))
        assertTrue(document.select("picture source, img[srcset]").isEmpty())
    }

    @Test
    fun safeChapterHtmlDocumentStripsScriptsAndPromotesLazyImages() {
        val unsafe = """<p onclick="alert(1)">正文</p><script>evil()</script><a href="javascript:alert(2)">链接</a><img data-src="https://img.example/1.jpg" src="placeholder.gif"><details><summary>作者的话</summary>附言</details>"""
        val doc = InAppBrowserActivity.buildSafeChapterHtmlDocument("第101章", unsafe, darkTheme = true, fontSizeSp = 19f)
        assertFalse(doc.contains("<script", ignoreCase = true))
        assertFalse(doc.contains("onclick", ignoreCase = true))
        assertFalse(doc.contains("javascript:", ignoreCase = true))
        assertTrue(doc.contains("https://img.example/1.jpg"))
        assertTrue(doc.contains("<details open"))
    }

    @Test
    fun randomizedHtmlDomFuzzAlwaysConservesTextImagesAndRuby() {
        val random = Random(20261125L)
        val containerTags = listOf("div", "p", "section", "span", "blockquote", "center", "custom-box", "article")
        val inlineTags = listOf("strong", "b", "em", "i", "u", "del", "span", "a", "font")

        fun buildNode(depth: Int, idPrefix: String): String {
            if (depth <= 0) {
                return when (random.nextInt(5)) {
                    0 -> "文本$idPrefix"
                    1 -> "<br>换行后$idPrefix"
                    2 -> """<img src="https://img.example/$idPrefix.png">"""
                    3 -> "<ruby>汉$idPrefix<rt>读$idPrefix</rt>字<rt>音</rt></ruby>"
                    else -> "<a href=\"https://example.com/$idPrefix\">链$idPrefix</a>"
                }
            }
            val childCount = 1 + random.nextInt(4)
            val body = (0 until childCount).joinToString("") { idx -> buildNode(depth - 1, "${idPrefix}_$idx") }
            return when (random.nextInt(6)) {
                0 -> {
                    val tag = containerTags[random.nextInt(containerTags.size)]
                    "<$tag>$body</$tag>"
                }
                1 -> {
                    val tag = inlineTags[random.nextInt(inlineTags.size)]
                    "<$tag>$body</$tag>"
                }
                2 -> "<details><summary>标题$idPrefix</summary>$body</details>"
                3 -> "<table><tbody><tr><td>$body</td><td>格$idPrefix</td></tr></tbody></table>"
                4 -> "<br><br>$body"
                else -> "<script>ignored_$idPrefix()</script><noscript>ignored_$idPrefix</noscript>$body"
            }
        }

        repeat(200) { seed ->
            val html = buildNode(depth = 3, idPrefix = "s$seed")
            val container = Jsoup.parseBodyFragment(html, "https://www.esjzone.cc/forum/1/$seed.html").body()
            val expected = extractDomContentSnapshot(container)
            val parsed = analyseChapterContent(container)
            val actual = extractBlocksContentSnapshot(parsed.blocks)
            assertTrue(
                "Content conservation failed for seed=$seed\nhtml=$html\nexpected=$expected\nactual=$actual",
                isContentConserved(expected, actual)
            )
        }
    }
}
