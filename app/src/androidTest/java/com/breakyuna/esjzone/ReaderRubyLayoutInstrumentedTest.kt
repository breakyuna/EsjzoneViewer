package com.breakyuna.esjzone

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterFormatting
import com.breakyuna.esjzone.domain.reader.ReaderRuby
import com.breakyuna.esjzone.ui.reader.ReaderPageSegment
import com.breakyuna.esjzone.ui.reader.ReaderBlocks
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.reader.paginateReaderChapter
import com.breakyuna.esjzone.ui.reader.readerRubyParagraphStyle
import com.breakyuna.esjzone.ui.reader.toAnnotatedReaderText
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ReaderRubyLayoutInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val density = Density(1f)
    private val measurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()),
        density, LayoutDirection.Ltr)
    private val bodyStyle = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = 0.sp)

    @Test fun rubyOnLaterLineGetsSpaceAboveAndKeepsTheTextBaseline() {
        val parts = listOf(
            ReaderBlock.Text("这是一段足够长的正文，用来让注释出现在第二行以后。"),
            ReaderBlock.Text("游戏评测", ruby = ReaderRuby("游戏评测", "粪作排雷")),
            ReaderBlock.Text("洋洋洒洒。")
        )
        val block = ReaderBlock.Paragraph(parts)
        val inline = linkedMapOf<String, InlineTextContent>()
        val text = buildAnnotatedString {
            parts.forEachIndexed { index, part ->
                val (value, content) = part.toAnnotatedReaderText(bodyStyle, measurer, density, { it }, "ruby-$index")
                append(value)
                inline.putAll(content)
            }
        }
        val style = readerRubyParagraphStyle(block, bodyStyle, inline, measurer, density) { it }
        val placeholders = text.getStringAnnotations(0, text.length).mapNotNull { range ->
            inline[range.item]?.let { AnnotatedString.Range<Placeholder>(it.placeholder, range.start, range.end) }
        }
        val layout = measurer.measure(text, style, placeholders = placeholders, constraints = Constraints(maxWidth = 140))
        val offset = text.text.indexOf("游戏评测")
        val line = layout.getLineForOffset(offset)
        val rect = requireNotNull(layout.placeholderRects.single())
        assertTrue(line > 0)
        assertTrue("Ruby must stay within its own line", rect.top >= layout.getLineTop(line) - 1f)
        assertTrue("Ruby base must share the surrounding baseline", abs(rect.bottom - layout.getLineBaseline(line)) <= 1f)
    }

    @Test fun changingLetterSpacingRemeasuresRubyWidth() {
        val ruby = ReaderBlock.Text("游戏评测", ruby = ReaderRuby("游戏评测", "粪作排雷"))
        val normal = ruby.toAnnotatedReaderText(bodyStyle, measurer, density, { it }).second.values.single()
        val spaced = ruby.toAnnotatedReaderText(bodyStyle.copy(letterSpacing = 4.sp), measurer, density, { it })
            .second.values.single()
        assertTrue(spaced.placeholder.width.value > normal.placeholder.width.value)
    }

    @Test fun renderedRubyUsesTheBodyColorAndBaseline() {
        val prefix = "这是一段足够长的正文，用来让注释出现在第二行以后。"
        val block = ReaderBlock.Paragraph(listOf(ReaderBlock.Text(prefix),
            ReaderBlock.Text("游戏评测", ruby = ReaderRuby("游戏评测", "粪作排雷")), ReaderBlock.Text("洋洋洒洒。")))
        val fullText = prefix + "游戏评测洋洋洒洒。"
        val color = Color(0xFF433422)
        var paragraphLayout: TextLayoutResult? = null
        composeRule.setContent {
            Box(Modifier.width(140.dp)) {
                ReaderBlocks(listOf(block), ReaderSettings(fontSizeSp = 20f, lineSpacingSp = 5f),
                    rememberTextMeasurer(), LocalDensity.current, color,
                    onTextLayout = { _, result -> paragraphLayout = result })
            }
        }
        val parent = composeRule.onNodeWithText(fullText, useUnmergedTree = true).fetchSemanticsNode()
        val rubyBase = composeRule.onNodeWithText("游戏评测", useUnmergedTree = true)
        val rubyReading = composeRule.onNodeWithText("粪作排雷", useUnmergedTree = true)
        val baseLayouts = mutableListOf<TextLayoutResult>()
        val readingLayouts = mutableListOf<TextLayoutResult>()
        rubyBase.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(baseLayouts) }
        rubyReading.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(readingLayouts) }
        val outer = requireNotNull(paragraphLayout)
        val line = outer.getLineForOffset(prefix.length)
        val expected = parent.boundsInRoot.top + outer.getLineBaseline(line)
        val actual = rubyBase.fetchSemanticsNode().boundsInRoot.top + baseLayouts.single().firstBaseline
        assertTrue(abs(expected - actual) <= 2f)
        assertEquals(color, baseLayouts.single().layoutInput.style.color)
        assertEquals(color, readingLayouts.single().layoutInput.style.color)
        assertTrue(rubyReading.fetchSemanticsNode().boundsInRoot.bottom <= rubyBase.fetchSemanticsNode().boundsInRoot.top + 1f)
    }

    @Test fun paginationCarriesCenteredParagraphAndDivider() {
        val block = ReaderBlock.Paragraph(listOf(ReaderBlock.Text("章节正文标题")))
        val settings = ReaderSettings(firstLineIndentChars = 2)
        val style = bodyStyle.copy(textIndent = TextIndent(firstLine = 40.sp))
        val pages = paginateReaderChapter("章节", listOf(block), settings, style, style,
            measurer, density, 300, 600, { it }, ReaderChapterFormatting(mapOf(0 to "center"), listOf(1)))
        val segments = pages.flatMap { it.segments }
        val text = segments.filterIsInstance<ReaderPageSegment.TextLine>().single()
        assertEquals("center", text.alignment)
        assertEquals(TextAlign.Center, text.paragraphStyle!!.textAlign)
        assertEquals(TextIndent.None, text.paragraphStyle!!.textIndent)
        assertEquals(1, segments.filterIsInstance<ReaderPageSegment.Divider>().size)
    }

    @Test fun scrollingCentersTheSourceParagraphWithinTheAvailableWidth() {
        val block = ReaderBlock.Paragraph(listOf(ReaderBlock.Text("章节正文标题")))
        var result: TextLayoutResult? = null
        composeRule.setContent {
            Box(Modifier.width(240.dp)) {
                ReaderBlocks(listOf(block), ReaderSettings(firstLineIndentChars = 2),
                    rememberTextMeasurer(), LocalDensity.current, Color.Black,
                    onTextLayout = { _, layout -> result = layout },
                    formatting = ReaderChapterFormatting(mapOf(0 to "center")))
            }
        }
        composeRule.runOnIdle {
            val layout = requireNotNull(result)
            assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
            assertEquals(TextIndent.None, layout.layoutInput.style.textIndent)
            assertTrue(layout.getLineLeft(0) > 0f)
            assertTrue(layout.getLineRight(0) < layout.size.width)
        }
    }
}
