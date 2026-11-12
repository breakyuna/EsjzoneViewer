package com.breakyuna.esjzone.data.reader

import com.breakyuna.esjzone.data.repository.toReaderBlocks
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterFormatting
import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.component.analyseParagraph
import com.breakyuna.esjzone.novellibrary.component.extractParagraphElements
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.IdentityHashMap

private val chapterAlignments = setOf("left", "center", "right", "start", "end", "justify")

internal data class ParsedChapterContent(
    val components: List<Component>,
    val blocks: List<ReaderBlock>,
    val formatting: ReaderChapterFormatting
)

/** Parse the body and its layout in the same pass without adding text blocks. */
internal fun analyseChapterContent(element: Element): ParsedChapterContent {
    val components = mutableListOf<Component>()
    val blocks = mutableListOf<ReaderBlock>()
    val alignments = mutableMapOf<Int, String>()
    val dividers = mutableListOf<Int>()
    val inherited = IdentityHashMap<Element, String?>()
    fun alignmentFor(node: Element?): String? {
        if (node == null) return null
        if (inherited.containsKey(node)) return inherited[node]
        val style = node.attr("style")
        val declared = if (style.contains("text-align", ignoreCase = true)) {
            style.split(';').lastOrNull {
                it.substringBefore(':').trim().equals("text-align", ignoreCase = true)
            }?.substringAfter(':')?.trim()?.lowercase()
        } else null
        val value = if (declared == null) alignmentFor(node.parent()) else declared.takeIf { it in chapterAlignments }
        inherited[node] = value
        return value
    }
    for (paragraph in extractParagraphElements(element)) {
        if (paragraph.normalName() == "hr") {
            dividers.add(blocks.size)
            continue
        }
        val alignment = alignmentFor(paragraph)
        val parsed = analyseParagraph(paragraph)
        val parsedBlocks = parsed.flatMap(Component::toReaderBlocks)
        if (alignment != null) parsedBlocks.indices.forEach { offset -> alignments[blocks.size + offset] = alignment }
        blocks.addAll(parsedBlocks)
        components.addAll(parsed)
    }
    return ParsedChapterContent(components, blocks, ReaderChapterFormatting(alignments.toMap(), dividers.toList()))
}

/** Recover formatting only when the saved HTML describes the same body blocks. */
internal fun ChapterBody.withFormatting(html: String?, baseUrl: String?): ChapterBody {
    val saved = formatting
    if (saved != null) {
        val valid = runCatching {
            saved.alignments.all { (index, alignment) -> index in blocks.indices && alignment in chapterAlignments } &&
                saved.dividersAt.all { it in 0..blocks.size }
        }.getOrDefault(false)
        if (valid) return this
        return copy(formatting = null).withFormatting(html, baseUrl)
    }
    if (html.isNullOrBlank()) return this
    if (!html.contains("text-align", ignoreCase = true) && !html.contains("<hr", ignoreCase = true)) return this
    val result = runCatching {
        val document = Jsoup.parseBodyFragment(html, baseUrl.orEmpty())
        val recovered = analyseChapterContent(document.body())
        val parsed = recovered.blocks
        val stored = readerBlocks()
        val matches = parsed.size == stored.size && parsed.indices.all { index ->
            val actual = parsed[index]
            val expected = stored[index]
            when (expected) {
                is ReaderBlock.Paragraph, is ReaderBlock.Text ->
                    (actual is ReaderBlock.Paragraph || actual is ReaderBlock.Text) &&
                        actual.sourceText() == expected.sourceText()
                is ReaderBlock.Image -> actual is ReaderBlock.Image && actual.url == expected.url
                ReaderBlock.LineBreak -> actual == ReaderBlock.LineBreak
            }
        }
        recovered.formatting.takeIf { matches && (it.alignments.isNotEmpty() || it.dividersAt.isNotEmpty()) }
    }.getOrElse { return this }
    return if (result == null) this else copy(formatting = result)
}
