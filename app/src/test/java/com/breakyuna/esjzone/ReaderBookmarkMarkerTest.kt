package com.breakyuna.esjzone

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderBookmarkPosition
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.page.ReaderChapter
import com.breakyuna.esjzone.ui.page.pageBookmarkTarget
import com.breakyuna.esjzone.ui.page.scrollBookmarkTarget
import com.breakyuna.esjzone.ui.page.scrollChapterItems
import com.breakyuna.esjzone.ui.reader.ReaderBookmarkMarker
import com.breakyuna.esjzone.ui.reader.ReaderPage
import com.breakyuna.esjzone.ui.reader.ReaderPageSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun scrollBookmarksUseVisibleContinuationWhenNoParagraphStartsOnScreen() {
        val entry = ReaderChapter(Chapter(document.chapter.name, document.chapter.url, false), document)
        val items = scrollChapterItems(listOf(entry))
        val itemsByKey = items.associateBy { it.key }
        val continuation = VisibleItem(1, items[1].key, offset = -120, size = 200)
        val blank = VisibleItem(2, items[2].key, offset = 80, size = 20)
        val image = VisibleItem(3, items[3].key, offset = 100, size = 80)
        val nextParagraph = VisibleItem(4, items[4].key, offset = 180, size = 60)
        val visible = listOf(continuation, blank, image, nextParagraph)

        val target = requireNotNull(scrollBookmarkTarget(visible, 0, 180, itemsByKey))
        assertEquals(0, target.blockIndex)
        assertFalse(target.startsInViewport)
        assertEquals(3, scrollBookmarkTarget(visible, 0, 200, itemsByKey)?.blockIndex)
        assertNull(scrollBookmarkTarget(visible, 80, 180, itemsByKey))
    }

    @Test fun pagedBookmarksRetainContinuationOffsetAndPreferNewParagraphStarts() {
        val entry = ReaderChapter(Chapter(document.chapter.name, document.chapter.url, false), document)
        val item = scrollChapterItems(listOf(entry)).first()
        val image = ReaderPageSegment.Image("cover.jpg")
        val continuation = ReaderPageSegment.TextLine(AnnotatedString("一段"), emptyMap(), 0.dp, startOffset = 1)
        val nextParagraph = ReaderPageSegment.TextLine(AnnotatedString("第二段很长"), emptyMap(), 0.dp, blockIndex = 3)

        val target = requireNotNull(pageBookmarkTarget(item.copy(page = ReaderPage(listOf(image, continuation), 0.3f))))
        assertEquals(0, target.blockIndex)
        assertEquals(1, target.displayOffset)
        assertFalse(target.startsInViewport)
        assertEquals(3, pageBookmarkTarget(item.copy(page = ReaderPage(listOf(continuation, nextParagraph), 0.3f)))?.blockIndex)
        assertNull(pageBookmarkTarget(item.copy(page = ReaderPage(listOf(image), 2f))))
    }

    private data class VisibleItem(
        override val index: Int,
        override val key: Any,
        override val offset: Int,
        override val size: Int,
        override val contentType: Any? = null
    ) : LazyListItemInfo

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
