package com.breakyuna.esjzone

import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.domain.reader.ReaderSearchHit
import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.StructuredChapterCache
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.novellibrary.novel.*
import com.breakyuna.esjzone.offline.*
import com.breakyuna.esjzone.storage.StorageMigrationBusyException
import com.breakyuna.esjzone.ui.reader.toReaderDocument
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
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

    private fun awaitMaintenance(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000L) {
            while (!condition()) delay(10L)
        }
    }

    // --- Core CRUD, formatting recovery, v1->v2 migration, export ---

    @Test
    fun saveReadReorderAndDeleteManageOfflineChapters() = withStorage { _ ->
        val order = listOf(chapter(1), chapter(2))
        save(1, order)
        assertNull(NovelDownloadStore.readChapter(chapter(2).url))
        save(2, order)
        assertNotNull(NovelDownloadStore.readChapter(chapter(2).url))

        runBlocking { NovelDownloadStore.download(auth, novel(order.reversed())) }
        assertEquals(chapter(2).url, NovelDownloadStore.readChapter(chapter(1).url)!!.previous!!.url)
        assertEquals(chapter(1).url, NovelDownloadStore.readChapter(chapter(2).url)!!.next!!.url)

        val otherUrl = "https://www.esjzone.cc/detail/9002.html"
        val otherChapter = Chapter("Other", "https://www.esjzone.cc/forum/9002/1.html", false)
        NovelDownloadStore.saveChapter("Other", otherUrl, "", listOf(otherChapter), otherChapter,
            DetailedChapter("Other", listOf(TextComponent("另一部作品")), null, null), auth)
        assertTrue(NovelDownloadStore.delete(novelUrl))
        assertNull(NovelDownloadStore.readChapter(chapter(1).url))
        assertNotNull(NovelDownloadStore.readChapter(otherChapter.url))
    }

    @Test
    fun externalChapterReadsWithAndWithoutParentNovel() = withStorage { root ->
        val external = Chapter("External", "https://www.wenku8.net/novel/1/1000/2000.htm", false)
        NovelDownloadStore.saveChapter("Novel", novelUrl, "", listOf(external), external,
            DetailedChapter("External", listOf(TextComponent("离线正文")), null, null), auth)
        NovelDownloadStore.initializeDirectory(root)
        val read = requireNotNull(NovelDownloadStore.readChapter(external.url, novelUrl))
        assertEquals("离线正文", read.toReaderDocument(external).blocks.single().sourceText())
        assertNull(NovelDownloadStore.readChapter(external.url.replace("2000.htm", "2001.htm"), novelUrl))
        NovelDownloadStore.initializeDirectory(root)
        assertNotNull(NovelDownloadStore.readChapter(external.url))
    }

    @Test
    fun oldDownloadedBodyRecoversFormattingWithoutChangingItsFingerprint() = withStorage { root ->
        val manifest = save(1, listOf(chapter(1)))
        val directory = root.listFiles()!!.single()
        val file = File(directory, manifest.chapters.single().fileName)
        val html = "<p style='text-align:center'>标题</p><hr><p>正文</p>"
        val body = ChapterBody.from(analyseComponents(Jsoup.parseBodyFragment(html, chapter(1).url).body()))
        file.writeText(Gson().toJson(DownloadedChapterContent("Chapter 1", chapter(1).url,
            listOf(DownloadedComponent("text", "标题"), DownloadedComponent("text", "正文")),
            html, chapter(1).url, 2, body)))
        val restored = requireNotNull(NovelDownloadStore.readChapter(chapter(1).url, novelUrl)).body!!
        assertEquals(body.fingerprint, restored.fingerprint)
        assertEquals(body.readerBlocks(), restored.readerBlocks())
        assertEquals(mapOf(0 to "center"), restored.formatting!!.alignments)
        assertEquals(listOf(1), restored.formatting!!.dividersAt)
        awaitMaintenance {
            runCatching {
                GZIPInputStream(file.inputStream()).bufferedReader().use {
                    Gson().fromJson(it, DownloadedChapterContent::class.java).body?.formatting == restored.formatting
                }
            }.getOrDefault(false)
        }
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
        awaitMaintenance {
            runCatching { GZIPInputStream(file.inputStream()).bufferedReader().use {
                Gson().fromJson(it, DownloadedChapterContent::class.java).schemaVersion == 2
            } }.getOrDefault(false)
        }
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
    fun legacyBlankImagePlaceholdersRemainReadableDuringMigration() = withStorage { root ->
        val order = listOf(chapter(1), chapter(2))
        save(1, order)
        val manifest = save(2, order)
        val directory = root.listFiles()!!.single()
        order.forEachIndexed { index, item ->
            val record = manifest.chapters[index]
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
    fun blankImagePlaceholdersRemainReadableWithoutChangingStructuredBody() = withStorage { root ->
        val target = chapter(1)
        val manifest = save(1, listOf(target))
        val directory = root.listFiles()!!.single()
        val file = File(directory, manifest.chapters.single().fileName)
        val body = ChapterBody.from(listOf(com.breakyuna.esjzone.novellibrary.component.ImageComponent("")))
        file.writeText(Gson().toJson(
            DownloadedChapterContent(target.name, target.url, listOf(DownloadedComponent("image", "")),
                schemaVersion = 2, body = body)))
        val snapshot = requireNotNull(NovelDownloadStore.readChapter(target.url))
        assertTrue(snapshot.content.single() is TextComponent)
        assertEquals(body, snapshot.body)
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
    fun exportReadsLegacyChapterWithoutRewritingIt() = withStorage { root ->
        val target = chapter(1)
        val saved = save(1, listOf(target))
        val record = saved.chapters.single()
        val file = File(root.listFiles()!!.single(), record.fileName)
        val legacy = Gson().toJson(DownloadedChapterContent(
            record.name, record.url, listOf(DownloadedComponent("text", "旧版正文")), schemaVersion = 1
        ))
        file.writeText(legacy)
        val txt = ByteArrayOutputStream()

        NovelExporter.exportTxt(saved, { NovelDownloadStore.chapterContent(novelUrl, it, migrateOnRead = false) }, txt)

        assertTrue(txt.toString("UTF-8").contains("旧版正文"))
        assertEquals(legacy, file.readText())
    }

    // --- Catalog & manifest completeness (5 tests) ---

    @Test
    fun partialDownloadRetainsRichDetailAndAppendsNewerChapters() = withStorage { root ->
        val order = listOf(chapter(1), chapter(2))
        NovelDownloadStore.saveChapter("Novel", novelUrl, "", listOf(chapter(1)), chapter(1),
            detail(1), auth, chapterOrderComplete = false)
        NovelDownloadStore.initializeDirectory(root)
        assertFalse(requireNotNull(NovelDownloadStore.readDetailedNovel(novelUrl)).chapterOrderComplete)

        save(1, order)
        val detailed = novel(order).copy(
            descriptionHtml = "<div class='description'><p>Retained description</p></div>",
            catalogHtml = "<div id='integration'><details open><summary>Volume 1</summary>" +
                order.joinToString("") { "<a href='${it.url}' data-title='${it.name}'>${it.name}</a>" } +
                "</details></div>"
        )
        NovelDownloadStore.retainNovelDetail(detailed)
        save(2, order)
        NovelDownloadStore.initializeDirectory(root)
        val completeOrder = requireNotNull(NovelDownloadStore.readDetailedNovel(novelUrl))
        assertTrue(completeOrder.description.preview(Int.MAX_VALUE).contains("Retained description"))
        assertTrue(completeOrder.chapterList.items.first() is com.breakyuna.esjzone.novellibrary.component.ChapterListItem)
        assertTrue(completeOrder.chapterOrderComplete)

        save(3, order + chapter(3))
        NovelDownloadStore.initializeDirectory(root)
        val expanded = requireNotNull(NovelDownloadStore.readDetailedNovel(novelUrl))
        assertEquals(listOf(1, 2, 3).map { chapter(it).url }, expanded.chapterList.orderedChapters.map { it.url })
        assertNotNull(NovelDownloadStore.readChapter(chapter(3).url, novelUrl))
        assertFalse(expanded.chapterOrderComplete)
    }

    @Test
    fun cachedReaderDetailCannotReplaceACompletedDetailRefresh() = withStorage { root ->
        val order = listOf(chapter(1), chapter(2))
        save(1, order)
        val old = novel(order).copy(
            descriptionHtml = "<p>Old description</p>",
            catalogHtml = "<div id='integration'><a href='${chapter(1).url}'>Old chapter name</a></div>"
        )
        val fresh = old.copy(descriptionHtml = "<p>Fresh description</p>",
            catalogHtml = "<div id='integration'>" +
                order.joinToString("") { "<a href='${it.url}'>${it.name}</a>" } + "</div>")
        NovelDownloadStore.retainNovelDetail(old)
        NovelDownloadStore.retainNovelDetail(fresh, replaceExistingSnapshot = true)
        NovelDownloadStore.retainNovelDetail(old)
        save(2, order)
        NovelDownloadStore.initializeDirectory(root)
        val restored = requireNotNull(NovelDownloadStore.readDetailedNovel(novelUrl))
        assertTrue(restored.description.preview(Int.MAX_VALUE).contains("Fresh description"))
        assertEquals(order.map { it.name }, restored.chapterList.orderedChapters.map { it.name })
        assertNotNull(NovelDownloadStore.readChapter(chapter(2).url, novelUrl))
    }

    @Test
    fun autoSavePersistsAndUpgradesCatalogCompletenessWithoutDownloadingWholeBook() = withStorage { root ->
        val order = (1..3).map(::chapter)
        val partial = save(1, order)
        assertFalse(partial.catalogComplete)
        assertFalse(partial.complete)

        val upgraded = requireNotNull(NovelDownloadStore.saveChapter(
            "Novel", novelUrl, "", order, chapter(1), detail(1), auth, chapterOrderComplete = true))
        assertTrue(upgraded.catalogComplete)
        assertFalse(upgraded.complete)
        save(2, order)

        NovelDownloadStore.initializeDirectory(root)
        val restored = requireNotNull(NovelDownloadStore.manifest(novelUrl))
        assertTrue(restored.catalogComplete)
        assertFalse(restored.complete)
        assertEquals(order.map { it.url }, restored.chapters.map { it.url })
        assertNotNull(NovelDownloadStore.readChapter(chapter(1).url))
        assertNotNull(NovelDownloadStore.readChapter(chapter(2).url))
    }

    @Test
    fun staleReaderCatalogCannotReplacePublishedCatalogOrRestoreRemovedEntries() = withStorage { root ->
        val readerOrder = listOf(chapter(1), chapter(2))
        requireNotNull(NovelDownloadStore.saveChapter(
            "Novel", novelUrl, "", readerOrder, chapter(1), detail(1), auth, chapterOrderComplete = true))
        val cache = Files.createTempDirectory("newer-catalog-cache-").toFile()
        PageCache.initializeDirectory(cache)
        try {
            val key = EsjzoneClient.novelDetailCacheKey(auth, chapter(3).url)
            PageCache.write(key, "fixture")
            requireNotNull(StructuredChapterCache.write(key, "fixture", detail(3)))
            val newerOrder = listOf(chapter(3), chapter(1))
            runBlocking {
                NovelDownloadStore.download(auth, novel(newerOrder), selectedChapterUrls = setOf(chapter(3).url))
            }
            requireNotNull(NovelDownloadStore.saveChapterCatalog("Novel", novelUrl, "", readerOrder))
            val staleOrder = listOf(chapter(1), chapter(2), chapter(4))
            requireNotNull(NovelDownloadStore.saveChapter(
                "Novel", novelUrl, "", staleOrder, chapter(2), detail(2), auth, chapterOrderComplete = true))

            NovelDownloadStore.initializeDirectory(root)
            val restored = requireNotNull(NovelDownloadStore.manifest(novelUrl))
            assertTrue(restored.catalogComplete)
            assertEquals(newerOrder.map { it.url }, restored.chapters.filterNot { it.localOnly }.map { it.url })
            assertTrue(restored.chapters.single { it.url == chapter(2).url }.localOnly)
            assertFalse(restored.chapters.any { it.url == chapter(4).url })
            assertNotNull(NovelDownloadStore.readChapter(chapter(2).url))
            assertNotNull(NovelDownloadStore.readChapter(chapter(3).url))
        } finally { cache.deleteRecursively() }
    }

    @Test
    fun resolvedCatalogSurvivesReloadAndRetainsRemovedChaptersAsLocalSnapshots() {
        for (bodySavedFirst in listOf(true, false)) withStorage { root ->
            val order = (1..3).map(::chapter)
            if (bodySavedFirst) save(1, emptyList())
            val catalog = requireNotNull(NovelDownloadStore.saveChapterCatalog("Novel", novelUrl, "", order))
            assertTrue(catalog.catalogComplete)
            assertFalse(catalog.complete)
            if (!bodySavedFirst) save(1, emptyList())

            NovelDownloadStore.initializeDirectory(root)
            val restored = requireNotNull(NovelDownloadStore.manifest(novelUrl))
            assertTrue(restored.catalogComplete)
            assertEquals(order.map { it.url }, restored.chapters.filterNot { it.localOnly }.map { it.url })
            assertNotNull(NovelDownloadStore.readChapter(chapter(1).url))
            assertNull(NovelDownloadStore.readChapter(chapter(2).url))
        }

        withStorage { root ->
            save(2, (1..3).map(::chapter))
            val trimmed = listOf(chapter(3), chapter(1))
            requireNotNull(NovelDownloadStore.saveChapterCatalog("Novel", novelUrl, "", trimmed))
            requireNotNull(NovelDownloadStore.saveChapter(
                "Novel", novelUrl, "", trimmed, chapter(2), detail(2), auth, chapterOrderComplete = true))
            NovelDownloadStore.initializeDirectory(root)
            val restored = requireNotNull(NovelDownloadStore.manifest(novelUrl))
            assertEquals(trimmed.map { it.url }, restored.chapters.filterNot { it.localOnly }.map { it.url })
            assertTrue(restored.chapters.single { it.url == chapter(2).url }.localOnly)
            assertNotNull(NovelDownloadStore.readChapter(chapter(2).url))
        }
    }

    // --- Backup & restore (4 core tests) ---

    @Test
    fun backupRestoresStructuredTextLegacyMigrationAndStripsCommonPassword() = withStorage { root ->
        val order = listOf(chapter(1), chapter(2))
        save(1, order)
        val manifest = save(2, order)
        val directory = root.listFiles()!!.single()
        val file = File(directory, manifest.chapters.first().fileName)
        val legacy = Gson().toJson(DownloadedChapterContent("Chapter 1", chapter(1).url,
            listOf(DownloadedComponent("text", "旧正文")), "<p>旧正文<strong>粗体</strong></p>", chapter(1).url))
        file.writeText(legacy)
        val image = File(directory, "images/fixture.png").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        NovelDownloadStore.updateCommonPassword(novelUrl, "fixture")
        val backup = Files.createTempDirectory("download-snapshot-").toFile()
        val restoredRoot = Files.createTempDirectory("download-restore-").toFile()
        try {
            NovelDownloadStore.exportBackup(backup)
            val snapshot = File(backup, "${directory.name}/${file.name}")
            val snapshotImage = File(backup, "${directory.name}/images/fixture.png")
            assertFalse(Files.isSameFile(file.toPath(), snapshot.toPath()))
            assertArrayEquals(image.readBytes(), snapshotImage.readBytes())
            val backupManifest = Gson().fromJson(File(backup, "${directory.name}/manifest.json").readText(),
                DownloadedNovelManifest::class.java)
            assertEquals(2, backupManifest.version)
            assertNull(backupManifest.commonPassword)
            assertNotNull(NovelDownloadStore.readChapter(chapter(1).url))
            assertEquals(legacy, snapshot.readText())
            assertTrue(NovelDownloadStore.delete(novelUrl))

            NovelDownloadStore.initializeDirectory(restoredRoot)
            NovelDownloadStore.importBackup(backup)
            assertEquals("旧正文粗体", NovelDownloadStore.readChapter(chapter(1).url)!!.body!!.readerBlocks().single().sourceText())
            assertEquals("正文 2", NovelDownloadStore.readChapter(chapter(2).url)!!.body!!.readerBlocks().single().sourceText())
            save(3, order + chapter(3))
            NovelDownloadStore.importBackup(backup)
            assertNotNull(NovelDownloadStore.readChapter(chapter(3).url))
        } finally { backup.deleteRecursively(); restoredRoot.deleteRecursively(); NovelDownloadStore.initializeDirectory(root) }
    }

    @Test
    fun backupExportAllowsConcurrentReadsStreamsPerNovelAndRejectsInvalidatedSnapshots() = withStorage { root ->
        val other = Files.createTempDirectory("backup-other-").toFile()
        val snapshots = Files.createTempDirectory("readable-backup-").toFile()
        val executor = Executors.newSingleThreadExecutor()
        try {
            save(1, listOf(chapter(1)))
            val otherUrl = "https://www.esjzone.cc/detail/9002.html"
            val otherChapter = Chapter("Other", "https://www.esjzone.cc/forum/9002/1.html", false)
            NovelDownloadStore.saveChapter("Other", otherUrl, "", listOf(otherChapter), otherChapter,
                DetailedChapter("Other", listOf(TextComponent("另一部作品")), null, null), auth)
            var checkedConcurrentRead = false
            var count = 0
            NovelDownloadStore.exportBackup(snapshots, checkActive = {
                if (!checkedConcurrentRead && snapshots.listFiles().orEmpty().any(File::isDirectory)) {
                    checkedConcurrentRead = true
                    assertNotNull(executor.submit<DetailedChapter?> {
                        NovelDownloadStore.readChapter(chapter(1).url)
                    }.get(5, TimeUnit.SECONDS))
                }
            }) { snapshot ->
                assertEquals(listOf(snapshot.name), snapshots.listFiles()!!.map { it.name })
                assertTrue(File(snapshot, "manifest.json").isFile)
                count++
                snapshot.deleteRecursively()
            }
            assertTrue(checkedConcurrentRead)
            assertEquals(2, count)
            assertTrue(snapshots.listFiles()!!.isEmpty())

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
            executor.shutdownNow()
            NovelDownloadStore.initializeDirectory(root)
            other.deleteRecursively()
            snapshots.deleteRecursively()
        }
    }

    @Test
    fun backupImportRejectsCorruptArchivesAndRollsBackCancelledReplacement() = withStorage { root ->
        val incoming = Files.createTempDirectory("restore-incoming-").toFile()
        val backup = Files.createTempDirectory("restore-backup-").toFile()
        val otherUrl = "https://www.esjzone.cc/detail/9002.html"
        val otherChapter = Chapter("Other chapter", "https://www.esjzone.cc/forum/9002/1.html", false)
        val laterUrl = "https://www.esjzone.cc/detail/9003.html"
        val laterChapter = Chapter("Later chapter", "https://www.esjzone.cc/forum/9003/1.html", false)
        try {
            NovelDownloadStore.initializeDirectory(incoming)
            requireNotNull(NovelDownloadStore.saveChapter("Novel", otherUrl, "", listOf(otherChapter), otherChapter,
                DetailedChapter("Other chapter", listOf(TextComponent("恢复的正文")), null, null), auth))
            requireNotNull(NovelDownloadStore.saveChapter("Later", laterUrl, "", listOf(laterChapter), laterChapter,
                DetailedChapter("Later chapter", listOf(TextComponent("另一段正文")), null, null), auth))
            NovelDownloadStore.exportBackup(backup)

            // Reject archive missing chapter inventory without harming existing downloads.
            val invalidBackup = Files.createTempDirectory("invalid-restore-").toFile()
            try {
                NovelDownloadStore.initializeDirectory(root)
                save(1, listOf(chapter(1)))
                NovelDownloadStore.exportBackup(invalidBackup)
                val manifest = invalidBackup.walkTopDown().single { it.name == "manifest.json" }
                val json = com.google.gson.JsonParser.parseString(manifest.readText()).asJsonObject
                json.remove("chapters")
                manifest.writeText(json.toString())
                assertThrows(IllegalArgumentException::class.java) {
                    NovelDownloadStore.importBackup(invalidBackup, replaceExisting = true)
                }
                NovelDownloadStore.initializeDirectory(root)
                assertNotNull(NovelDownloadStore.readChapter(chapter(1).url, novelUrl))
            } finally { invalidBackup.deleteRecursively() }

            val first = Gson().fromJson(File(requireNotNull(backup.listFiles()).first(), "manifest.json").readText(),
                DownloadedNovelManifest::class.java)
            for (cancelAfterCommit in listOf(true, false)) {
                root.deleteRecursively()
                NovelDownloadStore.initializeDirectory(root)
                NovelDownloadStore.saveChapter(first.name, novelUrl, "", listOf(chapter(1)), chapter(1), detail(1), auth)
                try {
                    NovelDownloadStore.importBackup(backup, replaceExisting = true) {
                        val committed = root.listFiles().orEmpty().filter { it.isDirectory && !it.name.endsWith(".tmp") }
                            .map { File(it, "manifest.json") }.any { file ->
                                file.isFile && Gson().fromJson(file.readText(), DownloadedNovelManifest::class.java).url == first.url
                            }
                        if (cancelAfterCommit && committed) {
                            throw kotlinx.coroutines.CancellationException("Cancelled after first book committed")
                        }
                    }
                    assertFalse(cancelAfterCommit)
                } catch (_: kotlinx.coroutines.CancellationException) {
                    assertTrue(cancelAfterCommit)
                }
                NovelDownloadStore.initializeDirectory(incoming)
                NovelDownloadStore.initializeDirectory(root)
                if (cancelAfterCommit) {
                    assertNotNull(NovelDownloadStore.readChapter(chapter(1).url, novelUrl))
                    assertNull(NovelDownloadStore.manifest(first.url))
                } else {
                    assertNull(NovelDownloadStore.manifest(novelUrl))
                    assertNotNull(NovelDownloadStore.readChapter(otherChapter.url, otherUrl))
                    assertNotNull(NovelDownloadStore.readChapter(laterChapter.url, laterUrl))
                }
            }
        } finally {
            incoming.deleteRecursively()
            backup.deleteRecursively()
            NovelDownloadStore.initializeDirectory(root)
        }
    }

    @Test
    fun failedDatabaseRestoreKeepsTheOriginalDownloadsAcrossARescan() = withStorage { root ->
        val incoming = Files.createTempDirectory("restore-rollback-incoming-").toFile()
        val backup = Files.createTempDirectory("restore-rollback-backup-").toFile()
        val replacement = Chapter("Replacement", "/forum/9002/1.html", false)
        val replacementUrl = "/detail/9002.html"
        try {
            NovelDownloadStore.initializeDirectory(incoming)
            NovelDownloadStore.saveChapter("Replacement", replacementUrl, "", listOf(replacement), replacement,
                DetailedChapter("Replacement", listOf(TextComponent("new body")), null, null), auth)
            NovelDownloadStore.exportBackup(backup)
            NovelDownloadStore.initializeDirectory(root)
            save(1, listOf(chapter(1)))
            assertThrows(java.io.IOException::class.java) {
                runBlocking {
                    NovelDownloadStore.withRestoredBackup(backup, {}) {
                        assertFalse(NovelDownloadStore.delete(replacementUrl))
                        throw java.io.IOException("Fixture database commit failure")
                    }
                }
            }
            NovelDownloadStore.initializeDirectory(incoming)
            NovelDownloadStore.initializeDirectory(root)
            assertNotNull(NovelDownloadStore.readChapter(chapter(1).url, novelUrl))
            assertNull(NovelDownloadStore.manifest(replacementUrl))
        } finally {
            incoming.deleteRecursively()
            backup.deleteRecursively()
        }
    }

    // --- Incremental download, concurrency, images, crash recovery, search, unlock, storage switch ---

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
        assertEquals("正文 1", NovelDownloadStore.readChapter(chapter(1).url)!!.toReaderDocument(chapter(1)).blocks.single().sourceText())
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
    fun imageTimeoutKeepsReadableBodyCountsProgressAndAllowsLaterRepair() = withStorage { root ->
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
        File(directory, manifest.chapters.first().fileName).writeText(Gson().toJson(DownloadedChapterContent(
            "Chapter 1", chapter(1).url,
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
            val partial = runBlocking { NovelDownloadStore.download(auth, novel(order), concurrency = 1) { progress += it } }
            assertTrue(requestedImages.isNotEmpty() && requestedImages.all { it == imageUrl })
            assertTrue(partial.chapters.first().bodyAvailable && partial.chapters.first().needsImages)
            assertFalse(partial.chapters.first().downloaded)
            assertTrue(partial.chapters.last().downloaded)
            assertFalse(partial.complete)
            assertEquals(2, progress.last().completed)
            assertTrue(progress.all { it.completed in 0..it.total })
            assertEquals(2, partial.savedChapterCount)
            assertEquals(1, partial.pendingImageChapterCount)
            val offline = requireNotNull(NovelDownloadStore.readChapter(chapter(1).url))
            assertEquals(body, offline.body)
            assertEquals(imageUrl, (offline.toReaderDocument(chapter(1)).blocks.last() as
                com.breakyuna.esjzone.domain.reader.ReaderBlock.Image).url)
            assertArrayEquals(byteArrayOf(1, 2, 3), existingImage.readBytes())

            // Repeated image failure must not double-count already durable bodies.
            progress.clear()
            runBlocking { NovelDownloadStore.download(auth, novel(order), concurrency = 1) { progress += it } }
            assertTrue(progress.all { it.completed == 2 && it.total == 2 })

            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(imageUrl.toByteArray())
                .joinToString("") { "%02x".format(it) }
            File(directory, "images/image-$digest.png").writeBytes(byteArrayOf(4, 5, 6))
            NovelDownloadStore.initializeDirectory(root)
            requestedImages.clear()
            val repaired = runBlocking { NovelDownloadStore.download(auth, novel(order)) }
            assertTrue(repaired.complete)
            assertEquals(0, repaired.pendingImageChapterCount)
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
        assertEquals("恢复正文", NovelDownloadStore.readChapter(chapter(2).url)!!.body!!
            .readerBlocks().single().sourceText())
        assertNull(NovelDownloadStore.readChapter(chapter(3).url))
        save(3, order)
        assertEquals("恢复正文", NovelDownloadStore.readChapter(chapter(2).url)!!.body!!
            .readerBlocks().single().sourceText())
        awaitMaintenance { NovelDownloadStore.manifest(novelUrl)!!.chapters[1].bodyAvailable }
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
    fun historyProgressRefreshesAfterCatalogChangesAndDeletion() = withStorage { root ->
        val order = listOf(chapter(1), chapter(2))
        val saved = save(1, order)
        File(root.listFiles()!!.single(), "manifest.json").writeText(Gson().toJson(saved.copy(catalogComplete = true)))
        NovelDownloadStore.initializeDirectory(root)
        val row = LocalReadingActivity("local:9001", "9001", "Novel", novelUrl,
            chapter(1).url, "Chapter 1", 0, 2, 0.5f, 1L, 2L, 0L)
        assertEquals(0.25f, NovelDownloadStore.refreshProgress(row).bookProgress!!, 0.0001f)

        save(2, order + chapter(3))
        val expanded = NovelDownloadStore.refreshProgress(row)
        assertEquals(3, expanded.totalChapters)
        assertEquals(1f / 6f, expanded.bookProgress!!, 0.0001f)
        assertEquals(row.anchor, expanded.anchor)
        assertEquals(1f / 3f, NovelDownloadStore.refreshProgress(row.copy(chapterProgress = 1f)).bookProgress!!, 0.0001f)
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
}
