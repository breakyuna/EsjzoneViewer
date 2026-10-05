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
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.offline.NovelDownloadStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBackupInstrumentedTest {
    @Test fun downloadedNovelsRoundTripThroughIncrementalZipSnapshots() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val root = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "backup-downloads-").toFile()
        val restored = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "restored-downloads-").toFile()
        val file = File.createTempFile("downloads-backup-", ".zip", context.cacheDir)
        val authorization = Authorization("", "", "www.esjzone.cc")
        val chapters = (9001..9002).map { Chapter("Chapter", "https://www.esjzone.cc/forum/$it/1.html", false) }
        NovelDownloadStore.initialize(context)
        NovelDownloadStore.initializeDirectory(root)
        try {
            chapters.forEachIndexed { index, chapter ->
                val novelUrl = "https://www.esjzone.cc/detail/${9001 + index}.html"
                NovelDownloadStore.saveChapter("Novel", novelUrl, "", listOf(chapter), chapter,
                    DetailedChapter("Chapter", listOf(TextComponent("正文 $index")), null, null,
                        "<p>正文 $index</p>", chapter.url), authorization)
                NovelDownloadStore.updateCommonPassword(novelUrl, "fixture")
            }
            val selected = setOf(BackupCategory.DOWNLOADS)
            LocalBackup.export(context, Uri.fromFile(file), database, "source", selected)
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                assertTrue("backup.json" in entries)
                assertEquals(2, entries.count { it.startsWith("downloads/") && it.endsWith("/manifest.json") })
                assertEquals(2, entries.count { it.startsWith("downloads/") && it.substringAfterLast('/').startsWith("chapter-") })
                entries.filter { it.endsWith("/manifest.json") }.forEach { name ->
                    val manifest = zip.getInputStream(zip.getEntry(name)).reader().use {
                        com.google.gson.JsonParser.parseReader(it).asJsonObject
                    }
                    assertFalse(manifest.has("commonPassword"))
                }
            }
            NovelDownloadStore.initializeDirectory(restored)
            LocalBackup.restore(context, Uri.fromFile(file), database, "target", selected)
            chapters.forEachIndexed { index, chapter ->
                assertEquals("正文 $index", NovelDownloadStore.readChapter(chapter.url)!!.content
                    .filterIsInstance<TextComponent>().single().text)
            }
        } finally {
            database.close()
            file.delete()
            root.deleteRecursively()
            restored.deleteRecursively()
            NovelDownloadStore.initialize(context)
        }
    }

    @Test fun historyAnchorRoundTripsWithoutDownloadingOrRestoringOtherCategories() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val file = File.createTempFile("history-backup-", ".zip", context.cacheDir)
        try {
            val anchor = com.breakyuna.esjzone.domain.reader.ReaderAnchor("/forum/1/2.html", "a".repeat(64), 3, 12)
            val row = com.breakyuna.esjzone.database.entity.LocalReadingActivity("local:1", "1", "Novel",
                "https://www.esjzone.cc/detail/1.html", "/forum/1/2.html", "Chapter", 1, 10, 0.4f,
                1L, 2L, 100L, anchor = com.breakyuna.esjzone.domain.reader.ReaderAnchor.encode(anchor), bookProgress = 0.14f)
            source.localReadingActivityDao().upsertLatest(row)
            val categories = setOf(BackupCategory.HISTORY)
            LocalBackup.export(context, Uri.fromFile(file), source, "source", categories)
            java.util.zip.ZipFile(file).use { zip ->
                val json = com.google.gson.JsonParser.parseString(zip.getInputStream(zip.getEntry("backup.json")).reader().readText()).asJsonObject
                assertEquals(2, json["version"].asInt)
            }
            LocalBackup.restore(context, Uri.fromFile(file), target, "target", categories)
            assertEquals(row, target.localReadingActivityDao().getAll().single())
            assertTrue(target.bookmarkDao().getAll().isEmpty())
            // An old history archive has neither precise-position field.
            val legacy = java.util.zip.ZipFile(file).use { zip ->
                com.google.gson.JsonParser.parseString(zip.getInputStream(zip.getEntry("backup.json")).reader().readText()).asJsonObject
            }
            legacy.addProperty("version", 1)
            legacy["history"].asJsonArray[0].asJsonObject.apply { remove("anchor"); remove("bookProgress") }
            java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("backup.json"))
                zip.write(legacy.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            target.localReadingActivityDao().deleteAll()
            LocalBackup.restore(context, Uri.fromFile(file), target, "target", categories)
            assertEquals(row.copy(anchor = null, bookProgress = null), target.localReadingActivityDao().getAll().single())
        } finally { source.close(); target.close(); file.delete() }
    }

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
