package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.EsjzoneUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM coverage for reader persistence identities (Room itself stays in androidTest). */
class ReaderPersistenceContractTest {

    @Test
    fun bookmarkIdentity_isCanonicalChapterPathAndEntityStoresLocalOnlyMetadata() {
        val key = EsjzoneUrls.canonicalPageKey(
            "https://www.esjzone.cc/forum/9001/1.html#quoted-reply"
        )
        val bookmark = Bookmark(
            chapterUrl = key,
            novelId = "9001",
            novelName = "Fixture novel",
            chapterName = "Chapter 1",
            createdAt = 1234L
        )
        assertEquals("/forum/9001/1.html", bookmark.chapterUrl)
        assertEquals(1234L, bookmark.createdAt)

        val position = LocalReadingActivity(
            activityId = "novel:/detail/9001.html",
            novelId = "9001",
            novelName = "Fixture novel",
            novelUrl = "https://www.esjzone.cc/detail/9001.html",
            chapterUrl = "https://www.esjzone.cc/forum/9001/1.html",
            chapterName = "Chapter 1",
            chapterIndex = 0,
            totalChapters = 2,
            chapterProgress = 0.75f,
            startedAt = 100L,
            lastReadAt = 200L,
            durationMs = 100L
        )
        assertEquals("novel:/detail/9001.html", position.activityId)
        assertEquals(0.75f, position.chapterProgress, 0.0001f)
    }
}
