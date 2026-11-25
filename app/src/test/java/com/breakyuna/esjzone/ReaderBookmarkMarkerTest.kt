package com.breakyuna.esjzone

import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderBookmarkPosition
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.ui.reader.ReaderBookmarkMarker
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

    @Test fun ribbonBoundsRespectTypographyNarrowMarginsAndRtl() {
        val gap = 6f
        for ((margin, font, spacing) in listOf(Triple(36f, 30f, 0f), Triple(60f, 54f, 20f), Triple(168f, 96f, 60f))) {
            val lineBottom = font * 1.2f + spacing
            val baseline = spacing / 2 + font * 0.95f
            val rect = requireNotNull(ReaderBookmarkMarker.bounds(1000f, 0f, lineBottom, baseline, font, margin, gap))
            assertTrue(rect.left >= 1000f + gap - 0.01f && rect.right <= 1000f + margin - gap + 0.01f)
            assertTrue(rect.top >= -0.01f && rect.bottom <= lineBottom + 0.01f)
            assertTrue(rect.center.y in (baseline - font)..baseline)
        }
        val narrow = requireNotNull(ReaderBookmarkMarker.bounds(800f, 0f, 60f, 45f, 48f, 18f, 4f))
        assertEquals(10f, narrow.width, 0.01f)
        assertNull(ReaderBookmarkMarker.bounds(800f, 0f, 60f, 45f, 48f, 8f, 4f))
        assertNull(ReaderBookmarkMarker.bounds(800f, 0f, 0f, 0f, 48f, 60f, 4f))
        val rtl = requireNotNull(ReaderBookmarkMarker.bounds(800f, 0f, 60f, 45f, 48f, 60f, 4f, rightToLeft = true))
        assertTrue(rtl.right <= -4f + 0.01f && rtl.left >= -60f + 4f - 0.01f)
    }
}
