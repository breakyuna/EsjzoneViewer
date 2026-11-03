package com.breakyuna.esjzone

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.BookshelfSyncState
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.database.entity.ReadingStat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NovelTitleDaoInstrumentedTest {
    private lateinit var database: GeneralDatabase

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), GeneralDatabase::class.java).build()
    }

    @After
    fun close() { database.close() }

    @Test
    fun repairCandidatesExcludeKnownTitlesAndKeepLegacyPlaceholders() = runBlocking {
        val history = LocalReadingActivity("book:123", "123", "Real title", "/detail/123.html",
            "/forum/123/1.html", "Chapter one", 0, 10, .5f, 1L, 2L, 10L)
        database.localReadingActivityDao().upsert(history)
        database.localReadingActivityDao().upsert(history.copy(activityId = "legacy", novelName = "123"))
        assertEquals(listOf("legacy"), database.localReadingActivityDao().titleRepairCandidates().map { it.activityId })

        database.bookmarkDao().insertRaw(Bookmark("/forum/123/1.html", "123", "Real title", "Chapter one", 1L))
        database.bookmarkDao().insertRaw(Bookmark("/forum/123/2.html", "123", "", "Chapter two", 2L))
        assertEquals(listOf("/forum/123/2.html"), database.bookmarkDao().titleRepairCandidates().map { it.chapterUrl })

        val shelf = BookshelfEntry("account", "/detail/123.html", "123", "/detail/123.html", "Real title")
        database.bookshelfDao().upsert(shelf)
        database.bookshelfDao().upsert(shelf.copy(bookKey = "/detail/456.html", novelId = "456", url = "/detail/456.html", title = "456"))
        assertEquals(listOf("456"), database.bookshelfDao().titleRepairCandidates().map { it.novelId })

        database.readingStatDao().insertIfMissing(ReadingStat("2026-10-30", "123", "Real title", 1L))
        database.readingStatDao().insertIfMissing(ReadingStat("2026-10-30", "456", "", 1L))
        assertEquals(listOf("456"), database.readingStatDao().titleRepairCandidates().map { it.bookKey })
    }

    @Test
    fun recordingAndBackupMergeKeepTheTitleAndDuration() {
        val dao = database.readingStatDao()
        dao.add(ReadingStat("2026-10-30", "123", "1984", 10_000L))
        dao.add(ReadingStat("2026-10-30", "123", "123", 20_000L))
        assertEquals(ReadingStat("2026-10-30", "123", "1984", 30_000L), dao.getAll().single())
        dao.merge(ReadingStat("2026-10-30", "123", "123", 40_000L))
        assertEquals(ReadingStat("2026-10-30", "123", "1984", 40_000L), dao.getAll().single())
    }

    @Test
    fun bookmarkedAndResumedNamesDoNotRegressToIds() {
        val bookmark = Bookmark("/forum/123/1.html", "123", "Real title", "Chapter one", 1L)
        database.bookmarkDao().insert(bookmark)
        database.bookmarkDao().insert(bookmark.copy(novelName = "123", createdAt = 2L))
        assertEquals("Real title", database.bookmarkDao().getAll().single().novelName)

        val history = LocalReadingActivity("book:123", "123", "Real title", "/detail/123.html",
            "/forum/123/1.html", "Chapter one", 0, 10, .5f, 1L, 2L, 10L)
        database.localReadingActivityDao().upsertLatest(history)
        database.localReadingActivityDao().upsertLatest(history.copy(novelName = "123", chapterProgress = .75f))
        assertEquals(history.copy(chapterProgress = .75f), database.localReadingActivityDao().getLatest())
    }

    @Test
    fun detailTitleRefreshPreservesShelfStateAndAccountIsolation() = runBlocking {
        val dao = database.bookshelfDao()
        val shelf = BookshelfEntry("account", "/detail/123.html", "123", "/detail/123.html", "Old title",
            coverUrl = "https://example.invalid/cover.jpg", syncState = BookshelfSyncState.PENDING_ADD,
            operationVersion = 7L, hasUpdate = true)
        val otherAccount = shelf.copy(scope = "other-account")
        dao.upsert(shelf)
        dao.upsert(otherAccount)

        assertEquals(1, dao.updateTitleIfChanged(shelf.scope, shelf.bookKey, "New title"))
        assertEquals(shelf.copy(title = "New title"), dao.find(shelf.scope, shelf.bookKey))
        assertEquals(otherAccount, dao.find(otherAccount.scope, otherAccount.bookKey))
        assertEquals(0, dao.updateTitleIfChanged(shelf.scope, shelf.bookKey, "New title"))
        assertEquals(0, dao.updateTitleIfChanged(shelf.scope, shelf.bookKey, ""))
        assertEquals(0, dao.updateTitleIfChanged(shelf.scope, "/detail/missing.html", "New title"))

        val removed = shelf.copy(visible = false, syncState = BookshelfSyncState.PENDING_REMOVE)
        dao.upsert(removed)
        assertEquals(0, dao.updateTitleIfChanged(shelf.scope, shelf.bookKey, "New title"))
        assertEquals(removed, dao.find(shelf.scope, shelf.bookKey))
    }

    @Test
    fun repairingLegacyTitlesDoesNotMoveProgressOrOverwriteNewerNames() = runBlocking {
        val history = LocalReadingActivity("book:123", "123", "123", "/detail/123.html",
            "/forum/123/1.html", "Chapter one", 0, 10, .5f, 1L, 2L, 10L)
        database.localReadingActivityDao().upsert(history)
        database.localReadingActivityDao().repairNovelName(history.activityId, "123", "Real title")
        database.localReadingActivityDao().repairNovelName(history.activityId, "123", "Stale title")
        assertEquals(history.copy(novelName = "Real title"), database.localReadingActivityDao().getLatest())

        val shelf = BookshelfEntry("account", "/detail/123.html", "123", "/detail/123.html", "123",
            syncState = BookshelfSyncState.PENDING_REMOVE, visible = false, operationVersion = 7L)
        database.bookshelfDao().upsert(shelf)
        database.bookshelfDao().repairNovelName(shelf.scope, shelf.bookKey, "123", "Real title")
        assertEquals(shelf.copy(title = "Real title"), database.bookshelfDao().find(shelf.scope, shelf.bookKey))
    }
}
