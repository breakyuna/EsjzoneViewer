package com.breakyuna.esjzone

import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.page.ReaderChapter
import com.breakyuna.esjzone.ui.page.ReaderDisplayItem
import com.breakyuna.esjzone.ui.page.ReaderPaginationSnapshot
import com.breakyuna.esjzone.ui.page.chapterIdentity
import com.breakyuna.esjzone.ui.reader.ReaderPage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaginationSnapshotTest {
    private val layout = listOf<Any?>(390, 844, "original")
    private fun chapter(number: Int, text: String = "Body $number"): ReaderChapter {
        val chapter = Chapter("Chapter $number", "/forum/9001/$number.html", false)
        return ReaderChapter(chapter, ReaderChapterDocument(
            ReaderChapterRef(chapter.name, chapter.url), listOf(ReaderBlock.Text(text))))
    }
    private fun snapshot(entry: ReaderChapter): ReaderPaginationSnapshot {
        val page = ReaderPage(emptyList(), -1f)
        val key = chapterIdentity(entry.chapter)
        return ReaderPaginationSnapshot(layout + listOf(listOf(entry)), layout,
            mapOf(entry to listOf(page)),
            listOf(ReaderDisplayItem("$key|page:0", key, entry, 0, 1, emptyList(), page)))
    }

    @Test fun visibleChapterRemainsReadableWhileEitherNeighborIsBeingPrepared() {
        val current = chapter(2)
        val prepared = snapshot(current)
        assertTrue(prepared.hasReadablePage(layout, current, listOf(current, chapter(3))))
        assertTrue(prepared.hasReadablePage(layout, current, listOf(chapter(1), current)))
        assertFalse(prepared.hasReadablePage(layout, chapter(3), listOf(current, chapter(3))))
    }

    @Test fun layoutChangesAndTrimmedChaptersCannotReuseOldPages() {
        val current = chapter(2)
        val prepared = snapshot(current)
        assertFalse(prepared.hasReadablePage(listOf(844, 390, "original"), current, listOf(current)))
        assertFalse(prepared.hasReadablePage(layout, current, listOf(chapter(3))))
    }

    @Test fun aNewBodyAtTheSameUrlWaitsForItsOwnPagination() {
        val original = chapter(2)
        val replacement = chapter(2, "Updated body")
        assertFalse(snapshot(original).hasReadablePage(layout, original, listOf(replacement)))
        assertFalse(snapshot(original).hasReadablePage(layout, replacement, listOf(replacement)))
    }

    @Test fun unlockingDoesNotTreatTheOldPasswordPageAsPreparedBody() {
        val body = chapter(2)
        val locked = ReaderChapter.passwordRequired(body.chapter)
        assertTrue(snapshot(locked).hasReadablePage(layout, locked, listOf(locked)))
        assertFalse(snapshot(locked).hasReadablePage(layout, locked, listOf(body)))
        assertFalse(snapshot(locked).hasReadablePage(layout, body, listOf(body)))
    }
}
