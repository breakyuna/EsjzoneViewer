package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.ui.page.statisticsActivitiesByBook
import com.breakyuna.esjzone.ui.page.statisticsCoverLookupUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingStatisticsCoverTest {
    private fun activity(id: String, novelId: String, novelUrl: String, chapterUrl: String = "") =
        LocalReadingActivity(id, novelId, "书名", novelUrl, chapterUrl, "章节", 0, 1,
            0f, 0L, 0L, 0L)

    @Test fun historyUsesTheSameBookIdentityAsReadingStatistics() {
        val latest = activity("new", "123", "https://www.esjzone.cc/detail/123.html")
        val older = activity("old", "123", "https://www.esjzone.one/detail/123.html")
        val missingId = activity("other", "", "https://www.esjzone.cc/detail/456.html")
        val byBook = statisticsActivitiesByBook(listOf(latest, older, missingId))
        assertEquals(latest, byBook["123"])
        assertEquals(missingId, byBook["456"])
    }

    @Test fun fallbackDoesNotTreatAForumPostAsABook() {
        assertEquals("/detail/123.html", statisticsCoverLookupUrl("123"))
        assertEquals("/detail/123.html", statisticsCoverLookupUrl("/detail/123.html"))
        assertEquals("https://example.com/book/42", statisticsCoverLookupUrl("https://example.com/book/42"))
        assertNull(statisticsCoverLookupUrl("/forum/123/456.html"))
        assertNull(statisticsCoverLookupUrl("https://user:password@example.com/book"))
        assertNull(statisticsCoverLookupUrl(""))
    }
}
