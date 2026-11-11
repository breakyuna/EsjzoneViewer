package com.breakyuna.esjzone

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderBookmarkPosition
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.ui.reader.ReaderBookmarkMarker
import com.breakyuna.esjzone.ui.reader.ReaderPage
import com.breakyuna.esjzone.ui.reader.ReaderPageSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBookmarkMarkerTest {
    private val fingerprint = "b".repeat(64)
    private val document = ReaderChapterDocument(ReaderChapterRef("第一章", "/forum/1/2.html"), listOf(
        ReaderBlock.Text("第一段"),
        ReaderBlock.Text("　"),
        ReaderBlock.Image("cover.jpg"),
        ReaderBlock.Paragraph(listOf(ReaderBlock.Text("第二"), ReaderBlock.Text("段很长"))),
        ReaderBlock.LineBreak
    ), contentFingerprint = fingerprint)

    private fun anchor(block: Int, offset: Int = 0, kind: String = "text", key: String = "saved-key") =
        ReaderAnchor(key, fingerprint, block, offset, kind)

    @Test fun bookmarkedParagraphIsTheAnchoredTextBlockEvenMidParagraph() {
        assertEquals(0, ReaderBookmarkMarker.paragraph(document, "key", anchor(0)))
        assertEquals(3, ReaderBookmarkMarker.paragraph(document, "key", anchor(3, offset = 4)))
    }

    @Test fun blankImageAndHeadingAnchorsMarkTheParagraphTheirExcerptStartsWith() {
        val excerpt = { a: ReaderAnchor -> ReaderBookmarkPosition.excerpt(document, a, { null }) }
        assertEquals(3, ReaderBookmarkMarker.paragraph(document, "key", anchor(1)))
        assertEquals(3, ReaderBookmarkMarker.paragraph(document, "key", anchor(2, kind = "image")))
        assertEquals("第二段很长", excerpt(anchor(2, kind = "image")))
        assertEquals(0, ReaderBookmarkMarker.paragraph(document, "key", anchor(-1, kind = "heading")))
        assertEquals("第一段", excerpt(anchor(-1, kind = "heading")))
        assertNull(ReaderBookmarkMarker.paragraph(document, "key", anchor(4, kind = "image")))
    }

    @Test fun chapterOnlyEndAndStaleBookmarksShowNoMarker() {
        assertNull(ReaderBookmarkMarker.paragraph(document, "key", null))
        assertNull(ReaderBookmarkMarker.paragraph(document, "key", anchor(0, kind = "end")))
        assertNull(ReaderBookmarkMarker.paragraph(document, "key", anchor(0).copy(fingerprint = "c".repeat(64))))
        assertNull(ReaderBookmarkMarker.paragraph(document, "key", anchor(9)))
        assertNull(ReaderBookmarkMarker.paragraph(document, "key", anchor(2)))
    }

    @Test fun paragraphSplitAcrossPagesShowsTheMarkerOnlyWithItsFirstLine() {
        fun line(block: Int, start: Int) = ReaderPageSegment.TextLine(AnnotatedString("行"), emptyMap(), 0.dp, block, "", start)
        val pages = listOf(
            ReaderPage(listOf(ReaderPageSegment.Heading("第一章"), line(0, 0), line(3, 0)), -1f),
            ReaderPage(listOf(line(3, 18)), 3.5f),
            ReaderPage(listOf(line(3, 40), ReaderPageSegment.Gap(4.dp)), 3.8f)
        )
        fun marked(blocks: Set<Int>) = pages.mapIndexedNotNull { index, page ->
            index.takeIf { page.segments.any { it is ReaderPageSegment.TextLine &&
                ReaderBookmarkMarker.showsOn(it.blockIndex, it.startOffset, blocks) } }
        }
        assertEquals(listOf(0), marked(setOf(3)))
        assertEquals(listOf(0), marked(setOf(0, 3)))
        assertTrue(ReaderBookmarkMarker.showsOn(0, 0, setOf(0, 3)))
        assertTrue(ReaderBookmarkMarker.showsOn(3, 0, setOf(0, 3)))
        assertEquals(emptyList<Int>(), marked(emptySet()))
        assertEquals(emptyList<Int>(), marked(setOf(5)))
    }

    @Test fun ribbonStaysInTheMarginAndWithinTheFirstLineForAnyTypography() {
        val gap = 6f
        for (margin in listOf(24f * 1.5f, 20f * 3f, 48f * 3.5f)) for (font in listOf(30f, 54f, 96f)) for (spacing in listOf(0f, 20f, 60f)) {
            val lineTop = 0f
            val lineBottom = font * 1.2f + spacing
            // Line spacing is distributed around the glyphs; the baseline moves with it.
            val baseline = spacing / 2 + font * 0.95f
            val rect = requireNotNull(ReaderBookmarkMarker.bounds(1000f, lineTop, lineBottom, baseline, font, margin, gap))
            assertTrue(rect.left >= 1000f + gap - 0.01f)
            assertTrue(rect.right <= 1000f + margin - gap + 0.01f)
            assertTrue(rect.top >= lineTop - 0.01f && rect.bottom <= lineBottom + 0.01f)
            assertTrue(rect.width > 0f && rect.height > 0f)
            // Centred on the glyph body, never pushed to the line-spacing area.
            assertTrue(rect.center.y in (baseline - font)..baseline)
        }
    }

    @Test fun ribbonShrinksForNarrowMarginsAndMovesLeftForRightToLeftText() {
        val narrow = requireNotNull(ReaderBookmarkMarker.bounds(800f, 0f, 60f, 45f, 48f, 18f, 4f))
        assertEquals(10f, narrow.width, 0.01f)
        assertNull(ReaderBookmarkMarker.bounds(800f, 0f, 60f, 45f, 48f, 8f, 4f))
        assertNull(ReaderBookmarkMarker.bounds(800f, 0f, 0f, 0f, 48f, 60f, 4f))
        val rtl = requireNotNull(ReaderBookmarkMarker.bounds(800f, 0f, 60f, 45f, 48f, 60f, 4f, rightToLeft = true))
        assertTrue(rtl.right <= -4f + 0.01f && rtl.left >= -60f + 4f - 0.01f)
    }
}
