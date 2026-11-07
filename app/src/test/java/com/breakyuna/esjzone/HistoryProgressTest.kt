package com.breakyuna.esjzone

import androidx.navigation3.runtime.NavKey
import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.AppNavigator
import com.breakyuna.esjzone.ui.page.fullBookProgress
import com.breakyuna.esjzone.ui.page.cloudHistoryChapterIndex
import com.breakyuna.esjzone.ui.page.cloudHistoryBookProgress
import com.breakyuna.esjzone.ui.page.cloudHistoryDestination
import com.breakyuna.esjzone.ui.page.ChapterPage
import com.breakyuna.esjzone.ui.page.NovelPage
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure boundary coverage for the local history book-progress projection. */
class HistoryProgressTest {
    @Test
    fun cloudHistoryOpensTheRecordedChapterDirectly() {
        val chapter = Chapter("Cloud chapter", "/forum/9001/9201.html", true)
        val history = HistoryNovel("Book", "/detail/9001.html", "different-view-id", chapter)

        val destination = cloudHistoryDestination(history)
        assertTrue(destination is ChapterPage)
        assertEquals("ChapterPage:9001:/forum/9001/9201.html", destination.key)

        val withCatalog = cloudHistoryDestination(history, "cover", listOf(chapter))
        assertTrue(withCatalog is ChapterPage)
        assertEquals(destination.key, withCatalog.key)
    }

    @Test
    fun cloudChapterRemainsAuthoritativeWhenRecreatedBeforeLoading() {
        val chapter = Chapter("Cloud chapter", "/forum/9001/9201.html", true)
        val history = HistoryNovel("Book", "/detail/9001.html", "view", chapter)
        val stack = mutableListOf<NavKey>(AppNavKey.Main)
        AppNavigator(stack, mutableMapOf()).push(cloudHistoryDestination(history))
        val serializer = AppNavKey.Reader.serializer()
        val saved = Json.encodeToString(serializer, stack.last() as AppNavKey.Reader)
        val restoredEntry = Json.decodeFromString(serializer, saved)
        val navigator = AppNavigator(mutableListOf(AppNavKey.Main, restoredEntry), mutableMapOf())
        val restored = navigator.readerDestination(restoredEntry.route) as ChapterPage
        assertEquals("ChapterPage:9001:/forum/9001/9201.html", restored.key)
        assertFalse(restored.restoreFromLocalHistory)
    }

    @Test
    fun cloudHistoryWithoutAChapterCanStillOpenTheBook() {
        val history = HistoryNovel("Book", "/detail/9001.html", "view", Chapter("", "", true))
        assertTrue(cloudHistoryDestination(history) is NovelPage)
    }

    @Test
    fun cloudHistoryDoesNotReportCompletionWhilePositionIsUnknown() {
        assertNull(cloudHistoryBookProgress(null, 10))
        assertNull(cloudHistoryBookProgress(-1, 10))
        assertNull(cloudHistoryBookProgress(0, 0))
        assertEquals(0.1f, cloudHistoryBookProgress(0, 10))
        assertEquals(1f, cloudHistoryBookProgress(9, 10))
    }

    @Test
    fun mapsChapterFractionIntoWholeBookFraction() {
        assertEquals(0.05f, fullBookProgress(chapterIndex = 0, totalChapters = 10, chapterProgress = 0.5f))
        assertEquals(0.95f, fullBookProgress(chapterIndex = 9, totalChapters = 10, chapterProgress = 0.5f))
        assertEquals(1f, fullBookProgress(chapterIndex = 9, totalChapters = 10, chapterProgress = 1f))
    }

    @Test
    fun clampsInvalidFractionsAndFallsBackWhenBookPositionUnknown() {
        assertEquals(0.2f, fullBookProgress(chapterIndex = 2, totalChapters = 10, chapterProgress = Float.NaN))
        assertEquals(0.3f, fullBookProgress(chapterIndex = 2, totalChapters = 10, chapterProgress = 4f))
        assertEquals(0.4f, fullBookProgress(chapterIndex = -1, totalChapters = 0, chapterProgress = 0.4f))
    }

    @Test
    fun cloudHistoryPositionMatchesMirrorLinksAndKeepsCatalogOrder() {
        val chapters = listOf(
            Chapter("第一章", "/forum/1/3.html", false),
            Chapter("第二章", "https://www.esjzone.cc/forum/1/2.html", false),
            Chapter("第三章", "/forum/1/1.html", false)
        )
        assertEquals(1, cloudHistoryChapterIndex(chapters, "https://www.esjzone.one/forum/1/2.html#comment"))
        assertEquals(2, cloudHistoryChapterIndex(chapters, "/forum/1/1.html"))
        assertEquals(-1, cloudHistoryChapterIndex(chapters, "/forum/1/999.html"))
    }

    @Test
    fun cloudHistoryPositionDistinguishesExternalChapterHostsAndQueries() {
        val chapters = listOf(
            Chapter("第一章", "https://example.test/read?id=1", false),
            Chapter("第二章", "https://example.test/read?id=2", false)
        )
        assertEquals(1, cloudHistoryChapterIndex(chapters, "https://example.test/read?id=2#comments"))
        assertEquals(-1, cloudHistoryChapterIndex(chapters, "https://other.test/read?id=2"))
    }
}
