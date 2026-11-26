package com.breakyuna.esjzone.offline

import com.breakyuna.esjzone.storage.StorageFile
import com.breakyuna.esjzone.ui.reader.ReaderLineIndent
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
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
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

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
        output: OutputStream,
        selectedChapterUrls: Set<String>? = null
    ) {
        val records = chaptersForExport(manifest, selectedChapterUrls)
        output.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.appendLine(manifest.name)
            if (manifest.author.isNotBlank()) writer.appendLine("作者：${manifest.author}")
            writer.appendLine()
            if (manifest.description.isNotBlank()) {
                writer.appendLine(manifest.description)
                writer.appendLine()
            }
            records.forEach { record ->
                val chapter = chapterLoader(record)
                    ?: error("Downloaded chapter ${record.index + 1} is unavailable for export")
                writer.appendLine(chapter.name.ifBlank { record.name })
                writer.appendLine()
                chapter.exportComponents().forEach { component ->
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
        imageLoader: (DownloadedComponent) -> StorageFile? = { null },
        selectedChapterUrls: Set<String>? = null,
        coverFiles: Sequence<StorageFile> = emptySequence()
    ) {
        val records = chaptersForExport(manifest, selectedChapterUrls)
        val chapterTitles = ArrayList<String>(records.size)
        val images = linkedMapOf<String, EpubImage>()
        val skippedImages = hashSetOf<String>()
        ZipOutputStream(output.buffered()).use { zip ->
            addStoredEntry(zip, "mimetype", "application/epub+zip")
            addTextEntry(zip, "META-INF/container.xml", containerXml())
            addTextEntry(zip, "OEBPS/images.css", "img { max-width: 100%; height: auto; }")
            val coverHref = writeCoverImage(zip, coverFiles)
            addTextEntry(zip, "OEBPS/cover.xhtml", coverXhtml(manifest, coverHref))
            records.forEachIndexed { index, record ->
                val chapter = chapterLoader(record)
                    ?: error("Downloaded chapter ${record.index + 1} is unavailable for export")
                chapterTitles += chapter.name.ifBlank { record.name }
                val components = chapter.exportComponents()
                val chapterImages = components.filter { it.type == "image" }
                chapterImages.forEach { component ->
                    val key = component.imageKey()
                    if (key in images || key in skippedImages) return@forEach
                    val file = imageLoader(component)?.takeIf(StorageFile::isFile)
                    val image = file?.let { writeChapterImage(zip, it, images.size + 1) }
                    if (image != null) images[key] = image else skippedImages += key
                }
                val chapterNumber = index + 1
                addTextEntry(
                    zip,
                    "OEBPS/chapter-$chapterNumber.xhtml",
                    chapterXhtml(chapter, chapterNumber, components, chapterImages, images)
                )
            }
            addTextEntry(zip, "OEBPS/content.opf", contentOpf(manifest, chapterTitles.size, images.values, coverHref))
            addTextEntry(zip, "OEBPS/nav.xhtml", navigationXhtml(manifest, chapterTitles))
        }
    }

    private fun chaptersForExport(
        manifest: DownloadedNovelManifest,
        selectedChapterUrls: Set<String>?
    ): List<DownloadedChapterRecord> {
        val chapters = manifest.chapters.filter {
            it.isSaved && (selectedChapterUrls == null || it.url in selectedChapterUrls)
        }
        require(chapters.isNotEmpty()) { "No downloaded chapters are available for export" }
        if (selectedChapterUrls != null) {
            require(chapters.map { it.url }.toSet() == selectedChapterUrls) {
                "Selected chapters are no longer available for export"
            }
        }
        return chapters
    }

    private fun DownloadedChapterContent.exportComponents(): List<DownloadedComponent> {
        val body = body ?: return components
        val assets = components.filter { it.type == "image" }.associateBy { it.value }
        return body.blocks.map { block ->
            when (block.type) {
                "image" -> assets[block.image] ?: DownloadedComponent("image", block.image.orEmpty())
                "break" -> DownloadedComponent("break", "")
                else -> DownloadedComponent("text", block.parts.joinToString("") { it.text })
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
        images: Collection<EpubImage>,
        coverHref: String?
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
        val coverItem = coverHref?.let { href ->
            "<item id=\"cover-image\" href=\"$href\" media-type=\"${mediaTypeFor(href.substringAfterLast('.'))}\" properties=\"cover-image\"/>"
        }.orEmpty()
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
                <item id="image-css" href="images.css" media-type="text/css"/>
                <item id="cover-page" href="cover.xhtml" media-type="application/xhtml+xml"/>
                $coverItem
            $chapterManifest
            $imageManifest
              </manifest>
              <spine>
                <itemref idref="cover-page"/>
            $spine
              </spine>
            </package>
        """.trimIndent()
    }

    private fun coverXhtml(manifest: DownloadedNovelManifest, coverHref: String?): String {
        val title = xml(manifest.name)
        val content = if (coverHref == null) {
            val author = manifest.author.takeIf(String::isNotBlank)?.let { "<p>${xml(it)}</p>" }.orEmpty()
            "<h1>$title</h1>$author"
        } else {
            "<img src=\"$coverHref\" alt=\"$title\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="zh-CN" xml:lang="zh-CN">
              <head><title>$title</title><link rel="stylesheet" href="images.css"/></head>
              <body epub:type="cover">$content</body>
            </html>
        """.trimIndent()
    }

    private fun navigationXhtml(
        manifest: DownloadedNovelManifest,
        chapterTitles: List<String>
    ): String {
        val links = chapterTitles.mapIndexed { index, title ->
            "        <li><a href=\"chapter-${index + 1}.xhtml\">${xml(title)}</a></li>"
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
        components: List<DownloadedComponent>,
        chapterImages: List<DownloadedComponent>,
        images: Map<String, EpubImage>
    ): String {
        val body = richChapterHtml(chapter, chapterImages, images) ?: components.joinToString("\n") { component ->
            when (component.type) {
                "image" -> images[component.imageKey()]?.let { image ->
                    "    <div><img src=\"${xml(image.href)}\" alt=\"插图\"/></div>"
                } ?: "    <p>[图片：${xml(component.value)}]</p>"
                "break" -> "    <p><br/></p>"
                else -> {
                    val text = ReaderLineIndent().trim(component.value)
                        .replace("\r\n", "\n").replace('\r', '\n').replace('\u2028', '\n').replace('\u2029', '\n')
                    val content = if (text.isEmpty()) "<br/>" else xml(text).replace("\n", "<br/>")
                    "    <p>$content</p>"
                }
            }
        }
        val title = chapter.name.ifBlank { "第 $index 章" }
        return """<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN" xml:lang="zh-CN">
              <head>
                <title>${xml(title)}</title>
                <meta charset="UTF-8"/>
                <link rel="stylesheet" href="images.css"/>
              </head>
              <body>
                <h1>${xml(title)}</h1>
            $body
              </body>
            </html>
        """.trimIndent()
    }

    private fun writeCoverImage(zip: ZipOutputStream, candidates: Sequence<StorageFile>): String? {
        for (file in candidates) {
            val cover = try {
                file.inputStream().use { it.readBytes() }
            } catch (_: IOException) {
                continue
            }
            val extension = imageExtension(cover) ?: continue
            val href = "images/cover.$extension"
            zip.putNextEntry(ZipEntry("OEBPS/$href"))
            zip.write(cover)
            zip.closeEntry()
            return href
        }
        return null
    }

    // Use only recognized EPUB raster formats. SVG can contain active content.
    private fun imageExtension(bytes: ByteArray): String? = when {
        bytes.size < 12 -> null
        bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "jpg"
        bytes.copyOf(8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> "png"
        String(bytes, 0, 6, StandardCharsets.US_ASCII) in setOf("GIF87a", "GIF89a") -> "gif"
        String(bytes, 0, 4, StandardCharsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, StandardCharsets.US_ASCII) == "WEBP" -> "webp"
        else -> null
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

    private fun writeChapterImage(zip: ZipOutputStream, file: StorageFile, number: Int): EpubImage? =
        file.inputStream().buffered().use { input ->
            val header = ByteArray(12)
            try {
                DataInputStream(input).readFully(header)
            } catch (_: EOFException) {
                return@use null
            }
            val extension = imageExtension(header) ?: return@use null
            val href = "images/image-$number.$extension"
            zip.putNextEntry(ZipEntry("OEBPS/$href"))
            zip.write(header)
            input.copyTo(zip)
            zip.closeEntry()
            EpubImage(href, mediaTypeFor(extension))
        }

    private fun DownloadedComponent.imageKey(): String = localFile ?: value

    private fun mediaTypeFor(extension: String): String = when (extension) {
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> error("Unsupported EPUB image extension: $extension")
    }

    private fun richChapterHtml(
        chapter: DownloadedChapterContent,
        chapterImages: List<DownloadedComponent>,
        images: Map<String, EpubImage>
    ): String? {
        val html = chapter.contentHtml?.takeIf(String::isNotBlank) ?: return null
        val document = Jsoup.parseBodyFragment(html, chapter.baseUrl ?: chapter.url)
        document.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .prettyPrint(false)
        document.select(
            "script, style, link, meta, base, svg, math, iframe, object, embed, form, input, button, video, audio, source"
        ).remove()
        document.allElements.forEach { element ->
            element.attributes().asList()
                .filter { it.key.startsWith("on", ignoreCase = true) }
                .forEach { element.removeAttr(it.key) }
        }

        document.select("img").forEach { image ->
            val candidates = IMAGE_URL_ATTRIBUTES.mapNotNull { attribute ->
                image.absUrl(attribute)
                    .ifBlank { image.attr(attribute) }
                    .trim()
                    .takeIf(String::isNotBlank)
            }
            val component = chapterImages.firstOrNull { it.value in candidates }
            val exportedImage = component?.let { images[it.imageKey()] }
            if (exportedImage != null) {
                IMAGE_URL_ATTRIBUTES.forEach(image::removeAttr)
                image.removeAttr("srcset")
                image.attr("src", exportedImage.href)
                if (!image.hasAttr("alt")) image.attr("alt", "插图")
            } else {
                val original = component?.value ?: candidates.firstOrNull().orEmpty()
                image.replaceWith(TextNode("[图片：$original]"))
            }
        }
        // A blocklist alone misses javascript: links, CSS imports/URLs and new HTML features.
        // Keep typography, ruby and tables, but permit only inert attributes and HTTPS links.
        val safe = Safelist.relaxed()
            .addTags("ruby", "rt", "rp", "rb", "hr")
            .removeAttributes(":all", "align", "height", "width")
            .removeAttributes("ul", "type")
            .removeProtocols("a", "href", "ftp", "http", "mailto")
            .addProtocols("a", "href", "https")
            .preserveRelativeLinks(true)
        val body = Cleaner(safe).clean(document).outputSettings(document.outputSettings()).body()
        formatRichProse(body)
        return body.html()
    }

    private fun formatRichProse(body: Element) {
        // Preserve tables and lists while giving each run of unwrapped prose a paragraph.
        for (container in body.select("div, blockquote, td, th, li").apply { add(body) }) {
            val nodes = container.childNodes().toList()
            container.empty()
            val inline = mutableListOf<Node>()
            fun flush() {
                if (inline.isEmpty()) return
                val paragraph = Element("p")
                inline.forEach(paragraph::appendChild)
                inline.clear()
                if (paragraph.hasText() || paragraph.selectFirst("img, br") != null) {
                    container.appendChild(paragraph)
                }
            }
            for (node in nodes) {
                if (node is Element && (node.tag().isBlock ||
                        node.normalName() == "a" && node.allElements.any { it.tag().isBlock })) {
                    flush()
                    container.appendChild(node)
                } else {
                    inline.add(node)
                }
            }
            flush()
        }

        for (original in body.select("p")) {
            val paragraphs = splitProseParagraphs(original)
            if (paragraphs.size > 1) {
                original.parent()?.insertChildren(original.siblingIndex(), paragraphs)
                original.remove()
            }
            for (paragraph in paragraphs) {
                val indent = ReaderLineIndent()
                paragraph.traverse { node, _ ->
                    if (node is TextNode) {
                        node.text(indent.trim(node.wholeText.replace(HTML_LINE_WHITESPACE, " ")))
                    } else if (node is Element && node.normalName() == "br") {
                        indent.startLine()
                    }
                }
                if (!paragraph.hasText() && paragraph.selectFirst("img, br") == null) {
                    paragraph.appendElement("br")
                }
            }
        }
    }

    private data class BreakRun(val start: Int, val nextContent: Int, val count: Int)

    /** Split sanitized inline paragraphs at blank lines or fresh full-width indents. */
    private fun splitProseParagraphs(element: Element): List<Element> {
        val nodes = element.childNodes().flatMap(::liftInlineBreaks)
        val separators = mutableListOf<BreakRun>()
        var leading: BreakRun? = null
        var trailing: BreakRun? = null
        var hasContent = false
        var index = 0
        while (index < nodes.size) {
            if (!nodes[index].isBreak()) {
                if (nodes[index].hasProseContent()) hasContent = true
                index++
                continue
            }
            val start = index
            var count = 0
            while (index < nodes.size) {
                when {
                    nodes[index].isBreak() -> count++
                    nodes[index].isCollapsibleGap() -> Unit
                    else -> break
                }
                index++
            }
            val indented = hasFullWidthIndent(nodes, index)
            val probeStart = if (indented && nodes.getOrNull(index)?.hasProseContent() != true) index + 1 else index
            val followedByContent = (probeStart until nodes.size)
                .firstOrNull { nodes[it].isBreak() || nodes[it].hasProseContent() }
                ?.let { !nodes[it].isBreak() } == true
            val gap = BreakRun(start, index, count)
            if (!followedByContent) {
                if (hasContent && index == nodes.size) trailing = gap
                continue
            }
            if (!hasContent) leading = gap
            else if (count > 1 || indented) separators += gap
        }
        if (separators.isEmpty()) return listOf(element)

        fun fragment(start: Int, end: Int): Element = Element("p").also { paragraph ->
            for (i in start until end) {
                val child = nodes[i].clone()
                if (child is TextNode) {
                    var text = child.wholeText
                    if (i == start) text = text.trimStart(::isHtmlSpace)
                    if (i == end - 1) text = text.trimEnd(::isHtmlSpace)
                    if (text.isEmpty()) continue
                    child.text(text)
                }
                paragraph.appendChild(child)
            }
        }

        fun blankLine(): Element = Element("p").apply { appendElement("br") }
        return buildList {
            repeat(leading?.count ?: 0) { add(blankLine()) }
            var start = leading?.nextContent ?: 0
            for (gap in separators) {
                add(fragment(start, gap.start))
                repeat(gap.count - 1) { add(blankLine()) }
                start = gap.nextContent
            }
            add(fragment(start, trailing?.start ?: nodes.size))
            repeat((trailing?.count ?: 1) - 1) { add(blankLine()) }
        }
    }

    /** Lift hard breaks out of inline wrappers so split paragraphs keep styling on both sides. */
    private fun liftInlineBreaks(node: Node): List<Node> {
        if (node !is Element || node.normalName() == "ruby" || node.childNodeSize() == 0) return listOf(node)
        val children = node.childNodes().flatMap(::liftInlineBreaks)
        if (children.none { it.isBreak() }) return listOf(node)
        return buildList {
            var run: Element? = null
            for (child in children) {
                if (child.isBreak()) {
                    add(child)
                    run = null
                } else {
                    val wrapper = run ?: node.shallowClone().also {
                        add(it)
                        run = it
                    }
                    wrapper.appendChild(if (child.hasParent()) child.clone() else child)
                }
            }
        }
    }

    private fun Node.isBreak(): Boolean = this is Element && normalName() == "br"

    private fun Node.isCollapsibleGap(): Boolean = when (this) {
        is TextNode -> wholeText.all(::isHtmlSpace)
        is Element -> !isBreak() && normalName() != "img" && normalName() != "ruby" &&
            childNodes().all { it.isCollapsibleGap() }
        else -> false
    }

    private fun Node.hasProseContent(): Boolean = when (this) {
        is TextNode -> wholeText.isNotBlank()
        is Element -> normalName() == "img" || hasText() || selectFirst("img") != null
        else -> false
    }

    private fun hasFullWidthIndent(nodes: List<Node>, startIndex: Int): Boolean {
        var spaces = 0
        fun scan(node: Node): Boolean? = when (node) {
            is TextNode -> {
                for (ch in node.wholeText) {
                    if (spaces == 0 && isHtmlSpace(ch)) continue
                    if (ch != '\u3000') return false
                    if (++spaces == 2) return true
                }
                null
            }
            is Element -> when (node.normalName()) {
                "br", "img" -> false
                "rp" -> null
                else -> node.childNodes().firstNotNullOfOrNull { scan(it) }
            }
            else -> null
        }
        for (i in startIndex until nodes.size) {
            scan(nodes[i])?.let { return it }
        }
        return false
    }

    private fun isHtmlSpace(char: Char): Boolean = char in " \t\n\r\u000c"

    private data class EpubImage(
        val href: String,
        val mediaType: String
    )

    private val IMAGE_URL_ATTRIBUTES = listOf(
        "data-src",
        "data-original",
        "data-lazy-src",
        "src"
    )
    private val HTML_LINE_WHITESPACE = Regex("[\\t\\n\\r\\f]+")

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
