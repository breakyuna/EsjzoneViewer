package com.breakyuna.esjzone

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.datastore.preferences.preferencesDataStoreFile
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineInfo
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.backup.BackupCategory
import com.breakyuna.esjzone.backup.LocalBackup
import com.breakyuna.esjzone.data.settings.ReaderSettingsDataStore
import com.breakyuna.esjzone.data.settings.SettingsDataStore
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.BookshelfGroup
import com.breakyuna.esjzone.database.entity.BookshelfSyncState
import com.breakyuna.esjzone.database.entity.BookshelfGroupMember
import com.breakyuna.esjzone.database.entity.ReadingStat
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.ui.reader.ReaderBackground
import com.breakyuna.esjzone.ui.reader.ReaderImageBackground
import com.breakyuna.esjzone.ui.reader.ReaderImageBackgroundStore
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceRule
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceSettings
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBackupInstrumentedTest {
    @Test fun multipleParagraphBookmarksSurviveBackupAndRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val file = File.createTempFile("paragraph-bookmarks-", ".zip", context.cacheDir)
        try {
            val chapter = "/forum/1/2.html"
            fun bookmark(block: Int) = Bookmark(chapter, "1", "Novel", "Chapter", block.toLong(),
                anchor = com.breakyuna.esjzone.domain.reader.ReaderAnchor.encode(
                    com.breakyuna.esjzone.domain.reader.ReaderAnchor(chapter, "a".repeat(64), block)),
                excerpt = "Paragraph $block")
            val first = bookmark(1)
            val second = bookmark(5)
            source.bookmarkDao().insert(first)
            source.bookmarkDao().insert(second)
            val selected = setOf(BackupCategory.BOOKMARKS)
            LocalBackup.export(context, Uri.fromFile(file), source, "source", selected)
            ZipFile(file).use { zip ->
                val json = com.google.gson.JsonParser.parseString(
                    zip.getInputStream(zip.getEntry("backup.json")).reader().readText()).asJsonObject
                assertEquals(4, json["version"].asInt)
            }
            repeat(2) { LocalBackup.restore(context, Uri.fromFile(file), target, "target", selected) }
            assertEquals(listOf(second, first), target.bookmarkDao().forChapter(chapter))
            target.bookmarkDao().delete(first)
            assertEquals(listOf(second), target.bookmarkDao().forChapter(chapter))
        } finally {
            source.close()
            target.close()
            file.delete()
        }
    }

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
            val underline = ReaderUnderline(0, "a".repeat(64), 1, 8,
                info = ReaderUnderlineInfo(novelId = "1", novelName = "Novel", chapterName = "Chapter"))
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
            assertEquals(300L, target.readingStatDao().getAll().single().durationMs)
            val restored = target.bookshelfGroupDao()
            assertEquals("Reading", restored.members("target").single().groupName)
            restored.rename("target", "Reading", "Finished")
            assertEquals("Finished", restored.members("target").single().groupName)
            restored.remove("target", "Finished")
            assertTrue(restored.members("target").isEmpty())
            assertTrue(target.bookshelfDao().getAll("target").isEmpty())
        } finally { source.close(); target.close(); file.delete() }
    }

    @Test fun restoreReplacesSelectedPresentCategoriesAndKeepsUnselectedOrAbsentData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val file = File.createTempFile("replacement-backup-", ".zip", context.cacheDir)
        try {
            val saved = Bookmark("/forum/1/1.html", "1", "Saved", "Chapter", 1)
            val extra = Bookmark("/forum/2/1.html", "2", "Extra", "Chapter", 10)
            source.bookmarkDao().insert(saved)
            source.bookshelfGroupDao().add(BookshelfGroup("source", "Saved group"))
            source.bookshelfGroupDao().assign(BookshelfGroupMember("source", "1", "Saved group"))
            val included = setOf(BackupCategory.BOOKMARKS, BackupCategory.GROUPS)
            LocalBackup.export(context, Uri.fromFile(file), source, "source", included)
            target.bookmarkDao().insert(saved.copy(createdAt = 100))
            target.bookmarkDao().insert(extra)
            val stat = ReadingStat("2026-10-19", "1", "Keep", 500)
            target.readingStatDao().add(stat)
            val underlineKey = ReaderUnderlines.KEY_PREFIX + "/forum/1/1.html"
            val marks = ReaderUnderlines.encode(listOf(ReaderUnderline(0, "a".repeat(64), 1, 8)))
            target.cacheDao().putAtomic(underlineKey, marks)
            target.cacheDao().putAtomic("unrelated", "keep")
            target.bookshelfGroupDao().add(BookshelfGroup("target", "Old group"))
            target.bookshelfGroupDao().assign(BookshelfGroupMember("target", "2", "Old group"))
            target.bookshelfGroupDao().add(BookshelfGroup("other-account", "Keep group"))

            LocalBackup.restore(context, Uri.fromFile(file), target, "target", included + BackupCategory.READING)
            assertEquals(listOf(saved), target.bookmarkDao().getAll())
            assertEquals(listOf(stat), target.readingStatDao().getAll()) // selected but absent
            assertEquals(marks, target.cacheDao().findByKey(underlineKey)?.value) // unselected
            assertEquals("keep", target.cacheDao().findByKey("unrelated")?.value)
            assertEquals(listOf("Saved group"), target.bookshelfGroupDao().groups("target").map { it.name })
            assertEquals(listOf("1"), target.bookshelfGroupDao().members("target").map { it.bookKey })
            assertEquals(1, target.bookshelfGroupDao().groups("other-account").size)

            source.bookmarkDao().deleteAll(listOf(saved))
            LocalBackup.export(context, Uri.fromFile(file), source, "source", setOf(BackupCategory.BOOKMARKS))
            LocalBackup.restore(context, Uri.fromFile(file), target, "target", setOf(BackupCategory.BOOKMARKS))
            assertTrue(target.bookmarkDao().getAll().isEmpty())
            assertEquals(listOf(stat), target.readingStatDao().getAll())
        } finally { source.close(); target.close(); file.delete() }
    }

    @Test fun bookshelfRestoreMergesVisibleBooksIntoCurrentAccountWithoutReplayingSync() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val file = File.createTempFile("bookshelf-backup-", ".zip", context.cacheDir)
        try {
            val saved = BookshelfEntry("source", "/detail/1.html", novelId = "1",
                url = "/detail/1.html", title = "Saved", author = "Author", coverUrl = "https://example.com/cover.jpg",
                isAdult = true, addedAt = 42L, syncState = BookshelfSyncState.PENDING_ADD, operationVersion = 5L)
            val removed = saved.copy(bookKey = "/detail/2.html", url = "/detail/2.html",
                visible = false, syncState = BookshelfSyncState.PENDING_REMOVE)
            val existing = saved.copy(scope = "target", title = "Existing",
                bookKey = "/detail/3.html", url = "/detail/3.html")
            val pendingRemoval = removed.copy(scope = "target", bookKey = "/detail/4.html", url = "/detail/4.html")
            val dao = database.bookshelfDao()
            dao.upsertAll(listOf(saved, removed, existing, pendingRemoval,
                existing.copy(scope = "source", title = "Old title"),
                pendingRemoval.copy(scope = "source", visible = true, syncState = BookshelfSyncState.SYNCED)))
            val selected = setOf(BackupCategory.BOOKSHELF)
            LocalBackup.export(context, Uri.fromFile(file), database, "source", selected)
            ZipFile(file).use { zip ->
                val json = zip.getInputStream(zip.getEntry("backup.json")).reader().use { it.readText() }
                assertFalse(json.contains("source"))
                assertFalse(json.contains("operationVersion"))
                assertFalse(json.contains("/detail/2.html"))
            }
            repeat(2) { LocalBackup.restore(context, Uri.fromFile(file), database, "target", selected) }
            val restored = saved.copy(scope = "target", syncState = BookshelfSyncState.SYNCED, operationVersion = 0)
            assertEquals(setOf(restored, existing, pendingRemoval), dao.getAll("target").toSet())
            assertEquals(saved, dao.find("source", saved.bookKey))
        } finally { database.close(); file.delete() }
    }

    @Test fun readerSettingsRoundTripWithPhotoAndKeepOtherAppPreferences() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val files = Files.createTempDirectory(base.cacheDir.toPath(), "backup-reader-").toFile()
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = files
        }
        val database = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val readerFile = "reader-backup-${UUID.randomUUID()}.preferences_pb"
        val appFile = "app-backup-${UUID.randomUUID()}.preferences_pb"
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val file = File(files, "reader.zip")
        try {
            val app = SettingsDataStore(context, scope, appFile)
            val reader = ReaderSettingsDataStore(context, scope, readerFile)
            val appearance = ReaderImageBackground(fileName = "${UUID.randomUUID()}.jpg",
                cropX = 0.25f, cropY = 0.75f, blur = 20f, transparency = 40f, glowEnabled = false)
            val source = requireNotNull(ReaderImageBackgroundStore.file(context, appearance))
            source.parentFile?.mkdirs()
            val bitmap = Bitmap.createBitmap(24, 48, Bitmap.Config.ARGB_8888)
            try {
                source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            } finally { bitmap.recycle() }
            val bytes = source.readBytes()
            val settings = ReaderSettings(fontSizeSp = 24f, firstLineIndentChars = 1)
            val names = ReaderNameReplaceSettings(true, listOf(
                ReaderNameReplaceRule(source = "Before", target = "After"),
                ReaderNameReplaceRule(source = "A([0-9])", target = "B$1", bookKey = "book-1", regex = true),
                ReaderNameReplaceRule(source = "", target = "", bookKey = "book-2")
            ))
            reader.save(settings)
            reader.saveImageBackground(appearance)
            reader.saveNameReplaceSettings(names)
            val selected = setOf(BackupCategory.READER_SETTINGS)
            LocalBackup.export(context, Uri.fromFile(file), database, "source", selected, reader)

            source.delete()
            reader.save(ReaderSettings())
            reader.saveNameReplaceSettings(ReaderNameReplaceSettings())
            app.setAdult(false)
            withTimeout(5_000) { app.adult.first { !it } }

            LocalBackup.restore(context, Uri.fromFile(file), database, "target", selected, reader)
            val restoredPhoto = reader.readImageBackground()
            assertNotEquals(appearance.fileName, restoredPhoto.fileName)
            assertEquals(appearance.copy(fileName = restoredPhoto.fileName), restoredPhoto)
            assertArrayEquals(bytes, requireNotNull(ReaderImageBackgroundStore.file(context, restoredPhoto)).readBytes())
            assertEquals(settings.copy(background = ReaderBackground.CUSTOM_IMAGE),
                withTimeout(5_000) { reader.settings.first { it.fontSizeSp == 24f } })
            assertEquals(names, withTimeout(5_000) { reader.nameReplaceSettings.first { it.enabled } })
            assertFalse(app.adult.value)
        } finally {
            job.cancelAndJoin()
            base.preferencesDataStoreFile(readerFile).delete()
            base.preferencesDataStoreFile(appFile).delete()
            database.close()
            files.deleteRecursively()
        }
    }
}
