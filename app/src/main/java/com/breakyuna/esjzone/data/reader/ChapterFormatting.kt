package com.breakyuna.esjzone.data.reader

import com.breakyuna.esjzone.data.repository.toReaderBlocks
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterFormatting
import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.component.analyseParagraph
import com.breakyuna.esjzone.novellibrary.component.extractParagraphElements
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.IdentityHashMap

private val chapterAlignments = setOf("left", "center", "right", "start", "end", "justify")
private val chapterFlowContainers = setOf("body", "div", "section", "article", "main", "blockquote", "td", "th")
private val chapterFlowIgnoredTags = setOf("script", "style", "noscript", "template", "rp")

internal fun String.hasChapterFormattingMarkup(): Boolean =
    contains("text-align", ignoreCase = true) || contains("<hr", ignoreCase = true) ||
        contains("<details", ignoreCase = true)

internal data class ParsedChapterContent(
    val components: List<Component>,
    val blocks: List<ReaderBlock>,
    val formatting: ReaderChapterFormatting,
    val degraded: Boolean = false
)

/**
 * Parse the body and its layout in one pass, falling back to the lossless walker
 * only when the primary parser throws or yields empty blocks for non-empty content.
 */
internal fun analyseChapterContent(
    element: Element,
    primaryParser: (Element) -> ParsedChapterContent = ::parseRichChapterContent
): ParsedChapterContent {
    val primary = try {
        primaryParser(element)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        AppLogger.w(
            "ChapterIntegrity",
            "Rich parser failed (${error.javaClass.simpleName}); switching to lossless fallback"
        )
        null
    }
    if (primary != null) {
        if (primary.blocks.isNotEmpty()) return primary
        if (!element.hasText() && element.selectFirst("img") == null) return primary
    }

    val fallbackBlocks = parseLosslessFallbackBlocks(element)
    if (primary != null && fallbackBlocks.isEmpty()) return primary
    if (primary != null) {
        AppLogger.w(
            "ChapterIntegrity",
            "Rich parser returned empty blocks; switching to lossless fallback"
        )
    }
    val fallbackComponents = if (fallbackBlocks.isEmpty()) emptyList()
        else ChapterBody.fromBlocks(fallbackBlocks).components()
    return ParsedChapterContent(
        components = fallbackComponents,
        blocks = fallbackBlocks,
        formatting = ReaderChapterFormatting(
            parserVersion = ReaderChapterFormatting.CURRENT_PARSER_VERSION,
            degraded = true
        ),
        degraded = true
    )
}

internal fun parseRichChapterContent(element: Element): ParsedChapterContent {
    val components = mutableListOf<Component>()
    val blocks = mutableListOf<ReaderBlock>()
    val alignments = mutableMapOf<Int, String>()
    val dividers = mutableListOf<Int>()
    val disclosureTitles = mutableMapOf<Int, Int>()
    val disclosureEnds = IdentityHashMap<Element, Int>()
    val inherited = IdentityHashMap<Element, String?>()
    val sourceParents = IdentityHashMap<Element, Element>()
    fun alignmentFor(node: Element?): String? {
        if (node == null) return null
        if (inherited.containsKey(node)) return inherited[node]
        val style = node.attr("style")
        val declared = if (style.contains("text-align", ignoreCase = true)) {
            style.split(';').lastOrNull {
                it.substringBefore(':').trim().equals("text-align", ignoreCase = true)
            }?.substringAfter(':')?.trim()?.lowercase()
        } else null
        val value = if (declared == null) alignmentFor(sourceParents[node] ?: node.parent())
            else declared.takeIf { it in chapterAlignments }
        inherited[node] = value
        return value
    }
    for (paragraph in extractParagraphElements(element) { paragraph, parent -> sourceParents[paragraph] = parent }) {
        if (paragraph.normalName() == "hr") {
            dividers.add(blocks.size)
            continue
        }
        val source = sourceParents[paragraph] ?: paragraph
        val alignment = alignmentFor(paragraph)
        val bodyParagraphs = if (source.normalName() in chapterFlowContainers) {
            splitChapterFlowParagraphs(paragraph)
        } else listOf(paragraph)
        val parsed = bodyParagraphs.flatMap(::analyseParagraph)
        val parsedBlocks = parsed.flatMap(Component::toReaderBlocks)
        if (source.normalName() == "details") {
            val summary = paragraph.children().firstOrNull { it.normalName() == "summary" }
            val title = summary?.let { analyseParagraph(it).flatMap(Component::toReaderBlocks) }
                ?.joinToString("") { it.sourceText() }
            val titleParts = title?.let { parsedBlocks.firstOrNull()?.leadingTitleParts(it) }
            if (titleParts != null) {
                disclosureTitles[blocks.size] = titleParts
                disclosureEnds[source] = blocks.size
            }
        }
        if (alignment != null) parsedBlocks.indices.forEach { offset -> alignments[blocks.size + offset] = alignment }
        blocks.addAll(parsedBlocks)
        components.addAll(parsed)
        if (disclosureEnds.isNotEmpty()) {
            for (ancestor in generateSequence(source) { it.parent() }) {
                if (disclosureEnds.containsKey(ancestor)) disclosureEnds[ancestor] = blocks.size
            }
        }
    }
    return ParsedChapterContent(components, blocks, ReaderChapterFormatting(
        alignments = alignments.toMap(),
        dividersAt = (dividers + disclosureEnds.values.toSet().filterNot(dividers::contains)).sorted(),
        parserVersion = ReaderChapterFormatting.CURRENT_PARSER_VERSION,
        disclosureTitleParts = disclosureTitles.toMap()
    ))
}

private data class ChapterBreakRun(val start: Int, val nextContent: Int, val count: Int)

/** A blank line or a fresh source indent separates paragraphs in unwrapped chapter prose. */
private fun splitChapterFlowParagraphs(element: Element): List<Element> {
    val nodes = element.childNodes()
    val separators = mutableListOf<ChapterBreakRun>()
    var leading: ChapterBreakRun? = null
    var trailing: ChapterBreakRun? = null
    var hasContent = false
    var index = 0
    while (index < nodes.size) {
        val node = nodes[index]
        if (!node.isChapterBreak()) {
            if (node.hasChapterFlowContent()) hasContent = true
            index++
            continue
        }
        val start = index
        var count = 0
        while (index < nodes.size) {
            val next = nodes[index]
            when {
                next.isChapterBreak() -> count++
                next is Comment || next is TextNode && next.wholeText.all(::isCollapsibleHtmlSpace) ||
                    next is Element && next.normalName() in chapterFlowIgnoredTags -> Unit
                else -> break
            }
            index++
        }
        val next = nodes.getOrNull(index)
        val indented = next is TextNode && next.wholeText.trimStart(::isCollapsibleHtmlSpace).startsWith("\u3000\u3000")
        var probe = if (indented && next?.hasChapterFlowContent() != true) index + 1 else index
        while (probe < nodes.size) {
            val candidate = nodes[probe]
            if (candidate is Comment ||
                candidate is TextNode && candidate.wholeText.all(::isCollapsibleHtmlSpace) ||
                candidate is Element && candidate.normalName() in chapterFlowIgnoredTags) {
                probe++
            } else break
        }
        val followedByContent = nodes.getOrNull(probe)?.hasChapterFlowContent() == true
        val gap = ChapterBreakRun(start, index, count)
        if (!followedByContent) {
            if (hasContent && index == nodes.size) trailing = gap
            continue
        }
        if (!hasContent) leading = gap
        else if (count > 1 || indented) separators += gap
    }
    if (separators.isEmpty()) return listOf(element)

    fun fragment(start: Int, end: Int): Element = Element("p").also { paragraph ->
        paragraph.setBaseUri(element.baseUri())
        for (nodeIndex in start until end) {
            val child = nodes[nodeIndex].clone()
            if (child is TextNode) {
                var text = child.wholeText
                if (nodeIndex == start) text = text.trimStart(::isCollapsibleHtmlSpace)
                if (nodeIndex == end - 1) text = text.trimEnd(::isCollapsibleHtmlSpace)
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
        val bodyEnd = trailing?.start ?: nodes.size
        add(fragment(start, bodyEnd))
        repeat((trailing?.count ?: 1) - 1) { add(blankLine()) }
    }
}

private fun isCollapsibleHtmlSpace(char: Char): Boolean = char in " \t\n\r\u000c"

private fun Node.isChapterBreak(): Boolean = this is Element && normalName() == "br"

private fun Node.hasChapterFlowContent(): Boolean = when (this) {
    is TextNode -> wholeText.isNotBlank()
    is Element -> normalName() !in chapterFlowIgnoredTags &&
        (normalName() == "img" || hasText() || selectFirst("img") != null)
    else -> false
}

private fun ReaderBlock.leadingTitleParts(title: String): Int? {
    if (this !is ReaderBlock.Paragraph) return null
    val expected = title.filterNot(Char::isWhitespace)
    if (expected.isEmpty()) return null
    val prefix = StringBuilder()
    for ((index, part) in parts.withIndex()) {
        prefix.append(part.value.filterNot(Char::isWhitespace))
        if (prefix.length >= expected.length) return (index + 1).takeIf { prefix.toString() == expected }
    }
    return null
}

/** The saved HTML omits the outer container, so some inherited alignments only exist in the snapshot. */
internal fun ChapterBody.savedAlignments(): Map<Int, String> =
    formatting?.alignments.orEmpty().filter { (index, alignment) ->
        index in blocks.indices && alignment in chapterAlignments
    }

internal fun ChapterBody.keepSavedAlignments(recovered: ReaderChapterFormatting): ReaderChapterFormatting {
    val saved = savedAlignments()
    return if (saved.isEmpty()) recovered else recovered.copy(alignments = saved + recovered.alignments)
}

/** Recover formatting only when the saved HTML describes the same body blocks. */
internal fun ChapterBody.withFormatting(html: String?, baseUrl: String?): ChapterBody {
    val saved = formatting
    if (saved != null) {
        val savedTitles: Map<Int, Int>? = saved.disclosureTitleParts
        val valid = runCatching {
            saved.alignments.all { (index, alignment) -> index in blocks.indices && alignment in chapterAlignments } &&
                saved.dividersAt.all { it in 0..blocks.size } &&
                savedTitles.orEmpty().all { (index, count) ->
                    val block = blocks.getOrNull(index)
                    block?.type == "paragraph" && count in 1..block.parts.size
                }
        }.getOrDefault(false)
        if (!valid) return copy(formatting = null).withFormatting(html, baseUrl)
        if (savedTitles == null) {
            return copy(formatting = saved.copy(disclosureTitleParts = emptyMap())).withFormatting(html, baseUrl)
        }
        if (saved.parserVersion >= ReaderChapterFormatting.CURRENT_PARSER_VERSION || html.isNullOrBlank()) return this
    }
    if (html.isNullOrBlank() || !html.hasChapterFormattingMarkup()) return this
    val result = runCatching {
        val document = Jsoup.parseBodyFragment(html, baseUrl.orEmpty())
        val recovered = analyseChapterContent(document.body())
        val layout = recovered.formatting
        val parsed = recovered.blocks
        val stored = readerBlocks()
        val matches = parsed.size == stored.size && parsed.indices.all { index ->
            val actual = parsed[index]
            val expected = stored[index]
            when (expected) {
                is ReaderBlock.Paragraph, is ReaderBlock.Text ->
                    (actual is ReaderBlock.Paragraph || actual is ReaderBlock.Text) &&
                        (actual.sourceText() == expected.sourceText() || layout.disclosureTitleParts.isNotEmpty() &&
                            actual.sourceText().filterNot(Char::isWhitespace) ==
                            expected.sourceText().filterNot(Char::isWhitespace))
                is ReaderBlock.Image -> actual is ReaderBlock.Image && actual.url == expected.url
                ReaderBlock.LineBreak -> actual == ReaderBlock.LineBreak
            }
        }
        if (!matches || (layout.alignments.isEmpty() && layout.dividersAt.isEmpty())) return this
        val titles = layout.disclosureTitleParts.mapNotNull { (index, count) ->
            val title = (parsed[index] as ReaderBlock.Paragraph).parts.take(count).joinToString("") { it.value }
            stored[index].leadingTitleParts(title)?.let { index to it }
        }.toMap()
        keepSavedAlignments(layout.copy(disclosureTitleParts = titles))
    }.getOrElse { return this }
    return if (result == null) this else copy(formatting = result)
}
