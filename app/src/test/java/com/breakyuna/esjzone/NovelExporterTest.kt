package com.breakyuna.esjzone

import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.offline.DownloadedChapterContent
import com.breakyuna.esjzone.offline.DownloadedChapterRecord
import com.breakyuna.esjzone.offline.DownloadedComponent
import com.breakyuna.esjzone.offline.DownloadedNovelManifest
import com.breakyuna.esjzone.offline.NovelExporter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import com.breakyuna.esjzone.storage.StorageFile
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.jsoup.Jsoup
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelExporterTest {

    @Test
    fun savedBodyWithMissingImagesRemainsSelectableForTxtAndEpub() {
        val original = manifest()
        val record = original.chapters.single().copy(downloaded = false, bodyAvailable = true)
        val partial = original.copy(chapters = listOf(record), complete = false, catalogComplete = true)
        val txt = ByteArrayOutputStream()
        NovelExporter.exportTxt(partial, ::chapter, txt, selectedChapterUrls = setOf(record.url))
        assertTrue(txt.toString("UTF-8").contains("章节正文"))
        val epub = ByteArrayOutputStream()
        NovelExporter.exportEpub(partial, ::chapter, epub, imageLoader = { null },
            selectedChapterUrls = setOf(record.url))
        assertTrue(epubChapter(epub).contains("章节正文"))
        assertTrue(epubChapter(epub).contains("[图片：https://example.com/illustration.jpg]"))
    }

    @Test
    fun structuredTxtUsesBodyAndEpubRetainsRichSource() {
        val record = manifest().chapters.single()
        val body = ChapterBody.fromBlocks(listOf(
            ReaderBlock.Paragraph(listOf(ReaderBlock.Text("结构化正文")))
        ))
        val snapshot = chapter(record).copy(schemaVersion = 2, body = body,
            components = listOf(DownloadedComponent("text", "旧简化文本")),
            contentHtml = """
                <p>结构化正文<a href="https://example.com/reference">引用</a></p>
                <table><tr><td>表格</td></tr></table>
                <ol type="A" start="2"><li>正文中的 B 项</li></ol>
            """.trimIndent())
        val txt = ByteArrayOutputStream()
        NovelExporter.exportTxt(manifest(), { snapshot }, txt)
        assertTrue(txt.toString("UTF-8").contains("结构化正文"))
        assertTrue(!txt.toString("UTF-8").contains("旧简化文本"))
        val epub = ByteArrayOutputStream()
        NovelExporter.exportEpub(manifest(), { snapshot }, epub)
        val xhtml = epubChapter(epub)
        assertTrue(xhtml.contains("https://example.com/reference"))
        assertTrue(xhtml.contains("<table>"))
        val orderedList = requireNotNull(Jsoup.parse(xhtml).selectFirst("ol"))
        assertEquals("A", orderedList.attr("type"))
        assertEquals("2", orderedList.attr("start"))
    }

    @Test
    fun structuredTxtWritesOneNewlineForEachBlankLineBlock() {
        val manifest = manifest()
        val snapshot = chapter(manifest.chapters.single()).copy(schemaVersion = 2, body = ChapterBody.fromBlocks(
            listOf(ReaderBlock.Text("第一段"), ReaderBlock.LineBreak, ReaderBlock.LineBreak, ReaderBlock.Text("第二段"))
        ))
        val output = ByteArrayOutputStream()

        NovelExporter.exportTxt(manifest, { snapshot }, output)

        val exported = output.toString("UTF-8")
        assertEquals("\n\n\n", exported.substringAfter("第一段").substringBefore("第二段"))
    }


    @Test
    fun selectedTxtChaptersFollowDirectoryOrderAndExcludeOtherContent() {
        val records = (1..3).map { number ->
            manifest().chapters.single().copy(index = number - 1, name = "Chapter $number", url = "/forum/1/$number.html")
        }
        val output = ByteArrayOutputStream()
        val loaded = mutableListOf<String>()
        NovelExporter.exportTxt(manifest().copy(chapters = records, complete = false), {
            loaded += it.url
            chapter(it)
        }, output, selectedChapterUrls = linkedSetOf(records[2].url, records[0].url))
        val text = output.toString(StandardCharsets.UTF_8.name())
        assertEquals(listOf(records[0].url, records[2].url), loaded)
        assertTrue(text.indexOf("Chapter 1") < text.indexOf("Chapter 3"))
        assertTrue(!text.contains("Chapter 2"))
    }

    @Test
    fun selectedEpubChaptersLimitContentsSpineAndImages() {
        val records = (1..3).map { number ->
            manifest().chapters.single().copy(index = number - 1, name = "Chapter $number", url = "/forum/1/$number.html")
        }
        val output = ByteArrayOutputStream()
        val image = File.createTempFile("selected-chapter", ".file")
        val loadedChapters = mutableListOf<String>()
        val loadedImages = mutableListOf<String>()
        try {
            image.writeBytes(SAMPLE_PNG)
            NovelExporter.exportEpub(manifest().copy(chapters = records, complete = false), { record ->
                loadedChapters += record.url
                chapter(record).copy(contentHtml = null, components = listOf(
                    DownloadedComponent("text", "Body ${record.index}"),
                    DownloadedComponent("image", "image-${record.index}", mediaType = "application/octet-stream")
                ))
            }, output, imageLoader = { component ->
                loadedImages += component.value
                StorageFile(image)
            }, selectedChapterUrls = linkedSetOf(records[2].url, records[0].url))
            val entries = mutableMapOf<String, String>()
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries[entry.name] = zip.readBytes().toString(StandardCharsets.UTF_8)
                    entry = zip.nextEntry
                }
            }
            assertEquals(listOf(records[0].url, records[2].url), loadedChapters)
            assertEquals(listOf("image-0", "image-2"), loadedImages)
            assertTrue(entries.getValue("OEBPS/chapter-1.xhtml").contains("Body 0"))
            assertTrue(entries.getValue("OEBPS/chapter-2.xhtml").contains("Body 2"))
            assertTrue(!entries.containsKey("OEBPS/chapter-3.xhtml"))
            val navigation = entries.getValue("OEBPS/nav.xhtml")
            assertTrue(!navigation.contains("Chapter 2"))
            assertTrue(navigation.indexOf("Chapter 1") < navigation.indexOf("Chapter 3"))
            val opf = entries.getValue("OEBPS/content.opf")
            assertTrue(!opf.contains("chapter-3"))
            assertTrue(!opf.contains("application/octet-stream"))
            assertEquals(setOf("OEBPS/images/image-1.png", "OEBPS/images/image-2.png"),
                entries.keys.filter { it.startsWith("OEBPS/images/") }.toSet())
        } finally { image.delete() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyChapterSelectionIsRejected() {
        NovelExporter.exportTxt(manifest(), ::chapter, ByteArrayOutputStream(), selectedChapterUrls = emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun selectedUndownloadedChapterIsRejected() {
        val original = manifest()
        NovelExporter.exportEpub(original.copy(chapters = original.chapters.map { it.copy(downloaded = false) }),
            ::chapter, ByteArrayOutputStream(), selectedChapterUrls = setOf(original.chapters.single().url))
    }

    @Test
    fun txtExport_writesMetadataChaptersAndImageReferences() {
        val output = ByteArrayOutputStream()

        NovelExporter.exportTxt(manifest(), ::chapter, output)

        val text = output.toString(StandardCharsets.UTF_8.name())
        assertTrue(text.contains("测试小说"))
        assertTrue(text.contains("作者：作者"))
        assertTrue(text.contains("第一章"))
        assertTrue(text.contains("章节正文"))
        assertTrue(text.contains("[图片：https://example.com/illustration.jpg]"))
    }

    @Test
    fun epubExport_writesUncompressedMimetypeFirstAndRequiredDocuments() {
        val output = ByteArrayOutputStream()
        val image = File.createTempFile("novel-export-test", ".jpg").apply {
            writeBytes(SAMPLE_PNG)
            deleteOnExit()
        }
        val record = manifest().chapters.single()
        val original = chapter(record)
        val component = original.components.single { it.type == "image" }
        val snapshot = original.copy(
            components = original.components.map { if (it == component) it.copy(mediaType = "image/jpeg") else it },
            contentHtml = original.contentHtml.orEmpty() +
                """<p><img src="${component.value}" alt="原图说明"><img src="${component.value}" alt=""></p>"""
        )

        NovelExporter.exportEpub(
            manifest = manifest(),
            chapterLoader = { snapshot },
            output = output,
            imageLoader = { StorageFile(image) }
        )

        val entries = mutableMapOf<String, Pair<Int, String>>()
        val order = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                order += entry.name
                entries[entry.name] = entry.method to
                    zip.readBytes().toString(StandardCharsets.UTF_8)
                entry = zip.nextEntry
            }
        }

        assertEquals("mimetype", order.first())
        assertEquals(ZipEntry.STORED, entries.getValue("mimetype").first)
        assertEquals("application/epub+zip", entries.getValue("mimetype").second)
        assertTrue(entries.containsKey("META-INF/container.xml"))
        assertTrue(entries.containsKey("OEBPS/content.opf"))
        assertTrue(entries.containsKey("OEBPS/nav.xhtml"))
        assertEquals(listOf("OEBPS/images.css"), order.filter { it.endsWith(".css") })
        assertEquals("img { max-width: 100%; height: auto; }", entries.getValue("OEBPS/images.css").second)
        assertTrue(entries.getValue("OEBPS/content.opf").second.contains("href=\"images.css\" media-type=\"text/css\""))
        entries.filterKeys { it.endsWith(".xhtml") }.values.forEach { (_, document) ->
            assertTrue(!document.contains("<style") && !document.contains("style="))
        }
        val chapterXhtml = entries.getValue("OEBPS/chapter-1.xhtml").second
        assertTrue(chapterXhtml.contains("rel=\"stylesheet\" href=\"images.css\""))
        assertTrue(chapterXhtml.contains("章节正文"))
        assertTrue(chapterXhtml.contains("<strong>"))
        assertTrue(chapterXhtml.contains("<ruby>"))
        assertTrue(chapterXhtml.contains("<img"))
        assertTrue(!chapterXhtml.contains("align=") && !chapterXhtml.contains("width=") &&
            !chapterXhtml.contains("height="))
        val exportedImages = Jsoup.parse(chapterXhtml).select("img")
        assertEquals(listOf("插图", "原图说明", ""), exportedImages.map { it.attr("alt") })
        assertTrue(exportedImages.all { it.hasAttr("alt") && it.attr("src") == "images/image-1.png" })
        val opf = entries.getValue("OEBPS/content.opf").second
        assertTrue(opf.contains("href=\"images/image-1.png\" media-type=\"image/png\""))
        assertTrue(!opf.contains("image/jpeg"))
        assertArrayEquals(SAMPLE_PNG, epubEntry(output, "OEBPS/images/image-1.png"))
    }

    @Test
    fun suggestedFileName_replacesCharactersRejectedByDocumentProviders() {
        assertEquals("书_名_.epub", NovelExporter.suggestedFileName("书/名?", "EPUB"))
    }

    @Test
    fun epubKeepsParagraphsAndRemovesSourceIndentAcrossStyledRuns() {
        val output = ByteArrayOutputStream()
        NovelExporter.exportEpub(
            manifest(),
            { record -> chapter(record).copy(contentHtml = """
                <div>　首段　行内<br><span>&nbsp;　续行</span><br><strong>　　新段</strong><br><br>　末段</div>
                <p><strong>　段首</strong>　段内<br>　硬换行<br><em>　</em><strong>　另一段</strong></p>
            """.trimIndent()) },
            output
        )

        val xhtml = epubChapter(output).replace(Regex("<br\\s*/>"), "<br/>")
        assertTrue(xhtml.contains("<p>首段　行内<br/><span>续行</span></p>"))
        assertTrue(xhtml.contains("<p><strong>新段</strong></p>"))
        assertTrue(xhtml.contains("<p><br/></p><p>末段</p>"))
        assertTrue(xhtml.contains("<strong>段首</strong>　段内<br/>硬换行</p>"))
        assertTrue(xhtml.contains("<p><em></em><strong>另一段</strong></p>"))
    }

    @Test
    fun epubSplitsParagraphsAtNestedBreaksAndKeepsInlineFormatting() {
        val output = ByteArrayOutputStream()
        val url = "https://example.com/reference"
        NovelExporter.exportEpub(manifest(), { record ->
            chapter(record).copy(contentHtml = """
                <p><a href="$url"><strong>首段<br><em>　　次段<br> <br>末段</em></strong></a></p>
                <p><strong>同段<br>　续行</strong></p>
            """.trimIndent())
        }, output)

        val xhtml = epubChapter(output).replace(Regex("<br\\s*/>"), "<br/>")
        assertTrue(xhtml.contains("""<p><a href="$url"><strong>首段</strong></a></p>"""))
        assertTrue(xhtml.contains("""<p><a href="$url"><strong><em>次段</em></strong></a></p>"""))
        assertTrue(xhtml.contains("""<p><br/></p><p><a href="$url"><strong><em>末段</em></strong></a></p>"""))
        assertTrue(xhtml.contains("<p><strong>同段<br/>续行</strong></p>"))
    }

    @Test
    fun epubClearsSourceIndentInLegacyChapters() {
        val output = ByteArrayOutputStream()
        NovelExporter.exportEpub(manifest(), { record ->
            chapter(record).copy(contentHtml = null, components = listOf(
                DownloadedComponent("text", "　开头\r\n\u00a0续行　保留")
            ))
        }, output)

        assertTrue(epubChapter(output).contains("<p>开头<br/>续行　保留</p>"))
        val cover = epubEntry(output, "OEBPS/cover.xhtml").toString(StandardCharsets.UTF_8)
        assertTrue(cover.contains("<h1>测试小说</h1>"))
    }

    @Test
    fun epubEmbedsCachedCoverWithItsActualMediaTypeAndFirstSpineEntry() {
        val output = ByteArrayOutputStream()
        val cover = File.createTempFile("novel-cover", ".img")
        try {
            cover.writeBytes(SAMPLE_PNG)
            NovelExporter.exportEpub(manifest(), ::chapter, output, coverFiles = sequenceOf(StorageFile(cover)))

            assertArrayEquals(SAMPLE_PNG, epubEntry(output, "OEBPS/images/cover.png"))
            val opf = epubEntry(output, "OEBPS/content.opf").toString(StandardCharsets.UTF_8)
            assertTrue(opf.contains("href=\"images/cover.png\" media-type=\"image/png\" properties=\"cover-image\""))
            assertTrue(opf.substringAfter("<spine>").trimStart().startsWith("<itemref idref=\"cover-page\"/>"))
            val page = epubEntry(output, "OEBPS/cover.xhtml").toString(StandardCharsets.UTF_8)
            assertTrue(page.contains("<img src=\"images/cover.png\" alt=\"测试小说\"/>"))
            assertTrue(page.contains("rel=\"stylesheet\" href=\"images.css\""))
        } finally {
            cover.delete()
        }
    }

    @Test
    fun epubSkipsUnreadableAndUnsupportedCoversBeforeWritingZipEntry() {
        val output = ByteArrayOutputStream()
        val missing = File(System.getProperty("java.io.tmpdir"), "missing-cover-${System.nanoTime()}.img")
        val invalid = File.createTempFile("invalid-cover", ".img")
        val valid = File.createTempFile("valid-cover", ".img")
        try {
            invalid.writeText("not an image")
            valid.writeBytes(SAMPLE_PNG)
            NovelExporter.exportEpub(
                manifest(),
                ::chapter,
                output,
                coverFiles = sequenceOf(StorageFile(missing), StorageFile(invalid), StorageFile(valid))
            )
            assertArrayEquals(SAMPLE_PNG, epubEntry(output, "OEBPS/images/cover.png"))
        } finally {
            invalid.delete()
            valid.delete()
        }
    }

    @Test
    fun epubExport_removesActiveMarkupButKeepsRubyAndSafeLinks() {
        val output = ByteArrayOutputStream()
        NovelExporter.exportEpub(
            manifest(),
            { record -> chapter(record).copy(contentHtml = """
                <p style="background:url(https://invalid.example/pixel)">
                    <strong>safe text</strong><ruby>漢<rt>かん</rt></ruby>
                    <a href="javascript:alert(1)" onclick="alert(1)">unsafe link</a>
                    <a href="https://example.com/reference">reference</a>
                </p>
                <style>@import 'https://invalid.example/style';</style>
                <svg><a href="javascript:alert(1)">svg link</a></svg>
                <iframe src="https://invalid.example/"></iframe>
            """.trimIndent()) },
            output
        )
        val html = epubChapter(output)
        assertTrue(html.contains("<strong>safe text</strong>"))
        assertTrue(html.contains("<ruby>"))
        assertTrue(html.contains("https://example.com/reference"))
        listOf("javascript:", "onclick", "background:", "@import", "<svg", "<iframe", "invalid.example")
            .forEach { assertTrue("Unexpected active content: $it", !html.contains(it)) }
    }

    @Test
    fun epubExport_rejectsSvgEvenWhenSavedWithARasterExtension() {
        val output = ByteArrayOutputStream()
        val svg = File.createTempFile("novel-active-image", ".png")
        try {
            svg.writeText("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>")
            NovelExporter.exportEpub(manifest(), ::chapter, output, imageLoader = { StorageFile(svg) })
            val chapter = epubChapter(output)
            assertTrue(!chapter.contains("<img"))
            assertTrue(chapter.contains("[图片：https://example.com/illustration.jpg]"))
            assertTrue(epubEntryNames(output).none { it.startsWith("OEBPS/images/") })
            assertTrue(!epubEntry(output, "OEBPS/content.opf").toString(StandardCharsets.UTF_8).contains("images/"))
        } finally { svg.delete() }
    }

    @Test
    fun epubExport_skipsTruncatedAndRepeatedRejectedImagesWithoutReloading() {
        val records = (1..2).map { number ->
            manifest().chapters.single().copy(index = number - 1, name = "Chapter $number", url = "/forum/1/$number.html")
        }
        val output = ByteArrayOutputStream()
        val truncated = File.createTempFile("novel-truncated-image", ".png")
        var loads = 0
        try {
            truncated.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
            NovelExporter.exportEpub(
                manifest = manifest().copy(chapters = records),
                chapterLoader = { record ->
                    val base = chapter(record)
                    val image = base.components.single { it.type == "image" }
                    base.copy(components = base.components + image)
                },
                output = output,
                imageLoader = {
                    loads++
                    StorageFile(truncated)
                }
            )
            assertEquals(1, loads)
            assertTrue(epubEntryNames(output).none { it.startsWith("OEBPS/images/") })
            assertTrue(!epubEntry(output, "OEBPS/content.opf").toString(StandardCharsets.UTF_8).contains("images/"))
            listOf("OEBPS/chapter-1.xhtml", "OEBPS/chapter-2.xhtml").forEach { name ->
                val xhtml = epubEntry(output, name).toString(StandardCharsets.UTF_8)
                assertTrue(!xhtml.contains("<img"))
                assertTrue(xhtml.contains("[图片：https://example.com/illustration.jpg]"))
            }
        } finally { truncated.delete() }
    }

    private fun epubChapter(output: ByteArrayOutputStream): String =
        epubEntry(output, "OEBPS/chapter-1.xhtml").toString(StandardCharsets.UTF_8)

    private fun epubEntryNames(output: ByteArrayOutputStream): Set<String> = buildSet {
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                add(entry.name)
                entry = zip.nextEntry
            }
        }
    }

    private fun epubEntry(output: ByteArrayOutputStream, name: String): ByteArray =
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) {
                    return@use zip.readBytes()
                }
                entry = zip.nextEntry
            }
            error("Missing EPUB entry $name")
        }

    private fun manifest() = DownloadedNovelManifest(
        name = "测试小说",
        url = "https://www.esjzone.cc/detail/1.html",
        coverUrl = "",
        views = 1,
        likes = 2,
        words = 3,
        type = "原创",
        author = "作者",
        forumUrl = "/forum/1/1/",
        tags = emptyList(),
        isAdult = false,
        description = "简介",
        sourceUrl = null,
        updatedAt = null,
        chapters = listOf(
            DownloadedChapterRecord(
                index = 0,
                name = "第一章",
                url = "/forum/1/1.html",
                fileName = "chapter.json",
                downloaded = true
            )
        ),
        downloadedAt = 1_700_000_000_000L,
        complete = true
    )

    private fun chapter(record: DownloadedChapterRecord) = DownloadedChapterContent(
        name = record.name,
        url = record.url,
        components = listOf(
            DownloadedComponent("text", "章节正文"),
            DownloadedComponent(
                type = "image",
                value = "https://example.com/illustration.jpg",
                localFile = "images/illustration.png",
                mediaType = "image/png"
            )
        ),
        contentHtml = """
            <p><strong>章节正文</strong><br><ruby>漢<rt>かん</rt></ruby></p>
            <p><img data-src="https://example.com/illustration.jpg" align="right" width="300" height="200"></p>
        """.trimIndent(),
        baseUrl = "https://www.esjzone.cc/forum/1/1.html"
    )

    companion object {
        // Minimal 1x1 transparent PNG.
        private val SAMPLE_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, 0xc4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0a, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9c.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0d, 0x0a, 0x2d, 0xb4.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, 0xae.toByte(),
            0x42, 0x60, 0x82.toByte()
        )
    }
}
