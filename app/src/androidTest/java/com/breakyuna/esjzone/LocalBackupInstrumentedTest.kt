package com.breakyuna.esjzone

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.backup.BackupCategory
import com.breakyuna.esjzone.backup.LocalBackup
import com.breakyuna.esjzone.data.settings.ReaderSettingsDataStore
import com.breakyuna.esjzone.data.settings.SettingsDataStore
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.BookshelfGroup
import com.breakyuna.esjzone.database.entity.BookshelfGroupMember
import com.breakyuna.esjzone.database.entity.BookshelfSyncState
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.database.entity.ReadingStat
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineInfo
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
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
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBackupInstrumentedTest {
    @Test fun databaseCategoriesRoundTripWithSelectiveReplacementHistoryAnchorAndBookshelfMerge() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val file = File.createTempFile("db-backup-", ".zip", context.cacheDir)
        try {
            val chapter = "/forum/1/2.html"
            fun bookmark(block: Int) = Bookmark(chapter, "1", "Novel", "Chapter", block.toLong(),
                anchor = ReaderAnchor.encode(ReaderAnchor(chapter, "a".repeat(64), block)),
                excerpt = "Paragraph $block")
            val firstMark = bookmark(1)
            val secondMark = bookmark(5)
            source.bookmarkDao().insert(firstMark)
            source.bookmarkDao().insert(secondMark)

            val historyAnchor = ReaderAnchor(chapter, "a".repeat(64), 3, 12)
            val historyRow = LocalReadingActivity("local:1", "1", "Novel",
                "https://www.esjzone.cc/detail/1.html", chapter, "Chapter", 1, 10, 0.4f,
                1L, 2L, 100L, anchor = ReaderAnchor.encode(historyAnchor), bookProgress = 0.14f)
            source.localReadingActivityDao().upsertLatest(historyRow)

            source.readingStatDao().add(ReadingStat("2026-09-29", "1", "Novel", 300))
            source.bookshelfGroupDao().add(BookshelfGroup("source", "Reading"))
            source.bookshelfGroupDao().assign(BookshelfGroupMember("source", "1", "Reading"))
            val underlineKey = ReaderUnderlines.KEY_PREFIX + chapter
            val underline = ReaderUnderline(0, "a".repeat(64), 1, 8,
                info = ReaderUnderlineInfo(novelId = "1", novelName = "Novel", chapterName = "Chapter"))
            source.cacheDao().putAtomic(underlineKey, ReaderUnderlines.encode(listOf(underline)))

            val savedShelf = BookshelfEntry("source", "/detail/1.html", novelId = "1",
                url = "/detail/1.html", title = "Saved", author = "Author", coverUrl = "https://example.com/cover.jpg",
                isAdult = true, addedAt = 42L, syncState = BookshelfSyncState.PENDING_ADD, operationVersion = 5L)
            val removedShelf = savedShelf.copy(bookKey = "/detail/2.html", url = "/detail/2.html",
                visible = false, syncState = BookshelfSyncState.PENDING_REMOVE)
            val existingShelf = savedShelf.copy(scope = "target", title = "Existing",
                bookKey = "/detail/3.html", url = "/detail/3.html")
            val pendingRemovalShelf = removedShelf.copy(scope = "target", bookKey = "/detail/4.html", url = "/detail/4.html")
            source.bookshelfDao().upsertAll(listOf(savedShelf, removedShelf,
                existingShelf.copy(scope = "source", title = "Old title"),
                pendingRemovalShelf.copy(scope = "source", visible = true, syncState = BookshelfSyncState.SYNCED)))

            // Seed target with extra/other-account data to verify selective replacement and isolation.
            target.bookmarkDao().insert(Bookmark("/forum/2/1.html", "2", "Extra", "Chapter", 10))
            target.cacheDao().putAtomic("unrelated", "keep")
            target.bookshelfGroupDao().add(BookshelfGroup("target", "Old group"))
            target.bookshelfGroupDao().assign(BookshelfGroupMember("target", "2", "Old group"))
            target.bookshelfGroupDao().add(BookshelfGroup("other-account", "Keep group"))
            target.bookshelfDao().upsertAll(listOf(existingShelf, pendingRemovalShelf))

            val allSelected = setOf(
                BackupCategory.BOOKMARKS, BackupCategory.HISTORY, BackupCategory.UNDERLINES,
                BackupCategory.READING, BackupCategory.GROUPS, BackupCategory.BOOKSHELF
            )
            LocalBackup.export(context, Uri.fromFile(file), source, "source", allSelected)
            ZipFile(file).use { zip ->
                val raw = zip.getInputStream(zip.getEntry("backup.json")).reader().use { it.readText() }
                assertEquals(4, JsonParser.parseString(raw).asJsonObject["version"].asInt)
                assertFalse(raw.contains("operationVersion"))
                assertFalse(raw.contains("/detail/2.html"))
            }

            // Restoring only READING must leave other categories untouched.
            LocalBackup.restore(context, Uri.fromFile(file), target, "target", setOf(BackupCategory.READING))
            assertNull(target.cacheDao().findByKey(underlineKey))
            assertTrue(target.localReadingActivityDao().getAll().isEmpty())
            assertEquals(listOf("Old group"), target.bookshelfGroupDao().groups("target").map { it.name })

            // Repeated full restore is idempotent, replaces present selected categories, and merges bookshelf.
            repeat(2) { LocalBackup.restore(context, Uri.fromFile(file), target, "target", allSelected) }
            assertEquals(listOf(secondMark, firstMark), target.bookmarkDao().forChapter(chapter))
            assertEquals(historyRow, target.localReadingActivityDao().getAll().single())
            assertEquals(listOf(underline), ReaderUnderlines.decode(target.cacheDao().findByKey(underlineKey)?.value))
            assertEquals("keep", target.cacheDao().findByKey("unrelated")?.value)
            assertEquals(300L, target.readingStatDao().getAll().single().durationMs)
            assertEquals(listOf("Reading"), target.bookshelfGroupDao().groups("target").map { it.name })
            assertEquals(listOf("1"), target.bookshelfGroupDao().members("target").map { it.bookKey })
            assertEquals(1, target.bookshelfGroupDao().groups("other-account").size)
            val restoredShelf = savedShelf.copy(scope = "target", syncState = BookshelfSyncState.SYNCED, operationVersion = 0)
            assertEquals(setOf(restoredShelf, existingShelf, pendingRemovalShelf), target.bookshelfDao().getAll("target").toSet())
            assertEquals(savedShelf, source.bookshelfDao().find("source", savedShelf.bookKey))

            // Absent selected categories stay intact while an explicitly empty present category clears target.
            source.bookmarkDao().deleteAll(listOf(firstMark, secondMark))
            LocalBackup.export(context, Uri.fromFile(file), source, "source", setOf(BackupCategory.BOOKMARKS))
            LocalBackup.restore(context, Uri.fromFile(file), target, "target",
                setOf(BackupCategory.BOOKMARKS, BackupCategory.READING))
            assertTrue(target.bookmarkDao().getAll().isEmpty())
            assertEquals(300L, target.readingStatDao().getAll().single().durationMs)

            // Legacy v1 history archives without precise-position fields still restore cleanly.
            LocalBackup.export(context, Uri.fromFile(file), source, "source", setOf(BackupCategory.HISTORY))
            val legacy = ZipFile(file).use { zip ->
                JsonParser.parseString(zip.getInputStream(zip.getEntry("backup.json")).reader().readText()).asJsonObject
            }
            legacy.addProperty("version", 1)
            legacy["history"].asJsonArray[0].asJsonObject.apply { remove("anchor"); remove("bookProgress") }
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(legacy.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            target.localReadingActivityDao().deleteAll()
            LocalBackup.restore(context, Uri.fromFile(file), target, "target", setOf(BackupCategory.HISTORY))
            assertEquals(historyRow.copy(anchor = null, bookProgress = null), target.localReadingActivityDao().getAll().single())
        } finally {
            source.close()
            target.close()
            file.delete()
        }
    }

    @Test fun downloadedNovelsRoundTripThroughIncrementalZipSnapshots() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "backup-downloads-").toFile()
        val restored = Files.createTempDirectory(context.cacheDir.toPath(), "restored-downloads-").toFile()
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
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                assertTrue("backup.json" in entries)
                assertEquals(2, entries.count { it.startsWith("downloads/") && it.endsWith("/manifest.json") })
                assertEquals(2, entries.count { it.startsWith("downloads/") && it.substringAfterLast('/').startsWith("chapter-") })
                entries.filter { it.endsWith("/manifest.json") }.forEach { name ->
                    val manifest = zip.getInputStream(zip.getEntry(name)).reader().use {
                        JsonParser.parseReader(it).asJsonObject
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
