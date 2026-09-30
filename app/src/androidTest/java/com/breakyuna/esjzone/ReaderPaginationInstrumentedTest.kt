package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderTextStyle
import com.breakyuna.esjzone.ui.reader.ReaderPageContent
import com.breakyuna.esjzone.ui.reader.ReaderPageSegment
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.reader.paginateReaderChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPaginationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

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
                    }
                }
                assertTrue("Rendered page height $renderedHeight exceeds $height", renderedHeight <= height)
            }
        }
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

    @Test
    fun oversizedHeadingCanScrollToItsHiddenContent() {
        composeRule.setContent {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val settings = ReaderSettings()
            val style = TextStyle(fontSize = 24.sp, lineHeight = 24.sp)
            val page = remember(density, measurer) {
                paginateReaderChapter(
                    "长章节标题".repeat(40), emptyList(), settings, style, style,
                    measurer, density, with(density) { 200.dp.roundToPx() },
                    with(density) { 50.dp.roundToPx() }, { it }
                ).single()
            }
            Box(Modifier.size(width = 200.dp, height = 50.dp)) {
                ReaderPageContent(page, settings, style, Color.Black, { it }, 50.dp)
            }
        }
        val scrollable = composeRule.onAllNodes(hasScrollAction()).onFirst()
        val range = scrollable.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Oversized heading must have hidden scrollable content", range.maxValue() > 0f)
        scrollable.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
            scrollBy(0f, range.maxValue())
        }
        composeRule.waitForIdle()
        val scrolledRange = scrollable.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Hidden heading content must be reachable", scrolledRange.value() > 0f)
    }
}
