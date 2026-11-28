package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.readerChapterProgress
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.offline.BookTextProgressIndex
import com.breakyuna.esjzone.ui.page.LocalReadingPosition
import com.breakyuna.esjzone.ui.page.bookmarkChapterIdentity
import com.breakyuna.esjzone.ui.page.chapterIdentity
import com.breakyuna.esjzone.ui.page.readerBookLocationFor
import com.breakyuna.esjzone.ui.page.toLocalReadingActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM coverage for reader persistence identities (Room itself stays in androidTest). */
class ReaderPersistenceContractTest {

    @Test
    fun underlineTargetProgressUsesItsAnchorAndFullCatalogAcrossMirrors() {
        val order = (1..3).map { Chapter("Chapter $it", "https://www.esjzone.cc/forum/9001/$it.html", false) }
        val target = Chapter("Chapter 2", "https://www.esjzone.one/forum/9001/2.html", false)
        val indices = order.mapIndexed { index, chapter -> chapterIdentity(chapter) to index }.toMap()
        val document = ReaderChapterDocument(ReaderChapterRef(target.name, target.url),
            listOf(ReaderBlock.Text("abcd"), ReaderBlock.Text("abcdefgh")))
        val anchor = ReaderAnchor(chapterIdentity(target), document.contentFingerprint, 1, 2)
        val chapterProgress = readerChapterProgress(document, anchor)

        val fallback = requireNotNull(readerBookLocationFor(target, chapterProgress, order, indices))
        assertEquals(order[1], fallback.chapter)
        assertEquals(1, fallback.chapterIndex)
        assertEquals(3, fallback.totalChapters)
        assertEquals(0.5f, fallback.chapterProgress, 0.0001f)
        assertEquals(0.5f, fallback.bookProgress, 0.0001f)

        val textIndex = BookTextProgressIndex(indices, longArrayOf(0L, 100L, 700L, 1000L))
        val weighted = requireNotNull(readerBookLocationFor(target, chapterProgress, order, indices, textIndex))
        assertEquals(0.4f, weighted.bookProgress, 0.0001f)
    }

    @Test
    fun readingHistoryPersistsTextContextFromTheMeasuredDocument() {
        val chapter = ReaderChapterRef("Chapter 1", "/forum/9001/1.html")
        val document = ReaderChapterDocument(
            chapter,
            listOf(ReaderBlock.Text("甲 乙"), ReaderBlock.Image("https://img.example/page.png"), ReaderBlock.Text(" 丙 丁")),
            contentFingerprint = "a".repeat(64)
        )
        val measured = ReaderAnchor(chapter.url, document.contentFingerprint, 0, offset = 1)
        val position = LocalReadingPosition(
            novelId = "9001",
            novelName = "Novel",
            novelUrl = "/detail/9001.html",
            novelCoverUrl = "",
            chapterUrl = chapter.url,
            chapterName = chapter.name,
            chapterIndex = 0,
            totalChapters = 1,
            chapterProgress = 0.25f,
            anchor = measured,
            anchorDocument = document
        )

        val saved = position.toLocalReadingActivity("activity", startedAt = 100L, now = 200L)
        val persisted = requireNotNull(ReaderAnchor.decode(saved.anchor))
        assertNull(measured.textQuote)
        assertEquals(measured.offset, persisted.offset)
        assertEquals("甲", persisted.textPrefix)
        assertEquals("乙丙丁", persisted.textQuote)
        assertEquals(0.25f, requireNotNull(persisted.normalizedOffset), 0.0001f)

        val changed = document.copy(contentFingerprint = "b".repeat(64))
        val stale = position.copy(anchorDocument = changed)
            .toLocalReadingActivity("activity", startedAt = 100L, now = 200L)
        assertEquals(measured, ReaderAnchor.decode(stale.anchor))
    }

    @Test
    fun bookmarkKeepsItsChapterFragmentAndEachRowHasItsOwnIdentity() {
        val chapter = Chapter("Part 2", "https://www.esjzone.cc/forum/9001/1.html#part-2", false)
        val oldRow = Bookmark("/forum/9001/1.html", "9001", "Novel", chapter.name,
            anchor = ReaderAnchor.encode(ReaderAnchor(chapterIdentity(chapter), "a".repeat(64), 2)))
        val newRow = Bookmark(chapterIdentity(chapter), "9001", "Novel", chapter.name,
            anchor = ReaderAnchor.encode(ReaderAnchor(chapterIdentity(chapter), "a".repeat(64), 5)))
        assertEquals(chapterIdentity(chapter), bookmarkChapterIdentity(oldRow))
        assertEquals(chapterIdentity(chapter), bookmarkChapterIdentity(newRow))
        assertTrue(oldRow.id != newRow.id)
    }

    @Test
    fun readerAnchorDecodeRejectsMalformedPayloadsAndAcceptsValidKinds() {
        val validFp = "a".repeat(64)
        assertNull(ReaderAnchor.decode(null))
        assertNull(ReaderAnchor.decode("   "))
        assertNull(ReaderAnchor.decode("not-json"))
        assertNull(ReaderAnchor.decode("""{"version":1,"chapterKey":"","fingerprint":"$validFp","blockIndex":0,"offset":0,"kind":"text","fraction":0.0}"""))
        assertNull(ReaderAnchor.decode("""{"version":1,"chapterKey":"/forum/1/2.html","fingerprint":"short","blockIndex":0,"offset":0,"kind":"text","fraction":0.0}"""))
        assertNull(ReaderAnchor.decode("""{"version":1,"chapterKey":"/forum/1/2.html","fingerprint":"$validFp","blockIndex":0,"offset":0,"kind":"heading","fraction":0.0}"""))
        assertNull(ReaderAnchor.decode("""{"version":1,"chapterKey":"/forum/1/2.html","fingerprint":"$validFp","blockIndex":-1,"offset":0,"kind":"text","fraction":0.0}"""))
        assertNull(ReaderAnchor.decode("""{"version":1,"chapterKey":"/forum/1/2.html","fingerprint":"$validFp","blockIndex":0,"offset":0,"kind":"unknown","fraction":0.0}"""))
        val heading = ReaderAnchor("/forum/1/2.html", validFp, -1, 0, kind = "heading")
        assertEquals(heading, ReaderAnchor.decode(ReaderAnchor.encode(heading)))
        val end = ReaderAnchor("/forum/1/2.html", validFp, -1, 0, kind = "end")
        assertEquals(end, ReaderAnchor.decode(ReaderAnchor.encode(end)))
    }
}
