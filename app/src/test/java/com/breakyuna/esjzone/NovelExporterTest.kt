package com.breakyuna.esjzone

import com.breakyuna.esjzone.offline.DownloadedChapterContent
import com.breakyuna.esjzone.offline.DownloadedChapterRecord
import com.breakyuna.esjzone.offline.DownloadedComponent
import com.breakyuna.esjzone.offline.DownloadedNovelManifest
import com.breakyuna.esjzone.offline.NovelExporter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelExporterTest {

    @Test
    fun structuredTxtUsesBodyAndEpubRetainsRichSource() {
        val record = manifest().chapters.single()
        val body = com.breakyuna.esjzone.data.reader.ChapterBody.fromBlocks(listOf(
            com.breakyuna.esjzone.domain.reader.ReaderBlock.Paragraph(listOf(
                com.breakyuna.esjzone.domain.reader.ReaderBlock.Text("结构化正文")))
        ))
        val snapshot = chapter(record).copy(schemaVersion = 2, body = body,
            components = listOf(DownloadedComponent("text", "旧简化文本")),
            contentHtml = "<p>结构化正文<a href='https://example.com/reference'>引用</a></p><table><tr><td>表格</td></tr></table>")
        val txt = ByteArrayOutputStream()
        NovelExporter.exportTxt(manifest(), { snapshot }, txt)
        assertTrue(txt.toString("UTF-8").contains("结构化正文"))
        assertTrue(!txt.toString("UTF-8").contains("旧简化文本"))
        val epub = ByteArrayOutputStream()
        NovelExporter.exportEpub(manifest(), { snapshot }, epub)
        assertTrue(epubChapter(epub).contains("https://example.com/reference"))
        assertTrue(epubChapter(epub).contains("<table>"))
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
        val image = File.createTempFile("selected-chapter", ".png")
        val loadedImages = mutableListOf<String>()
        try {
            image.writeBytes(byteArrayOf(1, 2, 3))
            NovelExporter.exportEpub(manifest().copy(chapters = records, complete = false), { record ->
                chapter(record).copy(contentHtml = null, components = listOf(
                    DownloadedComponent("text", "Body ${record.index}"),
                    DownloadedComponent("image", "image-${record.index}", mediaType = "image/png")
                ))
            }, output, imageLoader = { component ->
                loadedImages += component.value
                image
            }, selectedChapterUrls = linkedSetOf(records[2].url, records[0].url))
            val entries = mutableMapOf<String, String>()
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries[entry.name] = zip.readBytes().toString(StandardCharsets.UTF_8)
                    entry = zip.nextEntry
                }
            }
            assertEquals(listOf("image-0", "image-2"), loadedImages)
            assertTrue(entries.getValue("OEBPS/chapter-1.xhtml").contains("Body 0"))
            assertTrue(entries.getValue("OEBPS/chapter-2.xhtml").contains("Body 2"))
            assertTrue(!entries.containsKey("OEBPS/chapter-3.xhtml"))
            val navigation = entries.getValue("OEBPS/nav.xhtml")
            assertTrue(!navigation.contains("Chapter 2"))
            assertTrue(navigation.indexOf("Chapter 1") < navigation.indexOf("Chapter 3"))
            assertTrue(!entries.getValue("OEBPS/content.opf").contains("chapter-3"))
            assertEquals(2, entries.keys.count { it.startsWith("OEBPS/images/") })
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
        val image = File.createTempFile("novel-export-test", ".png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }

        NovelExporter.exportEpub(
            manifest = manifest(),
            chapterLoader = ::chapter,
            output = output,
            imageLoader = { image }
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
        val chapterXhtml = entries.getValue("OEBPS/chapter-1.xhtml").second
        assertTrue(chapterXhtml.contains("章节正文"))
        assertTrue(chapterXhtml.contains("<strong>"))
        assertTrue(chapterXhtml.contains("<ruby>"))
        assertTrue(chapterXhtml.contains("<img"))
        assertTrue(entries.containsKey("OEBPS/images/image-1.png"))
    }

    @Test
    fun suggestedFileName_replacesCharactersRejectedByDocumentProviders() {
        assertEquals("书_名_.epub", NovelExporter.suggestedFileName("书/名?", "EPUB"))
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
    fun epubExport_rejectsEmbeddedSvgAssets() {
        val output = ByteArrayOutputStream()
        val svg = File.createTempFile("novel-active-image", ".svg")
        try {
            svg.writeText("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>")
            NovelExporter.exportEpub(manifest(), ::chapter, output, imageLoader = { svg })
            assertTrue(!epubChapter(output).contains("<img"))
        } finally { svg.delete() }
    }

    private fun epubChapter(output: ByteArrayOutputStream): String =
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "OEBPS/chapter-1.xhtml") {
                    return@use zip.readBytes().toString(StandardCharsets.UTF_8)
                }
                entry = zip.nextEntry
            }
            error("Exported chapter missing")
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
            <p><img data-src="https://example.com/illustration.jpg"></p>
        """.trimIndent(),
        baseUrl = "https://www.esjzone.cc/forum/1/1.html"
    )
}
