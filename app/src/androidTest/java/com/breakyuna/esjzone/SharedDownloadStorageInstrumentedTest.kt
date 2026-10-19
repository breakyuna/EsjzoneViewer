package com.breakyuna.esjzone

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.offline.NovelExporter
import com.breakyuna.esjzone.offline.DownloadedComponent
import com.breakyuna.esjzone.storage.StorageFile
import com.breakyuna.esjzone.storage.StorageAccessException
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedDownloadStorageInstrumentedTest {
    @Before fun allowFixtureDocuments() {
        context.contentResolver.call(treeUri, "grant-fixture-tree", null, null)
    }
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val treeUri get() = DocumentsContract.buildTreeDocumentUri("com.breakyuna.esjzone.storage.test.documents", "root")

    private fun configure(directory: StorageFile, mode: String? = null, operation: String = "all") {
        context.contentResolver.call(treeUri, "configure-fixture", directory.name, android.os.Bundle().apply {
            putString("mode", mode)
            putString("operation", operation)
        })
    }

    @Test fun providerAccessFailuresAreReportedAsStorageErrorsForQueriesAndFileOperations() {
        val directory = StorageFile(StorageFile.fromTree(context, treeUri), "case-${UUID.randomUUID()}")
            .apply { check(mkdirs()) }
        try {
            StorageFile(directory, "fixture.json").writeText("fixture")
            for (mode in listOf("security", "argument")) {
                for (operation in listOf("metadata", "children", "open", "create", "rename", "delete")) {
                    configure(directory, mode, operation)
                    try {
                        val fresh = StorageFile(StorageFile.fromTree(context, treeUri), directory.name)
                        assertThrows(StorageAccessException::class.java) {
                            val file = StorageFile(fresh, "fixture.json")
                            when (operation) {
                                "metadata" -> fresh.isDirectory
                                "children" -> fresh.listFiles()
                                "open" -> file.inputStream().use { it.read() }
                                "create" -> StorageFile(fresh, "new.json").outputStream().close()
                                "rename" -> file.renameTo(StorageFile(fresh, "renamed.json"))
                                "delete" -> file.delete()
                            }
                        }
                    } finally { configure(directory) }
                }
            }
            assertEquals("fixture", StorageFile(directory, "fixture.json").inputStream().reader().use { it.readText() })
        } finally { configure(directory); directory.deleteRecursively() }
    }

    @Test fun unrepresentableProviderNamesDoNotHideValidDownloads() {
        val directory = StorageFile(StorageFile.fromTree(context, treeUri), "case-${UUID.randomUUID()}")
            .apply { check(mkdirs()) }
        try {
            StorageFile(directory, "valid.json").writeText("fixture")
            configure(directory, "odd-names")
            val fresh = StorageFile(StorageFile.fromTree(context, treeUri), directory.name)
            assertEquals(listOf("valid.json"), fresh.listFiles()!!.map { it.name })
            assertEquals("fixture", StorageFile(fresh, "valid.json").inputStream().reader().use { it.readText() })
            assertFalse(StorageFile(fresh, "absent.json").exists())
        } finally { configure(directory); directory.deleteRecursively() }
    }

    @Test fun migrateReadRescanExportAndRestoreThroughDocumentUris() {
        val privateRoot = File(context.cacheDir, "storage-${UUID.randomUUID()}").apply { mkdirs() }
        val tree = StorageFile.fromTree(context, treeUri)
        val shared = StorageFile(tree, "case-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val downloads = StorageFile(shared, "downloads")
        val backup = File(context.cacheDir, "storage-backup-${UUID.randomUUID()}")
        val novelId = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).toString()
        val novelUrl = "https://www.esjzone.cc/detail/$novelId.html"
        val secretKey = java.security.MessageDigest.getInstance("SHA-256").digest("/detail/$novelId.html".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val chapter = Chapter("Chapter", "https://www.esjzone.cc/forum/$novelId/1.html", false)
        try {
            NovelDownloadStore.initialize(context)
            NovelDownloadStore.initializeDirectory(privateRoot)
            requireNotNull(NovelDownloadStore.saveChapter("Novel", novelUrl, "", listOf(chapter), chapter,
                DetailedChapter("Chapter", listOf(TextComponent("正文")), null, null),
                Authorization("", "", "www.esjzone.cc")))
            val record = NovelDownloadStore.manifest(novelUrl)!!.chapters.single()
            val stored = NovelDownloadStore.chapterContent(novelUrl, record)!!
            val sourceBook = privateRoot.listFiles()!!.single()
            val imageUrl = "https://example.invalid/fixture.png"
            val imagePath = "images/image-${"a".repeat(64)}.png"
            val imageBytes = android.util.Base64.decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aDAAAAAAASUVORK5CYII=",
                android.util.Base64.DEFAULT)
            File(sourceBook, "images").mkdirs()
            File(sourceBook, imagePath).writeBytes(imageBytes)
            val illustrated = stored.copy(components = stored.components +
                DownloadedComponent("image", imageUrl, imagePath, "image/png"),
                body = ChapterBody.from(listOf(TextComponent("正文"), ImageComponent(imageUrl))))
            StorageFile(File(sourceBook, record.fileName)).writeCompressedText { com.google.gson.Gson().toJson(illustrated, it) }
            NovelDownloadStore.reindex()
            NovelDownloadStore.updateCommonPassword(novelUrl, "fixture-only")
            val migration = NovelDownloadStore.migrateStorage(downloads) { }
            assertTrue(migration.books.single().isDirectory)
            val book = downloads.listFiles()!!.single()
            val manifest = StorageFile(book, "manifest.json").inputStream().reader().use {
                JsonParser.parseReader(it).asJsonObject
            }
            assertFalse(manifest.has("commonPassword"))
            assertEquals("fixture-only", NovelDownloadStore.manifest(novelUrl)!!.commonPassword)
            val fresh = StorageFile(StorageFile.fromTree(context, treeUri), shared.name + "/downloads")
            NovelDownloadStore.reindex(fresh)
            assertEquals(1, NovelDownloadStore.listDownloadedNovels().size)
            val restoredImage = NovelDownloadStore.readChapter(chapter.url)!!.content.filterIsInstance<ImageComponent>().single()
            assertTrue(restoredImage.url.startsWith("content://"))
            assertArrayEquals(imageBytes, context.contentResolver.openInputStream(android.net.Uri.parse(restoredImage.url))!!.use { it.readBytes() })
            val saved = requireNotNull(NovelDownloadStore.manifest(novelUrl))
            val epub = ByteArrayOutputStream()
            NovelExporter.exportEpub(saved, { NovelDownloadStore.chapterContent(novelUrl, it) }, epub,
                imageLoader = { NovelDownloadStore.imageFile(novelUrl, it) })
            assertTrue(epub.size() > 0)
            java.util.zip.ZipInputStream(epub.toByteArray().inputStream()).use { zip ->
                var found = false
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "OEBPS/images/image-1.png") {
                        assertArrayEquals(imageBytes, zip.readBytes())
                        found = true
                    }
                    entry = zip.nextEntry
                }
                assertTrue(found)
            }
            NovelDownloadStore.exportBackup(backup)
            context.getSharedPreferences("download_secrets", Context.MODE_PRIVATE).edit().remove(secretKey).commit()
            NovelDownloadStore.restoreStorage(StorageFile(shared, "restored").apply { check(mkdirs()) })
            NovelDownloadStore.importBackup(backup)
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
            assertNull(NovelDownloadStore.manifest(novelUrl)!!.commonPassword)
        } finally {
            context.getSharedPreferences("download_secrets", Context.MODE_PRIVATE).edit().remove(secretKey).commit()
            kotlinx.coroutines.runBlocking { EsjzoneApplication.instance.container.initializeDownloads() }
            NovelDownloadStore.restoreStorage(EsjzoneApplication.instance.container.storage.downloads())
            shared.deleteRecursively()
            privateRoot.deleteRecursively()
            backup.deleteRecursively()
        }
    }

    @Test fun publicLegacyNamesRemainReadableWhenRenameIsDeniedAndDeletedRootsRegainMediaMarker() {
        val privateRoot = File(context.cacheDir, "storage-${UUID.randomUUID()}").apply { mkdirs() }
        val shared = StorageFile(StorageFile.fromTree(context, treeUri), "case-${UUID.randomUUID()}")
            .apply { check(mkdirs()) }
        val downloads = StorageFile(shared, "downloads")
        val novelId = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).toString()
        val novelUrl = "https://www.esjzone.cc/detail/$novelId.html"
        val chapter = Chapter("Chapter", "https://www.esjzone.cc/forum/$novelId/1.html", false)
        fun freshDownloads() = StorageFile(StorageFile.fromTree(context, treeUri), "${shared.name}/downloads")
        fun save() = requireNotNull(NovelDownloadStore.saveChapter("可读书名", novelUrl, "", listOf(chapter), chapter,
            DetailedChapter("Chapter", listOf(TextComponent("保存的正文")), null, null),
            Authorization("", "", "www.esjzone.cc")))
        try {
            NovelDownloadStore.initialize(context)
            NovelDownloadStore.initializeDirectory(privateRoot)
            save()
            NovelDownloadStore.migrateStorage(downloads) { }
            val named = downloads.listFiles()!!.single { it.isDirectory }
            assertEquals("可读书名", named.name)
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest("/detail/$novelId.html".toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val legacy = StorageFile(downloads, hash)
            check(named.renameTo(legacy))
            NovelDownloadStore.restoreStorage(StorageFile(privateRoot))
            NovelDownloadStore.restoreStorage(freshDownloads())
            configure(shared, "security", "rename")
            try {
                assertNotNull(NovelDownloadStore.readChapter(chapter.url))
                assertTrue(legacy.isDirectory)
            } finally { configure(shared) }
            NovelDownloadStore.restoreStorage(StorageFile(privateRoot))
            val reopened = freshDownloads()
            NovelDownloadStore.restoreStorage(reopened)
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
            assertTrue(StorageFile(reopened, "可读书名").isDirectory)
            assertFalse(StorageFile(reopened, hash).exists())
            check(reopened.deleteRecursively())
            save()
            assertTrue(StorageFile(reopened, ".nomedia").isFile)
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
        } finally {
            configure(shared)
            kotlinx.coroutines.runBlocking { EsjzoneApplication.instance.container.initializeDownloads() }
            NovelDownloadStore.restoreStorage(EsjzoneApplication.instance.container.storage.downloads())
            shared.deleteRecursively()
            privateRoot.deleteRecursively()
        }
    }

    @Test fun replacingDocumentKeepsPublishedContentAndReadableDocumentUri() {
        val directory = StorageFile(StorageFile.fromTree(context, treeUri), "case-${UUID.randomUUID()}")
            .apply { check(mkdirs()) }
        try {
            val published = StorageFile(directory, "manifest.json").apply { writeText("old") }
            val pending = StorageFile.createTempFile("pending-", ".tmp", directory).apply { writeText("new") }
            published.replaceWith(pending)
            assertEquals("new", published.inputStream().reader().use { it.readText() })
            assertEquals("content", published.uri.scheme)
            assertEquals("new", context.contentResolver.openInputStream(published.uri)!!.reader().use { it.readText() })
            assertFalse(pending.exists())
            // Simulate process death after moving the previous manifest aside.
            val previous = StorageFile(directory, "manifest.json.previous.tmp")
            check(published.renameTo(previous))
            val reopened = StorageFile(StorageFile.fromTree(context, treeUri), directory.name + "/manifest.json")
            reopened.recoverReplacement()
            assertEquals("new", reopened.inputStream().reader().use { it.readText() })
            assertFalse(previous.exists())
        } finally { directory.deleteRecursively() }
    }

    @Test fun migrationRecoversInterruptedChapterReplacementBeforeRemovingOriginals() {
        val privateRoot = File(context.cacheDir, "storage-${UUID.randomUUID()}").apply { mkdirs() }
        val restored = File(context.cacheDir, "storage-restored-${UUID.randomUUID()}").apply { mkdirs() }
        val shared = StorageFile(StorageFile.fromTree(context, treeUri), "case-${UUID.randomUUID()}")
            .apply { check(mkdirs()) }
        val downloads = StorageFile(shared, "downloads")
        val novelId = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).toString()
        val novelUrl = "https://www.esjzone.cc/detail/$novelId.html"
        val chapter = Chapter("Chapter", "https://www.esjzone.cc/forum/$novelId/1.html", false)
        try {
            NovelDownloadStore.initialize(context)
            NovelDownloadStore.initializeDirectory(privateRoot)
            requireNotNull(NovelDownloadStore.saveChapter("Novel", novelUrl, "", listOf(chapter), chapter,
                DetailedChapter("Chapter", listOf(TextComponent("可恢复正文")), null, null),
                Authorization("", "", "www.esjzone.cc")))
            NovelDownloadStore.migrateStorage(downloads) { }
            val record = NovelDownloadStore.manifest(novelUrl)!!.chapters.single()
            val book = downloads.listFiles()!!.single()
            val published = StorageFile(book, record.fileName)
            val previous = StorageFile(book, "${record.fileName}.previous.tmp")
            check(published.renameTo(previous))
            configure(shared, "security", "rename")
            try {
                // readJson must not turn a failed replacement recovery into an absent chapter.
                assertThrows(StorageAccessException::class.java) { NovelDownloadStore.readChapter(chapter.url) }
            } finally { configure(shared) }
            assertTrue(previous.exists())
            // A fresh tree models process restart; no successful chapter read precedes migration.
            val reopened = StorageFile(StorageFile.fromTree(context, treeUri), shared.name + "/downloads")
            NovelDownloadStore.restoreStorage(reopened)
            val migration = NovelDownloadStore.migrateStorage(StorageFile(restored)) { }
            assertEquals(1, migration.books.size)
            val recoveredBook = migration.books.single()
            assertTrue(StorageFile(recoveredBook, record.fileName).isFile)
            assertFalse(StorageFile(recoveredBook, previous.name).exists())
            migration.books.forEach { check(it.deleteRecursively()) }
            assertEquals("可恢复正文", NovelDownloadStore.readChapter(chapter.url)!!.content
                .filterIsInstance<TextComponent>().single().text)
            assertTrue(File(restored.listFiles()!!.single(), record.fileName).isFile)
        } finally {
            configure(shared)
            kotlinx.coroutines.runBlocking { EsjzoneApplication.instance.container.initializeDownloads() }
            NovelDownloadStore.restoreStorage(EsjzoneApplication.instance.container.storage.downloads())
            shared.deleteRecursively()
            privateRoot.deleteRecursively()
            restored.deleteRecursively()
        }
    }

    @Test fun migrationPublishesWholeBooksWithoutPerFileRenames() {
        val privateRoot = File(context.cacheDir, "storage-${UUID.randomUUID()}").apply { mkdirs() }
        val shared = StorageFile(StorageFile.fromTree(context, treeUri), "case-${UUID.randomUUID()}")
            .apply { check(mkdirs()) }
        val novelId = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).toString()
        val novelUrl = "https://www.esjzone.cc/detail/$novelId.html"
        val chapter = Chapter("Chapter", "https://www.esjzone.cc/forum/$novelId/1.html", false)
        try {
            NovelDownloadStore.initialize(context)
            NovelDownloadStore.initializeDirectory(privateRoot)
            requireNotNull(NovelDownloadStore.saveChapter("Novel", novelUrl, "", listOf(chapter), chapter,
                DetailedChapter("Chapter", listOf(TextComponent("Saved text")), null, null),
                Authorization("", "", "www.esjzone.cc")))
            val book = privateRoot.listFiles()!!.single()
            File(book, "images").mkdirs()
            repeat(20) { File(book, "images/fixture-$it.bin").writeBytes(ByteArray(64) { it.toByte() }) }
            configure(shared, "reject-file-rename", "rename")
            val migration = NovelDownloadStore.migrateStorage(StorageFile(shared, "downloads")) { }
            assertEquals(1, migration.books.size)
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
            assertEquals(20, StorageFile(StorageFile(shared, "downloads"),
                migration.targets.getValue(book.name) + "/images").entries().size)
        } finally {
            configure(shared)
            // Restore the application store explicitly after this isolated fixture.
            NovelDownloadStore.restoreStorage(EsjzoneApplication.instance.container.storage.downloads())
            shared.deleteRecursively()
            privateRoot.deleteRecursively()
        }
    }

}
