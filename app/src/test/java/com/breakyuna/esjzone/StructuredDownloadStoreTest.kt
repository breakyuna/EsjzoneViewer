package com.breakyuna.esjzone

import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.domain.reader.ReaderSearchHit
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.*
import com.breakyuna.esjzone.offline.*
import com.breakyuna.esjzone.ui.reader.toReaderDocument
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import java.io.ByteArrayOutputStream
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
            assertEquals(restored.body!!.fingerprint, NovelDownloadStore.readChapter(item.url)!!.body!!.fingerprint)
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
            assertNull(NovelDownloadStore.readChapter(chapter(2).url))
            val scans = root.scans
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

    private class CountingDirectory(file: File) : File(file.path) {
        var scans = 0
        override fun listFiles(): Array<File>? {
            scans++
            return super.listFiles()
        }
    }
}
