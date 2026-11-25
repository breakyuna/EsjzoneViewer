package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.BookshelfSort
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfSortTest {

    @Test
    fun recentlyReadBooksComeFirstAndNewestReadWins() {
        val entries = listOf(
            entry("unread", addedAt = 300),
            entry("old-read", addedAt = 100, novelId = "2"),
            entry("new-read", addedAt = 200, novelId = "1")
        )
        val activities = listOf(
            activity("2", lastReadAt = 10),
            activity("1", lastReadAt = 20)
        )

        assertEquals(
            listOf("new-read", "old-read", "unread"),
            BookshelfSort.sort(
                entries = entries,
                activities = activities,
                keyForUrl = { it.trim() }
            ).map { it.bookKey }
        )
    }

    @Test
    fun urlFallbackMatchesDifferentHostAndUsesStableKeyTieBreak() {
        val entries = listOf(
            entry("/detail/b", addedAt = 10, url = "https://www.esjzone.cc/detail/b.html"),
            entry("/detail/a", addedAt = 10, url = "https://www.esjzone.cc/detail/a.html")
        )
        val activities = listOf(
            activity("", "https://www.esjzone.one/detail/a.html", 99)
        )

        assertEquals(
            listOf("/detail/a", "/detail/b"),
            BookshelfSort.sort(
                entries = entries,
                activities = activities,
                keyForUrl = { url ->
                    url.substringAfter("/detail/").substringBefore(".html").let { "/detail/$it" }
                }
            ).map { it.bookKey }
        )
    }

    @Test
    fun recentAddedSortUsesLocalInsertionTime() {
        val entries = listOf(
            entry("old", addedAt = 100),
            entry("new", addedAt = 300),
            entry("middle", addedAt = 200)
        )

        assertEquals(
            listOf("new", "middle", "old"),
            BookshelfSort.sort(
                entries, emptyList(), { it.trim() }, BookshelfSort.Order.RECENT_ADDED
            ).map { it.bookKey }
        )
    }

    @Test
    fun recentUpdatedSortParsesObservedDateTextAndFallsBackToAddedAt() {
        val entries = listOf(
            entry("old-update", addedAt = 300).copy(remoteUpdatedAt = "2026-09-01"),
            entry("new-update", addedAt = 100).copy(remoteUpdatedAt = "2026-09-12"),
            entry("unknown-update", addedAt = 200)
        )

        assertEquals(
            listOf("new-update", "old-update", "unknown-update"),
            BookshelfSort.sort(
                entries, emptyList(), { it.trim() }, BookshelfSort.Order.RECENT_UPDATED
            ).map { it.bookKey }
        )
    }

    private fun entry(
        key: String,
        addedAt: Long,
        novelId: String = key,
        url: String = "https://www.esjzone.cc/detail/$key.html"
    ) = BookshelfEntry(
        scope = "test",
        bookKey = key,
        novelId = novelId,
        url = url,
        title = key,
        addedAt = addedAt
    )

    private fun activity(
        novelId: String,
        novelUrl: String = "https://www.esjzone.cc/detail/$novelId.html",
        lastReadAt: Long
    ) = LocalReadingActivity(
        activityId = novelId.ifBlank { novelUrl },
        novelId = novelId,
        novelName = novelId,
        novelUrl = novelUrl,
        chapterUrl = "",
        chapterName = "",
        chapterIndex = 0,
        totalChapters = 0,
        chapterProgress = 0f,
        startedAt = lastReadAt,
        lastReadAt = lastReadAt,
        durationMs = 0
    )
}
