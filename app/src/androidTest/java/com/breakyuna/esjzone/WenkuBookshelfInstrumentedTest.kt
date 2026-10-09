package com.breakyuna.esjzone

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.BookshelfGroup
import com.breakyuna.esjzone.database.entity.BookshelfGroupMember
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WenkuBookshelfInstrumentedTest {
    @Test fun localFavoriteAndDeletionKeepEsjRowsAndRemoveOnlyWenkuMembership() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val repositoryFields = listOf("database", "dao", "localReadingDao").map { name ->
            BookshelfRepository::class.java.getDeclaredField(name).apply { isAccessible = true }
        }
        val previousDependencies = repositoryFields.associateWith { it.get(BookshelfRepository) }
        BookshelfRepository.initialize(database)
        val auth = Authorization("", "", "www.esjzone.cc")
        val otherSite = Authorization("", "", "www.esjzone.one")
        val localScope = BookshelfRepository.WENKU8_SCOPE
        val url = "https://www.wenku8.net/book/2552.htm"
        val book = CoveredNovelImpl(name = "Synthetic Wenku book", url = url, author = "Synthetic author")
        try {
            val dao = database.bookshelfDao()
            val groups = database.bookshelfGroupDao()
            val esj = BookshelfEntry(scope = "synthetic-esj", bookKey = "/detail/2552.html",
                novelId = "2552", url = "/detail/2552.html", title = "Synthetic ESJ book")
            dao.upsert(esj)
            BookshelfRepository.setFavorite(auth, book, true)
            val row = requireNotNull(dao.find(localScope, BookshelfRepository.keyFor(url)))
            assertEquals("wenku8:2552", row.novelId)
            assertEquals("SYNCED", row.syncState)
            assertEquals(BookshelfRepository.scopeFor(auth, true), BookshelfRepository.scopeFor(otherSite, true))
            groups.add(BookshelfGroup(localScope, "Reading"))
            groups.assign(BookshelfGroupMember(localScope, row.bookKey, "Reading"))
            assertEquals(0, BookshelfRepository.removeBatch(otherSite, listOf(row.copy(operationVersion = row.operationVersion + 1))))
            assertEquals(1, groups.members(localScope).size)
            assertEquals(1, BookshelfRepository.removeBatch(otherSite, listOf(row)))
            assertTrue(groups.members(localScope).isEmpty())
            assertEquals(listOf(esj), dao.getAll(esj.scope))
            BookshelfRepository.setFavorite(otherSite, book, true)
            groups.assign(BookshelfGroupMember(localScope, row.bookKey, "Reading"))
            BookshelfRepository.setFavorite(auth, book, false)
            assertTrue(dao.getAll(localScope).isEmpty())
            assertTrue(groups.members(localScope).isEmpty())
        } finally {
            previousDependencies.forEach { (field, value) -> field.set(BookshelfRepository, value) }
            database.close()
        }
    }
}
