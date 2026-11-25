package com.breakyuna.esjzone

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderTextStyle
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.ui.reader.ReaderPageSegment
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.reader.paginateReaderChapter
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPaginationInstrumentedTest {
    @Test
    fun cancelledPaginationStopsInsideASingleLongParagraph() {
        val density = Density(1f)
        val measurer = TextMeasurer(
            createFontFamilyResolver(ApplicationProvider.getApplicationContext()),
            density, LayoutDirection.Ltr)
        val style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp)
        var checks = 0
        val cancelled = CancellationException("Reader layout changed")
        try {
            paginateReaderChapter("标题", listOf(ReaderBlock.Text("正文内容".repeat(1000))),
                ReaderSettings(), style, style, measurer, density, 200, 100, { it },
                checkCancelled = { if (++checks == 12) throw cancelled })
            fail("Cancelled pagination must not return partial pages")
        } catch (error: CancellationException) {
            assertSame(cancelled, error)
            assertEquals(12, checks)
        }
    }

    @Test
    fun renderedFragmentsFitWithoutLosingTextAcrossPageBreaks() {
        val source = (1..60).joinToString("\n") { "第${it}行：边缘文字 ABC italic，检查分页。" }
        val settings = ReaderSettings(paragraphSpacingDp = 7.5f)
        for (density in listOf(Density(1f), Density(2.625f, 1.3f))) {
            val measurer = TextMeasurer(
                createFontFamilyResolver(ApplicationProvider.getApplicationContext()),
                density, LayoutDirection.Ltr
            )
            val style = TextStyle(
                fontSize = settings.fontSizeSp.sp,
                lineHeight = settings.lineHeightSp.sp,
                letterSpacing = settings.letterSpacingSp.sp,
                lineHeightStyle = LineHeightStyle(
                    LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both
                )
            )
            val width = with(density) { 220.dp.roundToPx() }
            val height = with(density) { 145.dp.roundToPx() }
            val pages = paginateReaderChapter(
                "章节标题", listOf(ReaderBlock.Text(source, setOf(ReaderTextStyle.Italic))),
                settings, style, style, measurer, density, width, height, { it }
            )
            assertTrue(pages.size > 1)
            assertEquals(source, pages.flatMap { it.segments }
                .filterIsInstance<ReaderPageSegment.TextLine>().joinToString("") { it.text.text })
            var nextOffset = 0
            pages.flatMap { it.segments }.filterIsInstance<ReaderPageSegment.TextLine>().forEach { segment ->
                assertEquals(0, segment.blockIndex)
                assertEquals(nextOffset, segment.startOffset)
                assertEquals(source.substring(segment.startOffset, segment.startOffset + segment.text.length), segment.text.text)
                assertEquals(ReaderUnderlines.signature(
                    ReaderBlock.Text(source, setOf(ReaderTextStyle.Italic)), source), segment.signature)
                nextOffset += segment.text.length
            }
            for (page in pages) {
                // Measure the final fragments as separate Text nodes, including their
                // own first/last-line metrics and any trailing hard newline.
                val renderedHeight = page.segments.sumOf { segment ->
                    when (segment) {
                        is ReaderPageSegment.Heading -> measurer.measure(
                            segment.name, style, constraints = Constraints(maxWidth = width)
                        ).size.height + with(density) {
                            (settings.paragraphSpacingDp + 8f).dp.roundToPx()
                        }
                        is ReaderPageSegment.TextLine -> measurer.measure(
                            segment.text, style, constraints = Constraints(maxWidth = width)
                        ).size.height + with(density) { segment.bottomSpacing.roundToPx() }
                        is ReaderPageSegment.Gap -> with(density) { segment.height.roundToPx() }
                        is ReaderPageSegment.Image -> error("No images in this fixture")
                        is ReaderPageSegment.Divider -> error("No dividers in this fixture")
                    }
                }
                assertTrue("Rendered page height $renderedHeight exceeds $height", renderedHeight <= height)
            }
        }
    }

    @Test
    fun softContinuationKeepsFirstLineIndentAfterTheNextHardBreak() {
        val density = Density(1f)
        val measurer = TextMeasurer(
            createFontFamilyResolver(ApplicationProvider.getApplicationContext()),
            density, LayoutDirection.Ltr)
        val indent = TextIndent(firstLine = 36.sp)
        val style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, textIndent = indent)
        val first = "甲".repeat(24)
        val source = "$first\n下一行\n另一行"
        val settings = ReaderSettings(firstLineIndentChars = 2, paragraphSpacingDp = 0f)
        val width = 180
        val pages = paginateReaderChapter(
            "标题", listOf(ReaderBlock.Text(source)), settings, style, style.copy(textIndent = TextIndent.None),
            measurer, density, width, 90, { it }
        )
        val fragments = pages.flatMap { it.segments }.filterIsInstance<ReaderPageSegment.TextLine>()
        assertEquals(source, fragments.joinToString("") { it.text.text })
        val continuation = fragments.first { it.startOffset in 1 until first.length }
        val next = fragments.first { it.startOffset == first.length + 1 }
        assertEquals(TextIndent.None, continuation.paragraphStyle?.textIndent)
        assertEquals(first.length + 1, continuation.startOffset + continuation.text.length)
        assertEquals(indent, next.paragraphStyle?.textIndent)
        assertTrue(pages.any { continuation in it.segments && next in it.segments })
        val continuedLayout = measurer.measure(continuation.text, requireNotNull(continuation.paragraphStyle),
            maxLines = continuation.maxLines, constraints = Constraints(maxWidth = width))
        assertTrue(continuedLayout.getLineStart(continuedLayout.lineCount - 1) < continuation.text.length)
    }

    @Test
    fun aSingleOversizedLineIsKeptOnItsOwnPage() {
        val density = Density(1f)
        val measurer = TextMeasurer(
            createFontFamilyResolver(ApplicationProvider.getApplicationContext()),
            density, LayoutDirection.Ltr
        )
        val pages = paginateReaderChapter(
            "标题", listOf(ReaderBlock.Text("大", setOf(ReaderTextStyle.FontSizePx(96)))),
            ReaderSettings(), TextStyle(fontSize = 18.sp), TextStyle(fontSize = 18.sp),
            measurer, density, 200, 50, { it }
        )
        val textPage = pages.single { page -> page.segments.any { it is ReaderPageSegment.TextLine } }
        assertEquals("大",
            (textPage.segments.single() as ReaderPageSegment.TextLine).text.text)
        assertTrue(textPage.isOversized)
    }
}
