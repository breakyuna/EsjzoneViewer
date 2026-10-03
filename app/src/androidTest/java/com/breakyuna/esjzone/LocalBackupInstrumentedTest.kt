package com.breakyuna.esjzone

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.backup.BackupCategory
import com.breakyuna.esjzone.backup.LocalBackup
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.BookshelfGroup
import com.breakyuna.esjzone.database.entity.BookshelfGroupMember
import com.breakyuna.esjzone.database.entity.ReadingStat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBackupInstrumentedTest {
    @Test fun selectedCategoriesRoundTripAndGroupsStayLocal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val file = File.createTempFile("backup-test-", ".zip", context.cacheDir)
        try {
            val bookmark = Bookmark("https://www.esjzone.cc/forum/1/2.html", "1", "Novel", "Chapter", 1)
            source.bookmarkDao().insert(bookmark)
            source.readingStatDao().add(ReadingStat("2026-09-29", "1", "Novel", 300))
            val groups = source.bookshelfGroupDao()
            groups.add(BookshelfGroup("source", "Reading"))
            groups.assign(BookshelfGroupMember("source", "1", "Reading"))
            val underlineKey = ReaderUnderlines.KEY_PREFIX + "/forum/1/2.html"
            val underline = ReaderUnderline(0, "a".repeat(64), 1, 8)
            source.cacheDao().putAtomic(underlineKey, ReaderUnderlines.encode(listOf(underline)))
            val selected = setOf(BackupCategory.UNDERLINES, BackupCategory.BOOKMARKS, BackupCategory.READING, BackupCategory.GROUPS)
            LocalBackup.export(context, Uri.fromFile(file), source, "source", selected)
            LocalBackup.restore(context, Uri.fromFile(file), target, "target", setOf(BackupCategory.READING))
            assertNull(target.cacheDao().findByKey(underlineKey))
            assertTrue(target.bookmarkDao().getAll().isEmpty())
            assertTrue(target.bookshelfGroupDao().groups("target").isEmpty())
            repeat(2) { LocalBackup.restore(context, Uri.fromFile(file), target, "target", selected) }
            assertEquals(listOf(underline), ReaderUnderlines.decode(target.cacheDao().findByKey(underlineKey)?.value))
            assertEquals(listOf(bookmark), target.bookmarkDao().getAll())
            assertEquals(300L, target.readingStatDao().getAll().single().durationMs)
            target.readingStatDao().add(ReadingStat("2026-09-29", "1", "Novel", 100))
            LocalBackup.restore(context, Uri.fromFile(file), target, "target", selected)
            assertEquals(400L, target.readingStatDao().getAll().single().durationMs)
            val restored = target.bookshelfGroupDao()
            assertEquals("Reading", restored.members("target").single().groupName)
            restored.rename("target", "Reading", "Finished")
            assertEquals("Finished", restored.members("target").single().groupName)
            restored.remove("target", "Finished")
            assertTrue(restored.members("target").isEmpty())
            assertTrue(target.bookshelfDao().getAll("target").isEmpty())
        } finally { source.close(); target.close(); file.delete() }
    }
}
