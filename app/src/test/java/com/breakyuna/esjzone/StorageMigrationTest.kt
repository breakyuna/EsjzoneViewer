package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.storage.StorageFile
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class StorageMigrationTest {
    @Test fun storageSettingsRouteRestoresAfterProcessRecreation() {
        val navigator = com.breakyuna.esjzone.ui.navigation.AppNavigator(mutableListOf(), mutableMapOf())
        assertEquals(com.breakyuna.esjzone.ui.page.StorageLocationPage,
            navigator.destination(com.breakyuna.esjzone.ui.navigation.LegacyRoute.Static("StorageLocationPage")))
    }
    private val novel = "https://www.esjzone.cc/detail/81234.html"
    private val chapter = Chapter("Chapter", "https://www.esjzone.cc/forum/81234/1.html#part", false)
    private fun save(root: File) {
        NovelDownloadStore.initializeDirectory(root)
        requireNotNull(NovelDownloadStore.saveChapter("Novel", novel, "", listOf(chapter), chapter,
            DetailedChapter("Chapter", listOf(TextComponent("Saved text")), null, null),
            Authorization("", "", "www.esjzone.cc")))
    }

    @Test fun migrationPreservesMirrorIdentityAndChapterContentAndLeavesOriginalUntilCommit() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        try {
            save(source)
            val migration = NovelDownloadStore.migrateStorage(StorageFile(target)) { }
            assertEquals(1, migration.books.size)
            assertTrue(source.walkTopDown().any { it.name == "manifest.json" })
            assertEquals("Novel", NovelDownloadStore.manifest("/detail/81234.html")!!.name)
            assertEquals("Saved text", NovelDownloadStore.chapterContent(novel,
                NovelDownloadStore.manifest(novel)!!.chapters.single())!!.components.single().value)
            assertNotNull(NovelDownloadStore.readChapter(chapter.url.replace("www.esjzone.cc", "www.esjzone.one")))
            assertNull(NovelDownloadStore.readChapter(chapter.url.substringBefore('#')))
            // The caller may roll back a failed location preference write without losing the source.
            NovelDownloadStore.restoreStorage(requireNotNull(migration.source))
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
        } finally { source.deleteRecursively(); target.deleteRecursively() }
    }

    @Test fun cancellationAndConflictingDestinationKeepOriginalReadable() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        try {
            save(source)
            File(source.listFiles()!!.single(), "fixture.bin").writeBytes(ByteArray(256 * 1024))
            try {
                NovelDownloadStore.migrateStorage(StorageFile(target)) {
                    if (target.walkTopDown().any { it.isFile && it.length() >= 64 * 1024 }) {
                        throw CancellationException("fixture")
                    }
                }
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
            val book = File(target, source.listFiles()!!.single().name).apply { mkdirs() }
            val manifest = File(book, "manifest.json").apply { writeText("existing content") }
            try {
                NovelDownloadStore.migrateStorage(StorageFile(target)) { }
                fail("Expected destination conflict")
            } catch (_: IllegalArgumentException) { }
            assertEquals("existing content", manifest.readText())
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
            try {
                NovelDownloadStore.migrateStorage(StorageFile(StorageFile(source.listFiles()!!.single()), "nested")) { }
                fail("Expected overlapping migration rejection")
            } catch (_: IllegalArgumentException) { }
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
        } finally { source.deleteRecursively(); target.deleteRecursively() }
    }

    @Test fun archiveCopyRetriesAcceptIdenticalFilesAndRejectDifferentContent() {
        val root = Files.createTempDirectory("storage-copy-").toFile()
        try {
            val source = File(root, "source.zip").apply { writeText("archive") }
            val target = StorageFile(File(root, "target.zip"))
            target.copyFrom(StorageFile(source))
            target.copyFrom(StorageFile(source))
            source.writeText("changed")
            try { target.copyFrom(StorageFile(source)); fail("Expected conflict")
            } catch (_: IllegalArgumentException) { }
            assertEquals("archive", File(root, "target.zip").readText())
            for (path in listOf("../outside", "/absolute", "images/../../outside")) {
                try { StorageFile(StorageFile(root), path); fail("Expected invalid relative path")
                } catch (_: IllegalArgumentException) { }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun migrationRejectsMissingSavedChaptersBeforePublishingTheNewLocation() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        try {
            save(source)
            val manifest = requireNotNull(NovelDownloadStore.manifest(novel))
            val book = source.listFiles()!!.single()
            check(File(book, manifest.chapters.single().fileName).delete())
            for (downloaded in listOf(true, false)) {
                File(book, "manifest.json").writeText(com.google.gson.Gson().toJson(manifest.copy(
                    chapters = manifest.chapters.map { it.copy(downloaded = downloaded, bodyAvailable = true) }
                )))
                NovelDownloadStore.reindex()
                assertThrows(java.io.IOException::class.java) {
                    NovelDownloadStore.migrateStorage(StorageFile(target)) { }
                }
                assertEquals("Novel", NovelDownloadStore.manifest(novel)!!.name)
                assertTrue(File(book, "manifest.json").isFile)
                assertFalse(target.walkTopDown().any { it.name == "manifest.json" })
            }
        } finally { source.deleteRecursively(); target.deleteRecursively() }
    }
}
