package com.breakyuna.esjzone.offline

import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import org.jsoup.nodes.TextNode

object NovelExporter {

    fun suggestedFileName(title: String, extension: String): String {
        val safeTitle = Normalizer.normalize(title, Normalizer.Form.NFKC)
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim(' ', '.')
            .take(80)
            .ifBlank { "novel" }
        return "$safeTitle.${extension.lowercase(Locale.ROOT)}"
    }

    fun exportTxt(
        manifest: DownloadedNovelManifest,
        chapterLoader: (DownloadedChapterRecord) -> DownloadedChapterContent?,
        output: OutputStream
    ) {
        output.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.appendLine(manifest.name)
            if (manifest.author.isNotBlank()) writer.appendLine("作者：${manifest.author}")
            writer.appendLine()
            if (manifest.description.isNotBlank()) {
                writer.appendLine(manifest.description)
                writer.appendLine()
            }
            manifest.chapters.filter { it.downloaded }.forEach { record ->
                val chapter = chapterLoader(record) ?: return@forEach
                writer.appendLine(chapter.name.ifBlank { record.name })
                writer.appendLine()
                chapter.components.forEach { component ->
                    when (component.type) {
                        "image" -> writer.appendLine("[图片：${component.value}]")
                        else -> writer.appendLine(component.value)
                    }
                }
                writer.appendLine()
            }
        }
    }

    fun exportEpub(
        manifest: DownloadedNovelManifest,
        chapterLoader: (DownloadedChapterRecord) -> DownloadedChapterContent?,
        output: OutputStream,
        imageLoader: (DownloadedComponent) -> File? = { null }
    ) {
        val chapters = manifest.chapters
            .filter { it.downloaded }
            .mapNotNull { record -> chapterLoader(record)?.let { record to it } }
        require(chapters.isNotEmpty()) { "No downloaded chapters are available for export" }
        val images = linkedMapOf<String, EpubImage>()
        chapters.forEach { (_, chapter) ->
            chapter.components.filter { it.type == "image" }.forEach imageLoop@{ component ->
                val key = component.imageKey()
                val file = imageLoader(component)?.takeIf(File::isFile) ?: return@imageLoop
                if (!images.containsKey(key)) {
                    val extension = file.extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,5}")) }
                        ?.lowercase(Locale.ROOT)
                        ?: "img"
                    images[key] = EpubImage(
                        file = file,
                        href = "images/image-${images.size + 1}.$extension",
                        mediaType = component.mediaType
                            ?.takeIf { it.startsWith("image/") }
                            ?: mediaTypeFor(extension)
                    )
                }
            }
        }

        ZipOutputStream(output.buffered()).use { zip ->
            addStoredEntry(zip, "mimetype", "application/epub+zip")
            addTextEntry(zip, "META-INF/container.xml", containerXml())
            addTextEntry(zip, "OEBPS/content.opf", contentOpf(manifest, chapters.size, images.values))
            addTextEntry(zip, "OEBPS/nav.xhtml", navigationXhtml(manifest, chapters))
            chapters.forEachIndexed { index, (_, chapter) ->
                addTextEntry(
                    zip,
                    "OEBPS/chapter-${index + 1}.xhtml",
                    chapterXhtml(chapter, index + 1, images)
                )
            }
            images.values.forEach { image ->
                addBinaryEntry(zip, "OEBPS/${image.href}", image.file)
            }
        }
    }

    private fun containerXml() = """<?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private fun contentOpf(
        manifest: DownloadedNovelManifest,
        chapterCount: Int,
        images: Collection<EpubImage>
    ): String {
        val modified = Instant.ofEpochMilli(manifest.downloadedAt.coerceAtLeast(0L))
            .truncatedTo(ChronoUnit.SECONDS)
            .toString()
        val chapterManifest = (1..chapterCount).joinToString("\n") { index ->
            "    <item id=\"chapter-$index\" href=\"chapter-$index.xhtml\" media-type=\"application/xhtml+xml\"/>"
        }
        val spine = (1..chapterCount).joinToString("\n") { index ->
            "    <itemref idref=\"chapter-$index\"/>"
        }
        val imageManifest = images.mapIndexed { index, image ->
            "    <item id=\"image-${index + 1}\" href=\"${xml(image.href)}\" media-type=\"${xml(image.mediaType)}\"/>"
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="zh-CN">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="book-id">${xml(manifest.url)}</dc:identifier>
                <dc:title>${xml(manifest.name)}</dc:title>
                <dc:language>zh-CN</dc:language>
                ${if (manifest.author.isNotBlank()) "<dc:creator>${xml(manifest.author)}</dc:creator>" else ""}
                <meta property="dcterms:modified">$modified</meta>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            $chapterManifest
            $imageManifest
              </manifest>
              <spine>
            $spine
              </spine>
            </package>
        """.trimIndent()
    }

    private fun navigationXhtml(
        manifest: DownloadedNovelManifest,
        chapters: List<Pair<DownloadedChapterRecord, DownloadedChapterContent>>
    ): String {
        val links = chapters.mapIndexed { index, (record, chapter) ->
            "        <li><a href=\"chapter-${index + 1}.xhtml\">${xml(chapter.name.ifBlank { record.name })}</a></li>"
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="zh-CN" xml:lang="zh-CN">
              <head><title>${xml(manifest.name)}</title></head>
              <body>
                <nav epub:type="toc" id="toc">
                  <h1>${xml(manifest.name)}</h1>
                  <ol>
            $links
                  </ol>
                </nav>
              </body>
            </html>
        """.trimIndent()
    }

    private fun chapterXhtml(
        chapter: DownloadedChapterContent,
        index: Int,
        images: Map<String, EpubImage>
    ): String {
        val body = richChapterHtml(chapter, images) ?: chapter.components.joinToString("\n") { component ->
            when (component.type) {
                "image" -> images[component.imageKey()]?.let { image ->
                    "    <div class=\"image\"><img src=\"${xml(image.href)}\" alt=\"插图\"/></div>"
                } ?: "    <p class=\"image\">[图片：${xml(component.value)}]</p>"
                else -> "    <p>${xml(component.value).replace("\n", "<br/>")}</p>"
            }
        }
        val title = chapter.name.ifBlank { "第 $index 章" }
        return """<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN" xml:lang="zh-CN">
              <head>
                <title>${xml(title)}</title>
                <meta charset="UTF-8"/>
                <style>body{line-height:1.8;padding:5%;}h1{font-size:1.5em;}p{margin:0 0 1em;word-break:break-word;}img{display:block;max-width:100%;height:auto;margin:1em auto;}</style>
              </head>
              <body>
                <h1>${xml(title)}</h1>
            $body
              </body>
            </html>
        """.trimIndent()
    }

    private fun addStoredEntry(zip: ZipOutputStream, name: String, value: String) {
        val bytes = value.toByteArray(StandardCharsets.US_ASCII)
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun addTextEntry(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun addBinaryEntry(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun DownloadedComponent.imageKey(): String = localFile ?: value

    private fun mediaTypeFor(extension: String): String = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    private fun richChapterHtml(
        chapter: DownloadedChapterContent,
        images: Map<String, EpubImage>
    ): String? {
        val html = chapter.contentHtml?.takeIf(String::isNotBlank) ?: return null
        val document = Jsoup.parseBodyFragment(html, chapter.baseUrl ?: chapter.url)
        document.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .prettyPrint(false)
        document.select(
            "script, iframe, object, embed, form, input, button, video, audio, source"
        ).remove()
        document.allElements.forEach { element ->
            element.attributes().asList()
                .filter { it.key.startsWith("on", ignoreCase = true) }
                .forEach { element.removeAttr(it.key) }
        }

        val imageComponents = chapter.components.filter { it.type == "image" }
        document.select("img").forEach { image ->
            val candidates = IMAGE_URL_ATTRIBUTES.mapNotNull { attribute ->
                image.absUrl(attribute)
                    .ifBlank { image.attr(attribute) }
                    .trim()
                    .takeIf(String::isNotBlank)
            }
            val component = imageComponents.firstOrNull { it.value in candidates }
            val exportedImage = component?.let { images[it.imageKey()] }
            if (exportedImage != null) {
                IMAGE_URL_ATTRIBUTES.forEach(image::removeAttr)
                image.removeAttr("srcset")
                image.attr("src", exportedImage.href)
            } else {
                val original = component?.value ?: candidates.firstOrNull().orEmpty()
                image.replaceWith(TextNode("[图片：$original]"))
            }
        }
        return document.body().html()
    }

    private data class EpubImage(
        val file: File,
        val href: String,
        val mediaType: String
    )

    private val IMAGE_URL_ATTRIBUTES = listOf(
        "data-src",
        "data-original",
        "data-lazy-src",
        "src"
    )

    private fun xml(value: String): String = value
        .filter { character ->
            character == '\t' || character == '\n' || character == '\r' || character.code >= 0x20
        }
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
