package com.breakyuna.esjzone

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookshelfUpdateScopeInstrumentedTest {
    @Test
    fun readingTheLatestChapterOnlyClearsTheReadingAccountsUpdate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        try {
            val dao = database.bookshelfDao()
            val entry = BookshelfEntry(scope = "account-a", bookKey = "/detail/123.html",
                url = "/detail/123.html", title = "Novel", latestFingerprint = "/forum/123/456.html",
                hasUpdate = true)
            dao.upsert(entry)
            dao.upsert(entry.copy(scope = "account-b"))
            dao.clearUpdateForFingerprint("account-a", entry.latestFingerprint)
            assertFalse(requireNotNull(dao.find("account-a", entry.bookKey)).hasUpdate)
            assertTrue(requireNotNull(dao.find("account-b", entry.bookKey)).hasUpdate)
        } finally { database.close() }
    }
}
