package com.breakyuna.esjzone

import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.data.reader.withStructuredBody
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.domain.reader.ReaderSearchHit
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.StructuredChapterCache
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.*
import com.breakyuna.esjzone.offline.*
import com.breakyuna.esjzone.storage.StorageMigrationBusyException
import com.breakyuna.esjzone.ui.reader.toReaderDocument
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import okhttp3.OkHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StructuredDownloadStoreTest {
    private val auth = Authorization("", "", "www.esjzone.cc")
    private val novelUrl = "https://www.esjzone.cc/detail/9001.html"
    private fun chapter(number: Int) = Chapter("Chapter $number", "https://www.esjzone.cc/forum/9001/$number.html", false)
    private fun detail(number: Int) = DetailedChapter("Chapter $number", listOf(TextComponent("正文 $number")), null, null,
        "<p>正文 $number</p>", chapter(number).url)
    private fun save(number: Int, order: List<Chapter>) = requireNotNull(NovelDownloadStore.saveChapter(
        "Novel", novelUrl, "", order, chapter(number), detail(number), auth))
    private fun novel(order: List<Chapter>) = DetailedNovel("Novel", novelUrl, "", 0, 0, 0, "", "", "/forum/9001/1.html",
        emptyList(), false, false, NovelDescription(emptyList()), NovelChapterList(order.map(::ChapterItem)))

    private fun withStorage(block: (File) -> Unit) {
        val root = Files.createTempDirectory("structured-download-").toFile()
        NovelDownloadStore.initializeDirectory(root)
        try { block(root) } finally { root.deleteRecursively() }
    }

    @Test
    fun historyProgressReusesUnchangedBooksAndRefreshesAfterCatalogChanges() = withStorage { root ->
        val order = listOf(chapter(1), chapter(2))
        val saved = save(1, order)
        File(root.listFiles()!!.single(), "manifest.json").writeText(Gson().toJson(saved.copy(catalogComplete = true)))
        NovelDownloadStore.initializeDirectory(root)
        val row = LocalReadingActivity("local:9001", "9001", "Novel", novelUrl,
            chapter(1).url, "Chapter 1", 0, 2, 0.5f, 1L, 2L, 0L)
        val initial = NovelDownloadStore.refreshProgress(row)
        assertEquals(0.25f, initial.bookProgress!!, 0.0001f)
        assertSame(initial, NovelDownloadStore.refreshProgress(row))
        val other = Chapter("Other", "https://www.esjzone.cc/forum/9002/1.html", false)
        NovelDownloadStore.saveChapter("Other", "/detail/9002.html", "", listOf(other), other,
            DetailedChapter("Other", listOf(TextComponent("other")), null, null), auth)
        assertSame(initial, NovelDownloadStore.refreshProgress(row))

        save(2, order + chapter(3))
        val expanded = NovelDownloadStore.refreshProgress(row)
        assertEquals(3, expanded.totalChapters)
        assertEquals(1f / 6f, expanded.bookProgress!!, 0.0001f)
        assertEquals(row.anchor, expanded.anchor)
        val moved = NovelDownloadStore.refreshProgress(row.copy(chapterProgress = 1f))
        assertEquals(1f / 3f, moved.bookProgress!!, 0.0001f)
        assertTrue(NovelDownloadStore.delete(novelUrl))
        assertEquals(row, NovelDownloadStore.refreshProgress(row))
    }

    @Test
    fun reindexDuringDownloadPreservesTheActiveChapterWriteGuard() = withStorage { root ->
        val cache = Files.createTempDirectory("reindex-cache-").toFile()
        PageCache.initializeDirectory(cache)
        try {
            val key = EsjzoneClient.novelDetailCacheKey(auth, chapter(1).url)
            PageCache.write(key, "fixture")
            requireNotNull(StructuredChapterCache.write(key, "fixture", detail(1)))
            var rescanned = false
            val manifest = runBlocking {
                NovelDownloadStore.download(auth, novel(listOf(chapter(1)))) {
                    if (!rescanned) {
                        rescanned = true
                        // Use a new handle for the same location, as UserStorage does.
                        NovelDownloadStore.reindex(com.breakyuna.esjzone.storage.StorageFile(root))
                        NovelDownloadStore.listDownloadedNovels()
                    }
                }
            }
            assertTrue(rescanned)
            assertTrue(manifest.complete)
            assertEquals("正文 1", NovelDownloadStore.readChapter(chapter(1).url)!!.content
                .filterIsInstance<TextComponent>().single().text)
            assertEquals(1, NovelDownloadStore.listDownloadedNovels().single().downloadedChapterCount)
        } finally { cache.deleteRecursively() }
    }

    @Test
    fun changingStorageDuringDownloadRejectsOldWritesAndAllowsRetryAtTheNewLocation() = withStorage { root ->
        val cache = Files.createTempDirectory("switch-cache-").toFile()
        val target = Files.createTempDirectory("switch-target-").toFile()
        PageCache.initializeDirectory(cache)
        try {
            val key = EsjzoneClient.novelDetailCacheKey(auth, chapter(1).url)
            PageCache.write(key, "fixture")
            requireNotNull(StructuredChapterCache.write(key, "fixture", detail(1)))
            // Storage switches are retryable; WorkManager must resume at the current location.
            assertThrows(StorageMigrationBusyException::class.java) {
                runBlocking {
                    NovelDownloadStore.download(auth, novel(listOf(chapter(1)))) {
                        NovelDownloadStore.restoreStorage(com.breakyuna.esjzone.storage.StorageFile(target))
                    }
                }
            }
            assertNull(NovelDownloadStore.manifest(novelUrl))
            assertFalse(root.walkTopDown().any { it.name.startsWith("chapter-") })
            assertTrue(runBlocking { NovelDownloadStore.download(auth, novel(listOf(chapter(1)))) }.complete)
            assertNotNull(NovelDownloadStore.readChapter(chapter(1).url))
            assertTrue(target.walkTopDown().any { it.name.startsWith("chapter-") })
            assertFalse(root.walkTopDown().any { it.name.startsWith("chapter-") })
        } finally { cache.deleteRecursively(); target.deleteRecursively() }
    }

    @Test
    fun backupReleasesTheReaderLockBeforeCopyingSnapshotFiles() = withStorage {
        save(1, listOf(chapter(1)))
        val snapshots = Files.createTempDirectory("readable-backup-").toFile()
        val executor = Executors.newSingleThreadExecutor()
        var checked = false
        try {
            NovelDownloadStore.exportBackup(snapshots, checkActive = {
                if (!checked && snapshots.listFiles().orEmpty().any(File::isDirectory)) {
                    checked = true
                    assertNotNull(executor.submit<DetailedChapter?> {
                        NovelDownloadStore.readChapter(chapter(1).url)
                    }.get(5, TimeUnit.SECONDS))
                }
            })
            assertTrue(checked)
            assertTrue(snapshots.walkTopDown().any { it.name == "manifest.json" })
        } finally { executor.shutdownNow(); snapshots.deleteRecursively() }
    }

    @Test
    fun storageSwitchOrDeletionCannotPublishAnIncompleteBackupSnapshot() = withStorage { root ->
        val other = Files.createTempDirectory("backup-other-").toFile()
        val snapshots = Files.createTempDirectory("invalidated-backup-").toFile()
        try {
            for (switchStorage in listOf(true, false)) {
                NovelDownloadStore.initializeDirectory(root)
                save(1, listOf(chapter(1)))
                var changed = false
                var published = false
                assertThrows(IllegalStateException::class.java) {
                    NovelDownloadStore.exportBackup(snapshots, checkActive = {
                        if (!changed && snapshots.listFiles().orEmpty().any(File::isDirectory)) {
                            changed = true
                            if (switchStorage) NovelDownloadStore.initializeDirectory(other)
                            else assertTrue(NovelDownloadStore.delete(novelUrl))
                        }
                    }, onNovelSnapshot = { published = true })
                }
                assertTrue(changed)
                assertFalse(published)
                assertFalse(snapshots.walkTopDown().any { it.name == "manifest.json" })
                snapshots.deleteRecursively()
            }
        } finally {
            NovelDownloadStore.initializeDirectory(root)
            other.deleteRecursively()
            snapshots.deleteRecursively()
        }
    }

    @Test
    fun cancelledBackupImportRemovesUncommittedNovelAndKeepsStoreUsable() = withStorage { root ->
        save(1, listOf(chapter(1)))
        val backup = Files.createTempDirectory("cancelled-backup-").toFile()
        val restored = Files.createTempDirectory("cancelled-restore-").toFile()
        try {
            NovelDownloadStore.exportBackup(backup)
            val snapshot = backup.listFiles()!!.single()
            File(snapshot, "large-image.bin").writeBytes(ByteArray(256 * 1024))
            NovelDownloadStore.initializeDirectory(restored)
            try {
                NovelDownloadStore.importBackup(backup) {
                    if (restored.walkTopDown().any { it.isFile && it.length() >= 64 * 1024 }) {
                        throw kotlinx.coroutines.CancellationException("Cancelled fixture transfer")
                    }
                }
                fail("Expected cancellation during copy")
            } catch (_: kotlinx.coroutines.CancellationException) { }
            assertTrue(restored.listFiles()!!.isEmpty())
            assertNull(NovelDownloadStore.manifest(novelUrl))
            NovelDownloadStore.importBackup(backup)
            assertNotNull(NovelDownloadStore.readChapter(chapter(1).url))
        } finally {
            backup.deleteRecursively()
            restored.deleteRecursively()
            NovelDownloadStore.initializeDirectory(root)
        }
    }

    @Test
    fun deletingAbsentOrAlreadyDeletedNovelSucceedsWithoutCreatingFiles() = withStorage { root ->
        assertTrue(NovelDownloadStore.delete(novelUrl))
        assertTrue(root.listFiles()!!.isEmpty())
        save(1, listOf(chapter(1)))
        assertTrue(NovelDownloadStore.delete(novelUrl))
        assertTrue(NovelDownloadStore.delete(novelUrl))
        assertNull(NovelDownloadStore.manifest(novelUrl))
        assertTrue(root.listFiles()!!.isEmpty())
    }

    @Test
    fun resumedUnlockSkipsCompletedTargetsAndLeavesUnselectedPasswordChaptersAlone() = withStorage { root ->
        val order = listOf(chapter(1), chapter(2))
        val saved = save(1, order)
        val manifestFile = File(root.listFiles()!!.single(), "manifest.json")
        manifestFile.writeText(Gson().toJson(saved.copy(commonPassword = "fixture", chapters =
            saved.chapters.map { if (it.index == 1) it.copy(requiresPassword = true) else it })))
        NovelDownloadStore.initializeDirectory(root)
        val progress = mutableListOf<DownloadProgress>()

        val result = runBlocking {
            NovelDownloadStore.unlockChaptersWithCommonPassword(auth, novelUrl,
                setOf("/forum/9001/1.html"), "https://www.esjzone.one/") { progress += it }
        }

        assertEquals(1, result.totalCount)
        assertEquals(1, result.unlockedCount)
        assertEquals(0, result.failedCount)
        assertEquals(listOf(DownloadProgress(1, 1, "")), progress)
        val restored = requireNotNull(result.updatedManifest)
        assertFalse(restored.complete)
        assertEquals(listOf(chapter(2).url), restored.pendingPasswordChapters.map { it.url })
        assertEquals("fixture", restored.commonPassword)
        assertEquals("正文 1", NovelDownloadStore.readChapter(chapter(1).url)!!.body!!.readerBlocks().single().sourceText())
    }

    @Test
    fun unlockRejectsMissingQueuedChaptersWithoutExpandingToOtherChapters() = withStorage {
        save(1, listOf(chapter(1)))
        NovelDownloadStore.updateCommonPassword(novelUrl, "fixture")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                NovelDownloadStore.unlockChaptersWithCommonPassword(auth, novelUrl,
                    setOf(chapter(2).url), "https://www.esjzone.one/")
            }
        }
        assertEquals(1, NovelDownloadStore.manifest(novelUrl)!!.chapters.size)
    }

    @Test
    fun committedOfflineChapterReclaimsMatchingSourceCacheAndKeepsReadableBody() = withStorage {
        val cache = Files.createTempDirectory("saved-chapter-cache-").toFile()
        PageCache.initializeDirectory(cache)
        try {
            val source = detail(1)
            val key = "fixture-account|chapter-1"
            val html = "<html><p>正文 1</p></html>"
            PageCache.write(key, html)
            val reference = requireNotNull(StructuredChapterCache.write(key, html, source))
            val manifest = requireNotNull(NovelDownloadStore.saveChapter("Novel", novelUrl, "",
                listOf(chapter(1)), chapter(1), source.copy(sourceCacheReference = reference), auth))
            assertTrue(manifest.chapters.single().downloaded)
            assertEquals(0, PageCache.stats().entryCount)
            val offline = requireNotNull(NovelDownloadStore.readChapter(chapter(1).url))
            assertEquals(source.contentHtml, offline.contentHtml)
            assertEquals(source.withStructuredBody().body, offline.body)
        } finally { cache.deleteRecursively() }
    }

    @Test
    fun savingAnOlderChapterDoesNotReclaimANewerResponse() = withStorage {
        val cache = Files.createTempDirectory("newer-chapter-cache-").toFile()
        PageCache.initializeDirectory(cache)
        try {
            val source = detail(1)
            val key = "fixture-account|chapter-1"
            PageCache.write(key, "old")
            val reference = requireNotNull(StructuredChapterCache.write(key, "old", source))
            PageCache.write(key, "new")
            NovelDownloadStore.saveChapter("Novel", novelUrl, "", listOf(chapter(1)), chapter(1),
                source.copy(sourceCacheReference = reference), auth)
            assertEquals("new", PageCache.readStale(key))
            assertNotNull(NovelDownloadStore.readChapter(chapter(1).url))
        } finally { cache.deleteRecursively() }
    }

    @Test
    fun addingChaptersKeepsExistingBodyFilesAndIncrementalDownloadSkipsNetwork() = withStorage { root ->
        val oldOrder = (1..10).map(::chapter)
        oldOrder.forEachIndexed { index, _ -> save(index + 1, oldOrder) }
        val oldFiles = root.walkTopDown().filter { it.isFile && it.name.startsWith("chapter-") }
            .associate { it.path to it.readBytes().toList() }
        val order = (1..12).map(::chapter)
        save(11, order)
        save(12, order)
        runBlocking { NovelDownloadStore.download(auth, novel(order), selectedChapterUrls = order.map { it.url }.toSet()) }
        val manifest = requireNotNull(NovelDownloadStore.manifest(novelUrl))
        assertEquals(12, manifest.chapters.size)
        assertTrue(manifest.complete)
        assertTrue(manifest.chapters.all { it.downloaded && it.textLength != null })
        oldFiles.forEach { (path, bytes) -> assertEquals(bytes, File(path).readBytes().toList()) }
        assertEquals(12, root.walkTopDown().count { it.isFile && it.name.startsWith("chapter-") })
        assertEquals("正文 1", NovelDownloadStore.readChapter(chapter(1).url)!!.toReaderDocument(chapter(1)).blocks.single().let {
            it.sourceText()
        })
    }

    @Test
    fun concurrentAutoSavesDoNotDropCompletedChapters() = withStorage {
        val order = (1..12).map(::chapter)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val work = order.mapIndexed { index, _ -> executor.submit<DownloadedNovelManifest> { save(index + 1, order) } }
            work.forEach { it.get() }
        } finally {
            executor.shutdown()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
        val manifest = requireNotNull(NovelDownloadStore.manifest(novelUrl))
        assertEquals(12, manifest.chapters.count { it.downloaded })
        assertEquals(order.map { NovelDownloadStore.chapterKey(it.url) }, manifest.chapters.map { NovelDownloadStore.chapterKey(it.url) })
    }

    @Test
    fun imageTimeoutKeepsReadableBodyAndExistingImagesAndAllowsLaterRepair() = withStorage { root ->
        val order = listOf(chapter(1), chapter(2))
        val manifest = save(1, order)
        save(2, order)
        val directory = root.listFiles()!!.single()
        val imageUrl = "https://example.test/timeout.png"
        val existingImageUrl = "https://example.test/saved.png"
        val existingImage = File(directory, "images/saved.png").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val body = ChapterBody.from(listOf(TextComponent("保留正文"),
            com.breakyuna.esjzone.novellibrary.component.ImageComponent(existingImageUrl),
            com.breakyuna.esjzone.novellibrary.component.ImageComponent(imageUrl)))
        val chapterFile = File(directory, manifest.chapters.first().fileName)
        chapterFile.writeText(Gson().toJson(DownloadedChapterContent("Chapter 1", chapter(1).url,
            listOf(DownloadedComponent("text", "保留正文"),
                DownloadedComponent("image", existingImageUrl, "images/saved.png"),
                DownloadedComponent("image", imageUrl)), schemaVersion = 2, body = body)))
        val clientField = EsjzoneClient::class.java.getDeclaredField("sharedHttpClient").apply { isAccessible = true }
        val originalClient = clientField.get(EsjzoneClient) as OkHttpClient
        val requestedImages = mutableListOf<String>()
        clientField.set(EsjzoneClient, originalClient.newBuilder().addInterceptor { chain ->
            requestedImages += chain.request().url.toString()
            throw SocketTimeoutException("Fixture image timeout")
        }.build())
        try {
            val progress = mutableListOf<DownloadProgress>()
            val partial = runBlocking { NovelDownloadStore.download(auth, novel(order)) { progress += it } }
            assertTrue(requestedImages.isNotEmpty())
            assertTrue(requestedImages.all { it == imageUrl })
            assertTrue(partial.chapters.first().bodyAvailable)
            assertFalse(partial.chapters.first().downloaded)
            assertTrue(partial.chapters.last().downloaded)
            assertFalse(partial.complete)
            assertEquals(1, progress.last().completed)
            val offline = requireNotNull(NovelDownloadStore.readChapter(chapter(1).url))
            assertEquals(body, offline.body)
            assertEquals("保留正文", offline.content.filterIsInstance<TextComponent>().single().text)
            assertEquals(imageUrl, (offline.toReaderDocument(chapter(1)).blocks.last() as
                com.breakyuna.esjzone.domain.reader.ReaderBlock.Image).url)
            assertArrayEquals(byteArrayOf(1, 2, 3), existingImage.readBytes())

            NovelDownloadStore.updateCommonPassword(novelUrl, "fixture")
            val unlocked = runBlocking { NovelDownloadStore.unlockChaptersWithCommonPassword(auth, novelUrl,
                setOf(chapter(1).url), "https://www.esjzone.cc/") }
            assertEquals(1, unlocked.unlockedCount)
            assertEquals(0, unlocked.failedCount)
            assertFalse(unlocked.updatedManifest!!.chapters.first().downloaded)

            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(imageUrl.toByteArray())
                .joinToString("") { "%02x".format(it) }
            File(directory, "images/image-$digest.png").writeBytes(byteArrayOf(4, 5, 6))
            NovelDownloadStore.initializeDirectory(root)
            requestedImages.clear()
            val repaired = runBlocking { NovelDownloadStore.download(auth, novel(order)) }
            assertTrue(repaired.complete)
            assertTrue(requestedImages.isEmpty())
            assertEquals(body, NovelDownloadStore.readChapter(chapter(1).url)!!.body)
        } finally {
            clientField.set(EsjzoneClient, originalClient)
        }
    }

    @Test
    fun incompleteImagesResumeWithoutReloadingBodyOrChangingFingerprint() = withStorage { root ->
        val manifest = save(1, listOf(chapter(1)))
        val directory = root.listFiles()!!.single()
        val file = File(directory, manifest.chapters.single().fileName)
        val imageUrl = "https://example.test/illustration.png"
        val body = ChapterBody.from(listOf(TextComponent("固定正文"), com.breakyuna.esjzone.novellibrary.component.ImageComponent(imageUrl)))
        file.writeText(Gson().toJson(DownloadedChapterContent("Chapter 1", chapter(1).url,
            listOf(DownloadedComponent("text", "固定正文"), DownloadedComponent("image", imageUrl)),
            "<p>不应重新解析的归档</p>", chapter(1).url, 2, body)))
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(imageUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val image = File(directory, "images/image-$digest.png")
        image.parentFile!!.mkdirs()
        image.writeBytes(byteArrayOf(1, 2, 3))
        runBlocking { NovelDownloadStore.download(auth, novel(listOf(chapter(1)))) }
        val content = requireNotNull(NovelDownloadStore.chapterContent(novelUrl, NovelDownloadStore.manifest(novelUrl)!!.chapters.single()))
        assertEquals(body, content.body)
        assertEquals("images/${image.name}", content.components.last().localFile)
        assertTrue(NovelDownloadStore.isDownloaded(novelUrl))
        assertTrue((NovelDownloadStore.readChapter(chapter(1).url)!!.toReaderDocument(chapter(1)).blocks.last() as
            com.breakyuna.esjzone.domain.reader.ReaderBlock.Image).url.startsWith("file:"))
    }

    @Test
    fun legacyChapterMigratesLocallyAndThenIgnoresArchivedHtmlForReading() = withStorage { root ->
        val manifest = save(1, listOf(chapter(1)))
        val directory = root.listFiles()!!.single()
        val file = File(directory, manifest.chapters.single().fileName)
        val legacy = DownloadedChapterContent("Chapter 1", chapter(1).url, listOf(DownloadedComponent("text", "正文")),
            "<p>正文<strong>粗体</strong></p>", chapter(1).url)
        file.writeText(Gson().toJson(legacy))
        val first = requireNotNull(NovelDownloadStore.readChapter(chapter(1).url))
        assertNotNull(first.body)
        val migrated = GZIPInputStream(file.inputStream()).bufferedReader().use {
            Gson().fromJson(it, DownloadedChapterContent::class.java)
        }
        assertEquals(2, migrated.schemaVersion)
        assertEquals(first.body?.fingerprint, migrated.body?.fingerprint)
        file.writeText(Gson().toJson(migrated.copy(contentHtml = "<p>归档内容不应作为阅读来源</p>")))
        val second = requireNotNull(NovelDownloadStore.readChapter(chapter(1).url))
        assertEquals(first.toReaderDocument(chapter(1)).blocks, second.toReaderDocument(chapter(1)).blocks)
    }

    @Test
    fun manifestsReusePublishedSnapshotsAndRefreshAfterWritesAndDeletion() = withStorage {
        assertNull(NovelDownloadStore.manifest(novelUrl))
        save(1, listOf(chapter(1)))
        val first = requireNotNull(NovelDownloadStore.manifest(novelUrl))
        assertSame(first, NovelDownloadStore.manifest(novelUrl))
        save(2, listOf(chapter(1), chapter(2)))
        val updated = requireNotNull(NovelDownloadStore.manifest(novelUrl))
        assertEquals(2, updated.chapters.size)
        assertNotSame(first, updated)
        assertSame(updated, NovelDownloadStore.manifest(novelUrl))
        assertTrue(NovelDownloadStore.delete(novelUrl))
        assertNull(NovelDownloadStore.manifest(novelUrl))
        save(1, listOf(chapter(1)))
        assertEquals(1, NovelDownloadStore.manifest(novelUrl)!!.chapters.size)
    }

    @Test
    fun legacyMissingImagesKeepTheirPlaceholderWithAndWithoutHtml() = withStorage { root ->
        val order = listOf(chapter(1), chapter(2))
        val manifests = order.indices.map { save(it + 1, order) }
        val directory = root.listFiles()!!.single()
        order.forEachIndexed { index, item ->
            val record = manifests.last().chapters[index]
            val legacy = DownloadedChapterContent(item.name, item.url,
                listOf(DownloadedComponent("image", "")),
                if (index == 0) "<p><img/></p>" else null, item.url)
            File(directory, record.fileName).writeText(Gson().toJson(legacy))
            val restored = requireNotNull(NovelDownloadStore.readChapter(item.url))
            assertTrue(restored.content.all { it is TextComponent })
            assertTrue(restored.body!!.readerBlocks().joinToString("") { it.sourceText() }.isNotBlank())
            assertEquals(restored.body.fingerprint, NovelDownloadStore.readChapter(item.url)!!.body!!.fingerprint)
        }
    }

    @Test
    fun blankImagePlaceholderDoesNotRewriteAnExistingSnapshot() = withStorage { root ->
        val manifest = save(1, listOf(chapter(1)))
        val directory = root.listFiles()!!.single()
        val body = ChapterBody.from(listOf(com.breakyuna.esjzone.novellibrary.component.ImageComponent("")))
        File(directory, manifest.chapters.single().fileName).writeText(Gson().toJson(
            DownloadedChapterContent("Chapter 1", chapter(1).url, listOf(DownloadedComponent("image", "")),
                schemaVersion = 2, body = body)))
        val restored = requireNotNull(NovelDownloadStore.readChapter(chapter(1).url))
        assertTrue(restored.content.single() is TextComponent)
        assertEquals(body, restored.body)
    }

    @Test
    fun selectedChaptersRemainPartialAndNewChaptersBecomeSearchable() = withStorage {
        val order = (1..3).map(::chapter)
        save(1, order)
        val hits = mutableListOf<ReaderSearchHit>()
        runBlocking { NovelDownloadStore.searchChapters(novelUrl, "正文") { hits.addAll(it) } }
        assertEquals(1, hits.size)
        save(3, order)
        hits.clear()
        runBlocking { NovelDownloadStore.searchChapters(novelUrl, "正文") { hits.addAll(it) } }
        assertEquals(listOf(1, 3).map { NovelDownloadStore.chapterKey(chapter(it).url) }, hits.map { it.anchor.chapterKey })
        assertFalse(NovelDownloadStore.isDownloaded(novelUrl))
    }

    @Test
    fun backupRestoresStructuredTextAndKeepsExistingDownloads() = withStorage { root ->
        save(1, listOf(chapter(1)))
        val backup = Files.createTempDirectory("structured-backup-").toFile()
        val restoredRoot = Files.createTempDirectory("structured-restored-").toFile()
        try {
            NovelDownloadStore.exportBackup(backup)
            assertEquals(2, Gson().fromJson(backup.walkTopDown().first { it.name == "manifest.json" }.readText(), DownloadedNovelManifest::class.java).version)
            NovelDownloadStore.initializeDirectory(restoredRoot)
            assertNull(NovelDownloadStore.manifest(novelUrl))
            NovelDownloadStore.importBackup(backup)
            assertNotNull(NovelDownloadStore.manifest(novelUrl))
            assertEquals("正文 1", NovelDownloadStore.readChapter(chapter(1).url)!!.body!!.readerBlocks().single().let {
                it.sourceText()
            })
            save(2, listOf(chapter(1), chapter(2)))
            NovelDownloadStore.importBackup(backup)
            assertNotNull(NovelDownloadStore.readChapter(chapter(2).url))
        } finally { backup.deleteRecursively(); restoredRoot.deleteRecursively(); NovelDownloadStore.initializeDirectory(root) }
    }

    @Test
    fun chapterIndexTracksNewNovelsCatalogChangesAndDeletionWithoutRescanningRoot() {
        val root = CountingDirectory(Files.createTempDirectory("download-index-").toFile())
        NovelDownloadStore.initializeDirectory(root)
        try {
            val order = listOf(chapter(1), chapter(2))
            save(1, order)
            root.scans = 0
            NovelDownloadStore.initializeDirectory(root)
            assertNull(NovelDownloadStore.readChapter(chapter(2).url))
            val scans = root.scans
            // Discover title folders once; chapter changes then update the existing index.
            assertEquals(1, scans)
            val committed = save(2, order)
            assertSame(committed, NovelDownloadStore.manifest(novelUrl))
            assertNotNull(NovelDownloadStore.readChapter(chapter(2).url))

            runBlocking { NovelDownloadStore.download(auth, novel(order.reversed())) }
            assertEquals(chapter(2).url, NovelDownloadStore.readChapter(chapter(1).url)!!.previous!!.url)
            assertEquals(chapter(1).url, NovelDownloadStore.readChapter(chapter(2).url)!!.next!!.url)

            val otherUrl = "https://www.esjzone.cc/detail/9002.html"
            val otherChapter = Chapter("Other", "https://www.esjzone.cc/forum/9002/1.html", false)
            assertNull(NovelDownloadStore.readChapter(otherChapter.url))
            NovelDownloadStore.saveChapter("Other", otherUrl, "", listOf(otherChapter), otherChapter,
                DetailedChapter("Other", listOf(TextComponent("另一部作品")), null, null), auth)
            assertNotNull(NovelDownloadStore.readChapter(otherChapter.url))
            assertTrue(NovelDownloadStore.delete(novelUrl))
            assertNull(NovelDownloadStore.readChapter(chapter(1).url))
            assertNotNull(NovelDownloadStore.readChapter(otherChapter.url))
            assertEquals(scans, root.scans)
        } finally { root.deleteRecursively() }
    }

    @Test
    fun recoveredChapterStaysIndexedWhenAnotherChapterIsSaved() = withStorage { root ->
        val order = (1..3).map(::chapter)
        val manifest = save(1, order)
        val directory = root.listFiles()!!.single()
        val recovered = manifest.chapters[1]
        File(directory, recovered.fileName).writeText(Gson().toJson(DownloadedChapterContent(
            recovered.name, recovered.url, listOf(DownloadedComponent("text", "恢复正文")),
            schemaVersion = 2, body = ChapterBody.from(listOf(TextComponent("恢复正文"))))))
        // Simulate a chapter committed before its manifest checkpoint at process exit.
        NovelDownloadStore.initializeDirectory(root)
        assertNotNull(NovelDownloadStore.readChapter(chapter(1).url))
        save(3, order)
        assertEquals("恢复正文", NovelDownloadStore.readChapter(chapter(2).url)!!.body!!
            .readerBlocks().single().sourceText())
        assertTrue(NovelDownloadStore.manifest(novelUrl)!!.chapters[1].bodyAvailable)
    }

    @Test
    fun compressedChaptersKeepHtmlAndExportTextAndEpub() = withStorage { root ->
        val text = "正文中文与 ruby\n".repeat(500)
        val html = "<p>${text.replace("\n", "<br>")}</p>"
        val saved = requireNotNull(NovelDownloadStore.saveChapter("Novel", novelUrl, "", listOf(chapter(1)),
            chapter(1), DetailedChapter("Chapter 1", listOf(TextComponent(text)), null, null, html, chapter(1).url), auth))
        val directory = root.listFiles()!!.single()
        val file = File(directory, saved.chapters.single().fileName)
        val stored = GZIPInputStream(file.inputStream()).bufferedReader().use {
            Gson().fromJson(it, DownloadedChapterContent::class.java)
        }
        assertEquals(html, stored.contentHtml)
        assertEquals(text, stored.body!!.readerBlocks().single().sourceText())
        assertTrue(file.length() < Gson().toJson(stored).toByteArray(Charsets.UTF_8).size / 5)
        assertEquals(directory.walkTopDown().filter(File::isFile).sumOf(File::length), NovelDownloadStore.storageBytes(novelUrl))
        val loader: (DownloadedChapterRecord) -> DownloadedChapterContent? = { NovelDownloadStore.chapterContent(novelUrl, it) }
        val txt = ByteArrayOutputStream()
        NovelExporter.exportTxt(saved, loader, txt)
        assertTrue(txt.toString("UTF-8").contains(text))
        val epub = ByteArrayOutputStream()
        NovelExporter.exportEpub(saved, loader, epub)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(epub.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(entries.getValue("OEBPS/chapter-1.xhtml").contains("正文中文与 ruby"))
    }

    @Test
    fun backupSnapshotsRemainStableThroughLegacyMigrationAndSourceDeletion() = withStorage { root ->
        val order = listOf(chapter(1), chapter(2))
        save(1, order)
        val manifest = save(2, order)
        val directory = root.listFiles()!!.single()
        val file = File(directory, manifest.chapters.first().fileName)
        val legacy = Gson().toJson(DownloadedChapterContent("Chapter 1", chapter(1).url,
            listOf(DownloadedComponent("text", "旧正文")), "<p>旧正文<strong>粗体</strong></p>", chapter(1).url))
        file.writeText(legacy)
        val image = File(directory, "images/fixture.png")
        image.parentFile!!.mkdirs()
        image.writeBytes(byteArrayOf(1, 2, 3))
        NovelDownloadStore.updateCommonPassword(novelUrl, "fixture")
        val backup = Files.createTempDirectory("download-snapshot-").toFile()
        val restoredRoot = Files.createTempDirectory("download-restore-").toFile()
        try {
            NovelDownloadStore.exportBackup(backup)
            val snapshot = File(backup, "${directory.name}/${file.name}")
            val snapshotImage = File(backup, "${directory.name}/images/fixture.png")
            assertFalse(Files.isSameFile(file.toPath(), snapshot.toPath()))
            assertArrayEquals(image.readBytes(), snapshotImage.readBytes())
            val backupManifest = Gson().fromJson(File(backup, "${directory.name}/manifest.json").readText(), DownloadedNovelManifest::class.java)
            assertNull(backupManifest.commonPassword)
            assertNotNull(NovelDownloadStore.readChapter(chapter(1).url))
            assertFalse(Files.isSameFile(file.toPath(), snapshot.toPath()))
            assertEquals(legacy, snapshot.readText())
            assertTrue(NovelDownloadStore.delete(novelUrl))
            assertArrayEquals(byteArrayOf(1, 2, 3), snapshotImage.readBytes())
            NovelDownloadStore.initializeDirectory(restoredRoot)
            NovelDownloadStore.importBackup(backup)
            assertEquals("旧正文粗体", NovelDownloadStore.readChapter(chapter(1).url)!!.body!!.readerBlocks().single().sourceText())
            assertEquals("正文 2", NovelDownloadStore.readChapter(chapter(2).url)!!.body!!.readerBlocks().single().sourceText())
        } finally { backup.deleteRecursively(); restoredRoot.deleteRecursively(); NovelDownloadStore.initializeDirectory(root) }
    }

    @Test
    fun backupPublishesOneNovelAtATimeOutsideStorageLock() = withStorage { _ ->
        save(1, listOf(chapter(1)))
        val otherUrl = "https://www.esjzone.cc/detail/9002.html"
        val other = Chapter("Other", "https://www.esjzone.cc/forum/9002/1.html", false)
        NovelDownloadStore.saveChapter("Other", otherUrl, "", listOf(other), other,
            DetailedChapter("Other", listOf(TextComponent("另一部作品")), null, null), auth)
        val backup = Files.createTempDirectory("incremental-backup-").toFile()
        val executor = Executors.newSingleThreadExecutor()
        var count = 0
        try {
            NovelDownloadStore.exportBackup(backup) { snapshot ->
                assertEquals(listOf(snapshot.name), backup.listFiles()!!.map { it.name })
                assertNotNull(executor.submit<DownloadedNovelManifest?> { NovelDownloadStore.manifest(novelUrl) }
                    .get(5, TimeUnit.SECONDS))
                assertTrue(File(snapshot, "manifest.json").isFile)
                assertTrue(snapshot.listFiles()!!.any { it.name.startsWith("chapter-") })
                count++
                snapshot.deleteRecursively()
            }
            assertEquals(2, count)
            assertTrue(backup.listFiles()!!.isEmpty())
        } finally { executor.shutdownNow(); backup.deleteRecursively() }
    }

    @Test
    fun externalChapterUsesItsParentAfterOneDirectoryDiscoveryPerInitialization() {
        val root = CountingDirectory(Files.createTempDirectory("external-parent-").toFile())
        NovelDownloadStore.initializeDirectory(root)
        try {
            val external = Chapter("External", "https://www.wenku8.net/novel/1/1000/2000.htm", false)
            NovelDownloadStore.saveChapter("Novel", novelUrl, "", listOf(external), external,
                DetailedChapter("External", listOf(TextComponent("离线正文")), null, null), auth)
            root.scans = 0
            NovelDownloadStore.initializeDirectory(root)
            val read = requireNotNull(NovelDownloadStore.readChapter(external.url, novelUrl))
            assertEquals("离线正文", read.toReaderDocument(external).blocks.single().sourceText())
            assertNull(NovelDownloadStore.readChapter(external.url.replace("2000.htm", "2001.htm"), novelUrl))
            assertEquals(1, root.scans)
            // Callers without a parent still find externally hosted chapters through the full index.
            NovelDownloadStore.initializeDirectory(root)
            assertNotNull(NovelDownloadStore.readChapter(external.url))
            assertEquals(2, root.scans)
        } finally { root.deleteRecursively() }
    }

    @Test
    fun shelfMembershipSkipsSizeWalkAndInventoryAllowsConcurrentReading() {
        val rootFile = Files.createTempDirectory("inventory-reading-").toFile()
        val executor = Executors.newSingleThreadExecutor()
        var sizeWalks = 0
        var checkConcurrentReads = true
        val root = object : File(rootFile.path) {
            override fun listFiles(): Array<File>? = super.listFiles()?.map { file ->
                object : File(file.path) {
                    override fun listFiles(): Array<File>? {
                        sizeWalks++
                        // A size traversal holding ioLock would deadlock this independent reader.
                        if (checkConcurrentReads) assertNotNull(executor.submit<DetailedChapter?> {
                            NovelDownloadStore.readChapter(chapter(1).url)
                        }.get(5, TimeUnit.SECONDS))
                        return super.listFiles()
                    }
                }
            }?.toTypedArray()
        }
        NovelDownloadStore.initializeDirectory(root)
        try {
            save(1, listOf(chapter(1), chapter(2)))
            // Discover the actual directory handles, including the instrumented size walker.
            NovelDownloadStore.reindex(com.breakyuna.esjzone.storage.StorageFile(root))
            assertEquals(setOf(novelUrl), NovelDownloadStore.downloadedNovelUrls())
            assertEquals(0, sizeWalks)
            val inventory = NovelDownloadStore.listDownloadedNovels()
            assertTrue(sizeWalks > 0)
            assertEquals(1, inventory.single().downloadedChapterCount)
            assertEquals(rootFile.walkTopDown().filter(File::isFile).sumOf(File::length),
                inventory.single().storageBytes)
            save(2, listOf(chapter(1), chapter(2)))
            val updated = NovelDownloadStore.listDownloadedNovels().single()
            assertEquals(2, updated.downloadedChapterCount)
            assertEquals(rootFile.walkTopDown().filter(File::isFile).sumOf(File::length), updated.storageBytes)
            checkConcurrentReads = false
            assertTrue(NovelDownloadStore.delete(novelUrl))
            assertTrue(NovelDownloadStore.downloadedNovelUrls().isEmpty())
            assertTrue(NovelDownloadStore.listDownloadedNovels().isEmpty())
        } finally { executor.shutdownNow(); rootFile.deleteRecursively() }
    }

    private class CountingDirectory(file: File) : File(file.path) {
        var scans = 0
        override fun listFiles(): Array<File>? {
            scans++
            return super.listFiles()
        }
    }
}
