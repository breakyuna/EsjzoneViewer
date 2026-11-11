package com.breakyuna.esjzone

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.ui.page.UnderlineListRow
import com.breakyuna.esjzone.ui.page.deleteUnderlineRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnderlinesPageInstrumentedTest {
    @Test fun batchDeletionKeepsUnselectedRangesAndOtherChapters() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            GeneralDatabase::class.java).build()
        try {
            val first = ReaderUnderline(0, "a".repeat(64), 0, 2, "first")
            val second = ReaderUnderline(1, "b".repeat(64), 1, 3, "second")
            val third = ReaderUnderline(2, "c".repeat(64), 1, 3, "third")
            fun row(chapter: String, mark: ReaderUnderline) =
                UnderlineListRow(chapter, mark, "1", "Novel", "/detail/1.html", "Chapter", "")
            withContext(Dispatchers.IO) {
                val dao = database.cacheDao()
                dao.putAtomic(ReaderUnderlines.KEY_PREFIX + "/forum/1/1.html", ReaderUnderlines.encode(listOf(first, second)))
                dao.putAtomic(ReaderUnderlines.KEY_PREFIX + "/forum/1/2.html", ReaderUnderlines.encode(listOf(third)))
                deleteUnderlineRows(database, listOf(row("/forum/1/1.html", first), row("/forum/1/2.html", third)))
                assertEquals(listOf(second), ReaderUnderlines.decode(dao.findByKey(ReaderUnderlines.KEY_PREFIX + "/forum/1/1.html")?.value))
                assertNull(dao.findByKey(ReaderUnderlines.KEY_PREFIX + "/forum/1/2.html"))
            }
        } finally { database.close() }
    }
}
