package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.storage.StorageFile
import com.breakyuna.esjzone.storage.StorageMigrationBusyException
import com.breakyuna.esjzone.storage.StorageTransferException
import com.breakyuna.esjzone.storage.StorageTransferFailureReason
import com.breakyuna.esjzone.storage.StorageTransferPhase
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class StorageMigrationTest {
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

    @Test fun inventoryPublishesBooksBeforeSizesAndMetadataOnlyDoesNotPublishUnknownSizesToCache() {
        val root = Files.createTempDirectory("inventory-metadata-").toFile()
        try {
            save(root)
            val book = root.listFiles()!!.single()
            File(book, "images").mkdirs()
            File(book, "images/fixture.jpg").writeBytes(ByteArray(4096))
            val metadata = NovelDownloadStore.listDownloadedNovels(calculateSizes = false)
            assertEquals(1, metadata.single().downloadedChapterCount)
            assertEquals(-1L, metadata.single().storageBytes)
            var preliminary: Long? = null
            val complete = NovelDownloadStore.listDownloadedNovels(onMetadata = {
                preliminary = it.single().storageBytes
            })
            assertEquals(-1L, requireNotNull(preliminary))
            assertEquals(book.walkTopDown().filter { it.isFile }.sumOf { it.length() }, complete.single().storageBytes)
            assertEquals(complete, NovelDownloadStore.listDownloadedNovels())
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationConflictingManifestAndNestedDestinationKeepOriginalReadable() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        try {
            save(source)
            val manifestData = requireNotNull(NovelDownloadStore.manifest(novel))
            File(source.listFiles()!!.single(), "fixture.bin").writeBytes(ByteArray(256 * 1024))
            assertThrows(CancellationException::class.java) {
                NovelDownloadStore.migrateStorage(StorageFile(target)) {
                    if (target.walkTopDown().any { it.isFile && it.length() >= 64 * 1024 }) {
                        throw CancellationException("fixture")
                    }
                }
            }
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))

            val book = File(target, source.listFiles()!!.single().name).apply { mkdirs() }
            for (conflicting in listOf(
                com.google.gson.Gson().toJson(manifestData.copy(name = "Conflicting copy")),
                com.google.gson.Gson().toJson(manifestData.copy(downloadedAt = manifestData.downloadedAt + 1))
            )) {
                val manifest = File(book, "manifest.json").apply { writeText(conflicting) }
                val error = assertThrows(StorageTransferException::class.java) {
                    NovelDownloadStore.migrateStorage(StorageFile(target)) { }
                }
                assertEquals(StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT, error.failure.reason)
                assertEquals("downloads/${book.name}/manifest.json", error.failure.item)
                assertEquals(conflicting, manifest.readText())
            }
            assertThrows(IllegalArgumentException::class.java) {
                NovelDownloadStore.migrateStorage(StorageFile(StorageFile(source.listFiles()!!.single()), "nested")) { }
            }
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
            val error = assertThrows(StorageTransferException::class.java) {
                target.copyFrom(StorageFile(source))
            }
            assertEquals(StorageTransferFailureReason.TARGET_FILE_CONFLICT, error.failure.reason)
            assertTrue(error.failure.item.endsWith("target.zip"))
            assertEquals("archive", File(root, "target.zip").readText())
            for (path in listOf("../outside", "/absolute", "images/../../outside")) {
                assertThrows(IllegalArgumentException::class.java) { StorageFile(StorageFile(root), path) }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun sameTitleFromAnotherNovelUsesSeparateDirectoryWithoutOverwriting() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        try {
            save(source)
            val manifest = NovelDownloadStore.manifest(novel)!!
            val existing = File(target, "Novel").apply { mkdirs() }
            val otherManifest = com.google.gson.Gson().toJson(manifest.copy(url = "/detail/99999.html",
                chapters = emptyList(), complete = false))
            File(existing, "manifest.json").writeText(otherManifest)
            val migration = NovelDownloadStore.migrateStorage(StorageFile(target)) { }
            assertNotEquals(existing.name, migration.targets.values.single())
            assertEquals(otherManifest, File(existing, "manifest.json").readText())
            assertEquals(2, target.listFiles()!!.size)
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
        } finally { source.deleteRecursively(); target.deleteRecursively() }
    }

    @Test fun damagedSourceManifestAndMissingSavedChaptersStopMigrationBeforePublishing() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        try {
            save(source)
            val manifest = NovelDownloadStore.manifest(novel)!!
            val book = source.listFiles()!!.single()
            val originalChapter = File(book, manifest.chapters.single().fileName)
            for (damaged in listOf("{", "null", com.google.gson.Gson().toJson(manifest.copy(version = 999)))) {
                File(book, "manifest.json").writeText(damaged)
                val error = assertThrows(StorageTransferException::class.java) {
                    NovelDownloadStore.migrateStorage(StorageFile(target)) { }
                }
                assertEquals(StorageTransferFailureReason.SOURCE_MANIFEST_INVALID, error.failure.reason)
                assertEquals("downloads/${book.name}/manifest.json", error.failure.item)
                assertTrue(originalChapter.isFile)
                assertTrue(target.listFiles()!!.isEmpty())
            }

            check(originalChapter.delete())
            for (downloaded in listOf(true, false)) {
                File(book, "manifest.json").writeText(com.google.gson.Gson().toJson(manifest.copy(
                    chapters = manifest.chapters.map { it.copy(downloaded = downloaded, bodyAvailable = true) }
                )))
                NovelDownloadStore.reindex()
                assertThrows(java.io.IOException::class.java) {
                    NovelDownloadStore.migrateStorage(StorageFile(target)) { }
                }
                assertEquals("Novel", NovelDownloadStore.manifest(novel)!!.name)
                assertFalse(target.walkTopDown().any { it.name == "manifest.json" })
            }
        } finally { source.deleteRecursively(); target.deleteRecursively() }
    }

    @Test fun readersRemainAvailableWhileMutationsPauseDuringCopyCommitAndPendingQueue() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val detail = DetailedNovel("Novel", novel, "", 0, 0, 0, "", "", "", emptyList(), false, false,
            NovelDescription(emptyList()), NovelChapterList(listOf(ChapterItem(chapter))))
        var checkedCopy = false
        try {
            save(source)
            assertNotNull(NovelDownloadStore.updateCommonPassword(detail, "fixture-only"))
            NovelDownloadStore.setStorageMigrationPending(true)
            assertNull(NovelDownloadStore.updateCommonPassword(detail, null))
            assertNull(NovelDownloadStore.saveChapter("Novel", novel, "", listOf(chapter), chapter,
                DetailedChapter("Chapter", listOf(TextComponent("Replacement text")), null, null)))
            assertEquals("fixture-only", NovelDownloadStore.manifest(novel)!!.commonPassword)
            NovelDownloadStore.finishStorageMigration()

            val migration = NovelDownloadStore.migrateStorage(StorageFile(target), holdWritesUntilCommit = true,
                onProgress = { progress ->
                    if (!checkedCopy && progress.phase == StorageTransferPhase.COPYING) {
                        checkedCopy = true
                        assertNotNull(executor.submit<DetailedChapter?> {
                            NovelDownloadStore.readChapter(chapter.url)
                        }.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        assertFalse(NovelDownloadStore.delete(novel))
                        assertThrows(StorageMigrationBusyException::class.java) {
                            NovelDownloadStore.updateCommonPassword(novel, null)
                        }
                    }
                }) { }
            assertTrue(checkedCopy)
            assertTrue(NovelDownloadStore.migrating.value)
            assertFalse(NovelDownloadStore.delete(novel))
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))

            NovelDownloadStore.restoreStorage(requireNotNull(migration.source))
            NovelDownloadStore.finishStorageMigration()
            assertFalse(NovelDownloadStore.migrating.value)
            assertNotNull(NovelDownloadStore.updateCommonPassword(detail, null))
            assertNull(NovelDownloadStore.manifest(novel)!!.commonPassword)
        } finally {
            NovelDownloadStore.finishStorageMigration()
            executor.shutdownNow()
            source.deleteRecursively()
            target.deleteRecursively()
        }
    }

    @Test fun interruptionAfterBookPublicationReusesValidatedBookWithoutCopyingItAgain() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        try {
            save(source)
            assertThrows(CancellationException::class.java) {
                NovelDownloadStore.migrateStorage(StorageFile(target), onProgress = { progress ->
                    if (progress.phase == StorageTransferPhase.VERIFYING &&
                        target.listFiles().orEmpty().any { File(it, "manifest.json").isFile }) {
                        throw CancellationException("fixture interruption after publication")
                    }
                }) { }
            }
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
            assertTrue(source.walkTopDown().any { it.name == "manifest.json" })
            var allocatedStaging = false
            val phases = mutableListOf<StorageTransferPhase>()
            NovelDownloadStore.migrateStorage(StorageFile(target),
                onProgress = { phases += it.phase }, stagingJournal = { allocatedStaging = true }) { }
            assertFalse(allocatedStaging)
            assertFalse(StorageTransferPhase.COPYING in phases)
            assertTrue(StorageTransferPhase.VERIFYING in phases)
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
        } finally { source.deleteRecursively(); target.deleteRecursively() }
    }

    @Test fun stagedMigrationAndRetryValidationRejectDamagedFilesBeforePublication() {
        for (damage in listOf("missing", "shortened", "extra")) {
            val source = Files.createTempDirectory("storage-source-").toFile()
            val target = Files.createTempDirectory("storage-target-").toFile()
            try {
                save(source)
                val record = NovelDownloadStore.manifest(novel)!!.chapters.single()
                var changed = false
                val error = assertThrows(StorageTransferException::class.java) {
                    NovelDownloadStore.migrateStorage(StorageFile(target), onProgress = { progress ->
                        if (!changed && progress.phase == StorageTransferPhase.VERIFYING) {
                            changed = true
                            val staging = target.listFiles()!!.single()
                            val saved = File(staging, record.fileName)
                            when (damage) {
                                "missing" -> {
                                    File(staging, "unrelated.bin").writeBytes(saved.readBytes())
                                    check(saved.delete())
                                }
                                "shortened" -> saved.writeBytes(byteArrayOf())
                                "extra" -> File(staging, "unrelated.bin").writeText("extra")
                            }
                        }
                    }) { }
                }
                assertEquals(StorageTransferFailureReason.VERIFICATION_FAILED, error.failure.reason)
                assertTrue(error.failure.item.startsWith("downloads/Novel/"))
                assertTrue(changed)
                assertTrue(target.listFiles()!!.isEmpty())
            } finally { source.deleteRecursively(); target.deleteRecursively() }
        }

        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        try {
            save(source)
            val record = NovelDownloadStore.manifest(novel)!!.chapters.single()
            NovelDownloadStore.migrateStorage(StorageFile(target)) { }
            val book = target.listFiles()!!.single()
            val saved = File(book, record.fileName)
            val content = saved.readBytes()
            val extra = File(book, "unrelated.bin").apply { writeBytes(content) }
            NovelDownloadStore.restoreStorage(StorageFile(source))
            check(saved.delete())
            val missing = assertThrows(StorageTransferException::class.java) {
                NovelDownloadStore.migrateStorage(StorageFile(target)) { }
            }
            assertEquals(StorageTransferFailureReason.TARGET_FILE_CONFLICT, missing.failure.reason)
            val changed = content.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
            saved.writeBytes(changed)
            assertEquals(StorageTransferFailureReason.TARGET_FILE_CONFLICT, assertThrows(StorageTransferException::class.java) {
                NovelDownloadStore.migrateStorage(StorageFile(target)) { }
            }.failure.reason)
            saved.writeBytes(content)
            NovelDownloadStore.migrateStorage(StorageFile(target),
                stagingJournal = { fail("A valid existing book must not be copied again") }) { }
            assertArrayEquals(content, extra.readBytes())
        } finally { source.deleteRecursively(); target.deleteRecursively() }
    }

    @Test fun resumedCleanupRevalidatesDestinationAndAcceptsPartiallyDeletedOriginals() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        try {
            save(source)
            val migration = NovelDownloadStore.migrateStorage(StorageFile(target)) { }
            val original = migration.books.single()
            val copied = StorageFile(StorageFile(target), requireNotNull(migration.targets[original.name]))
            val record = NovelDownloadStore.manifest(novel)!!.chapters.single()
            val destinationChapter = File(target, copied.name + "/" + record.fileName)
            val bytes = destinationChapter.readBytes()
            check(destinationChapter.delete())
            val error = assertThrows(StorageTransferException::class.java) {
                NovelDownloadStore.verifyMigratedBook(original, copied) { }
            }
            assertEquals(StorageTransferFailureReason.TARGET_FILE_CONFLICT, error.failure.reason)
            assertTrue(File(source, original.name + "/" + record.fileName).isFile)
            destinationChapter.writeBytes(bytes)
            // Model a process stopping after removing one source file but before updating the journal.
            check(File(source, original.name + "/" + record.fileName).delete())
            NovelDownloadStore.verifyMigratedBook(original, copied) { }
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
        } finally { source.deleteRecursively(); target.deleteRecursively() }
    }
}
