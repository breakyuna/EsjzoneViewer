package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.storage.StorageFile
import com.breakyuna.esjzone.storage.StorageTransferPhase
import com.breakyuna.esjzone.storage.StorageMigrationBusyException
import com.breakyuna.esjzone.storage.StorageTransferException
import com.breakyuna.esjzone.storage.StorageTransferFailureReason
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class StorageMigrationTest {
    @Test fun formerStorageRouteRestoresToInlineSettingsAfterProcessRecreation() {
        val navigator = com.breakyuna.esjzone.ui.navigation.AppNavigator(mutableListOf(), mutableMapOf())
        assertEquals(com.breakyuna.esjzone.ui.page.SettingsPage,
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
            val existing = com.google.gson.Gson().toJson(requireNotNull(NovelDownloadStore.manifest(novel))
                .copy(name = "Conflicting copy"))
            val manifest = File(book, "manifest.json").apply { writeText(existing) }
            try {
                NovelDownloadStore.migrateStorage(StorageFile(target)) { }
                fail("Expected destination conflict")
            } catch (error: StorageTransferException) {
                assertEquals(StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT, error.failure.reason)
                assertEquals("downloads/${book.name}/manifest.json", error.failure.item)
            }
            assertEquals(existing, manifest.readText())
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
            } catch (error: StorageTransferException) {
                assertEquals(StorageTransferFailureReason.TARGET_FILE_CONFLICT, error.failure.reason)
                assertTrue(error.failure.item.endsWith("target.zip"))
            }
            assertEquals("archive", File(root, "target.zip").readText())
            for (path in listOf("../outside", "/absolute", "images/../../outside")) {
                try { StorageFile(StorageFile(root), path); fail("Expected invalid relative path")
                } catch (_: IllegalArgumentException) { }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun previousTestCopyWithOnlyDifferentDownloadTimeReportsManifestConflict() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        try {
            save(source)
            val manifest = NovelDownloadStore.manifest(novel)!!
            NovelDownloadStore.migrateStorage(StorageFile(target)) { }
            val targetManifest = File(target.listFiles()!!.single(), "manifest.json")
            val previousCopy = com.google.gson.Gson().toJson(manifest.copy(downloadedAt = manifest.downloadedAt + 1))
            targetManifest.writeText(previousCopy)
            NovelDownloadStore.restoreStorage(StorageFile(source))
            val error = assertThrows(StorageTransferException::class.java) {
                NovelDownloadStore.migrateStorage(StorageFile(target)) { }
            }
            assertEquals(StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT, error.failure.reason)
            assertEquals("downloads/${targetManifest.parentFile!!.name}/manifest.json", error.failure.item)
            assertEquals(previousCopy, targetManifest.readText())
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
        } finally { source.deleteRecursively(); target.deleteRecursively() }
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

    @Test fun damagedSourceManifestStopsMigrationEvenWhenAValidManifestWasCached() {
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
                assertEquals(damaged, File(book, "manifest.json").readText())
            }
        } finally { source.deleteRecursively(); target.deleteRecursively() }
    }

    @Test fun reusedComparisonBuffersDetectTailDifferencesAndRespectCancellation() {
        val root = Files.createTempDirectory("storage-compare-").toFile()
        try {
            val first = File(root, "first.bin")
            val second = File(root, "second.bin")
            val a = StorageFile(first)
            val b = StorageFile(second)
            val buffer = ByteArray(64 * 1024)
            val otherBuffer = ByteArray(buffer.size)
            for (size in listOf(2 * buffer.size + 17, 3, 0)) {
                val content = ByteArray(size) { (it % 251).toByte() }
                first.writeBytes(content)
                second.writeBytes(content)
                assertTrue(a.sameContent(b, buffer = buffer, otherBuffer = otherBuffer))
                if (size > 0) {
                    content[size - 1] = (content.last().toInt() xor 1).toByte()
                    second.writeBytes(content)
                    assertFalse(a.sameContent(b, buffer = buffer, otherBuffer = otherBuffer))
                }
            }
            first.writeBytes(ByteArray(2 * buffer.size + 17))
            second.writeBytes(first.readBytes())
            var checks = 0
            assertThrows(CancellationException::class.java) {
                a.sameContent(b, checkActive = {
                    if (++checks == 3) throw CancellationException("fixture")
                }, buffer = buffer, otherBuffer = otherBuffer)
            }
            assertArrayEquals(first.readBytes(), second.readBytes())
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

    @Test fun readersRemainAvailableAndMutationsPauseDuringTheCopy() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        var checked = false
        try {
            save(source)
            NovelDownloadStore.migrateStorage(StorageFile(target), onProgress = { progress ->
                if (!checked && progress.phase == StorageTransferPhase.COPYING) {
                    checked = true
                    // A separate thread proves the migration callback does not retain ioLock.
                    assertNotNull(executor.submit<DetailedChapter?> {
                        NovelDownloadStore.readChapter(chapter.url)
                    }.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    assertFalse(NovelDownloadStore.delete(novel))
                    assertThrows(StorageMigrationBusyException::class.java) {
                        NovelDownloadStore.updateCommonPassword(novel, null)
                    }
                }
            }) { }
            assertTrue(checked)
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
            assertFalse(NovelDownloadStore.migrating.value)
        } finally { executor.shutdownNow(); source.deleteRecursively(); target.deleteRecursively() }
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

    @Test fun stagedMigrationRejectsMissingShortenedAndUnexpectedFilesBeforePublication() {
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
                                    // Same count and size, but the required path is absent.
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
                assertNotNull(NovelDownloadStore.readChapter(chapter.url))
            } finally { source.deleteRecursively(); target.deleteRecursively() }
        }
    }

    @Test fun retryValidationRejectsMissingAndChangedFilesButRetainsExistingExtras() {
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
            assertEquals("downloads/${book.name}/${record.fileName}", missing.failure.item)
            val changed = content.copyOf()
            changed[changed.lastIndex] = (changed.last().toInt() xor 1).toByte()
            saved.writeBytes(changed)
            val conflict = assertThrows(StorageTransferException::class.java) {
                NovelDownloadStore.migrateStorage(StorageFile(target)) { }
            }
            assertEquals(StorageTransferFailureReason.TARGET_FILE_CONFLICT, conflict.failure.reason)
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
            saved.writeBytes(content)
            NovelDownloadStore.migrateStorage(StorageFile(target),
                stagingJournal = { fail("A valid existing book must not be copied again") }) { }
            assertArrayEquals(content, extra.readBytes())
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
        } finally { source.deleteRecursively(); target.deleteRecursively() }
    }

    @Test fun writesStayPausedUntilTheLocationCommitAndCanResumeAfterward() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val target = Files.createTempDirectory("storage-target-").toFile()
        try {
            save(source)
            val migration = NovelDownloadStore.migrateStorage(StorageFile(target), holdWritesUntilCommit = true) { }
            assertTrue(NovelDownloadStore.migrating.value)
            assertFalse(NovelDownloadStore.delete(novel))
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
            // Simulate a rejected preference commit, then release the write barrier.
            NovelDownloadStore.restoreStorage(requireNotNull(migration.source))
            NovelDownloadStore.finishStorageMigration()
            assertFalse(NovelDownloadStore.migrating.value)
            assertNotNull(NovelDownloadStore.updateCommonPassword(novel, null))
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
        } finally {
            NovelDownloadStore.finishStorageMigration()
            source.deleteRecursively(); target.deleteRecursively()
        }
    }

    @Test fun queuedMigrationPreservesPasswordAndChapterWhenNovelDetailWritesArePaused() {
        val source = Files.createTempDirectory("storage-source-").toFile()
        val detail = DetailedNovel("Novel", novel, "", 0, 0, 0, "", "", "", emptyList(), false, false,
            NovelDescription(emptyList()), NovelChapterList(listOf(ChapterItem(chapter))))
        try {
            save(source)
            assertNotNull(NovelDownloadStore.updateCommonPassword(detail, "fixture-only"))
            NovelDownloadStore.setStorageMigrationPending(true)
            assertNull(NovelDownloadStore.updateCommonPassword(detail, null))
            assertNull(NovelDownloadStore.saveChapter("Novel", novel, "", listOf(chapter), chapter,
                DetailedChapter("Chapter", listOf(TextComponent("Replacement text")), null, null)))
            assertEquals("fixture-only", NovelDownloadStore.manifest(novel)!!.commonPassword)
            assertEquals("Saved text", NovelDownloadStore.chapterContent(novel,
                NovelDownloadStore.manifest(novel)!!.chapters.single())!!.components.single().value)
            NovelDownloadStore.finishStorageMigration()
            assertNotNull(NovelDownloadStore.updateCommonPassword(detail, null))
            assertNull(NovelDownloadStore.manifest(novel)!!.commonPassword)
        } finally {
            NovelDownloadStore.finishStorageMigration()
            source.deleteRecursively()
        }
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
