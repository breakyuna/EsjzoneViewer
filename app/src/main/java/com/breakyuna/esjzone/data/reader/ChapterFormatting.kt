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
import org.jsoup.nodes.Element
import java.util.IdentityHashMap

private val chapterAlignments = setOf("left", "center", "right", "start", "end", "justify")

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
 * Parse the body and its layout in one pass, then verify content conservation
 * against an independent DOM traversal. If the primary parser drops visible
 * prose, furigana, or images, fall back immediately to the lossless walker.
 */
internal fun analyseChapterContent(
    element: Element,
    primaryParser: (Element) -> ParsedChapterContent = ::parseRichChapterContent
): ParsedChapterContent {
    val expected = extractDomContentSnapshot(element)
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
        val actual = extractBlocksContentSnapshot(primary.blocks)
        if (isContentConserved(expected, actual)) return primary
        AppLogger.w(
            "ChapterIntegrity",
            "Rich parser failed content conservation (${expected.countsComparedTo(actual)}); " +
                "switching to lossless fallback"
        )
    }

    val fallbackBlocks = parseLosslessFallbackBlocks(element)
    val fallback = extractBlocksContentSnapshot(fallbackBlocks)
    if (isContentConserved(expected, fallback)) {
        AppLogger.d("ChapterIntegrity", "Lossless fallback passed content conservation")
    } else {
        AppLogger.w(
            "ChapterIntegrity",
            "Lossless fallback failed content conservation (${expected.countsComparedTo(fallback)})"
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

private fun parseRichChapterContent(element: Element): ParsedChapterContent {
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
        val alignment = alignmentFor(paragraph)
        val parsed = analyseParagraph(paragraph)
        val parsedBlocks = parsed.flatMap(Component::toReaderBlocks)
        val source = sourceParents[paragraph] ?: paragraph
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
internal fun ChapterBody.keepSavedAlignments(recovered: ReaderChapterFormatting): ReaderChapterFormatting {
    val saved = formatting?.alignments.orEmpty().filter { (index, alignment) ->
        index in blocks.indices && alignment in chapterAlignments
    }
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
