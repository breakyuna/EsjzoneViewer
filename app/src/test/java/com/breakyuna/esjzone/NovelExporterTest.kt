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
