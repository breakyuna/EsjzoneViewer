package com.breakyuna.esjzone.data.reader

import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderRuby
import com.breakyuna.esjzone.novellibrary.component.resolveImageUrl
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private val INTEGRITY_IGNORED_TAGS = setOf("script", "style", "noscript", "template", "rp")
private val FALLBACK_BLOCK_TAGS = setOf(
    "p", "div", "section", "article", "blockquote", "main", "aside",
    "header", "footer", "h1", "h2", "h3", "h4", "h5", "h6",
    "ul", "ol", "li", "pre", "hr", "table", "thead", "tbody", "tfoot",
    "tr", "td", "th", "details", "summary", "figure", "figcaption",
    "dl", "dt", "dd", "center", "address", "fieldset", "legend"
)

internal sealed interface ChapterContentEvent {
    data class Text(val value: String) : ChapterContentEvent
    data class Image(val url: String) : ChapterContentEvent
}

internal data class ChapterRubySpan(val start: Int, val end: Int, val reading: String)

internal data class DomContentSnapshot(
    val events: List<ChapterContentEvent>,
    val rubySpans: List<ChapterRubySpan>
) {
    private val textLength = events.sumOf { if (it is ChapterContentEvent.Text) it.value.length else 0 }
    private val imageCount = events.count { it is ChapterContentEvent.Image }

    fun countsComparedTo(actual: DomContentSnapshot): String =
        "domChars=$textLength, parsedChars=${actual.textLength}, domImages=$imageCount, " +
            "parsedImages=${actual.imageCount}, domRuby=${rubySpans.size}, parsedRuby=${actual.rubySpans.size}"
}

private class ContentSnapshotBuilder {
    private val events = mutableListOf<ChapterContentEvent>()
    private val pendingText = StringBuilder()
    private val rubySpans = mutableListOf<ChapterRubySpan>()
    private var textLength = 0

    fun appendText(raw: String, reading: String? = null) {
        val start = textLength
        for (ch in raw) {
            if (!ch.isWhitespace()) {
                pendingText.append(ch)
                textLength++
            }
        }
        if (start == textLength) return
        val normalizedReading = reading?.filterNot(Char::isWhitespace)
        if (normalizedReading.isNullOrEmpty()) return
        val last = rubySpans.lastOrNull()
        if (last != null && last.end == start && last.reading == normalizedReading) {
            // A single reading can be split over several styled text parts.
            rubySpans[rubySpans.lastIndex] = last.copy(end = textLength)
        } else {
            rubySpans.add(ChapterRubySpan(start, textLength, normalizedReading))
        }
    }

    fun appendImage(url: String) {
        if (url.isBlank()) return
        flushText()
        events.add(ChapterContentEvent.Image(url))
    }

    private fun flushText() {
        if (pendingText.isEmpty()) return
        events.add(ChapterContentEvent.Text(pendingText.toString()))
        pendingText.clear()
    }

    fun build(): DomContentSnapshot {
        flushText()
        return DomContentSnapshot(events, rubySpans)
    }
}

/** Groups ruby base nodes and readings even when they are wrapped in inline elements. */
private fun visitRubyRuns(ruby: Element, visit: (List<Node>, String?) -> Unit) {
    val base = mutableListOf<Node>()
    val readings = mutableListOf<String>()

    fun flush() {
        if (base.isNotEmpty()) visit(base, readings.joinToString(" ").trim().takeIf { it.isNotEmpty() })
        base.clear()
        readings.clear()
    }

    fun walk(node: Node) {
        when (node) {
            is TextNode -> {
                if (readings.isNotEmpty() && node.text().isNotBlank()) flush()
                base.add(node)
            }
            is Element -> when (node.normalName()) {
                in INTEGRITY_IGNORED_TAGS -> Unit
                "rt" -> readings.add(node.text())
                "img", "br" -> {
                    if (readings.isNotEmpty()) flush()
                    base.add(node)
                }
                else -> node.childNodes().forEach(::walk)
            }
        }
    }

    ruby.childNodes().forEach(::walk)
    flush()
}

/** Extracts visible DOM content independently of the rich Component parser. */
internal fun extractDomContentSnapshot(container: Element): DomContentSnapshot {
    val snapshot = ContentSnapshotBuilder()

    fun walk(node: Node) {
        when (node) {
            is TextNode -> snapshot.appendText(node.text())
            is Element -> when (node.normalName()) {
                in INTEGRITY_IGNORED_TAGS, "rt" -> Unit
                "img" -> snapshot.appendImage(resolveImageUrl(node))
                "ruby" -> visitRubyRuns(node) { base, reading ->
                    for (part in base) {
                        if (part is TextNode) snapshot.appendText(part.text(), reading)
                        else walk(part)
                    }
                }
                else -> node.childNodes().forEach(::walk)
            }
        }
    }

    container.childNodes().forEach(::walk)
    return snapshot.build()
}

internal fun extractBlocksContentSnapshot(blocks: List<ReaderBlock>): DomContentSnapshot {
    val snapshot = ContentSnapshotBuilder()

    fun recordPart(part: ReaderBlock.Text) {
        snapshot.appendText(part.value, part.ruby?.reading)
    }

    for (block in blocks) {
        when (block) {
            is ReaderBlock.Paragraph -> block.parts.forEach(::recordPart)
            is ReaderBlock.Text -> recordPart(block)
            is ReaderBlock.Image -> snapshot.appendImage(block.url)
            ReaderBlock.LineBreak -> Unit
        }
    }
    return snapshot.build()
}

internal fun isContentConserved(expected: DomContentSnapshot, actual: DomContentSnapshot): Boolean =
    expected == actual

/** Simplified DOM-to-ReaderBlock walker used when rich parsing fails content conservation. */
internal fun parseLosslessFallbackBlocks(container: Element): List<ReaderBlock> {
    val blocks = mutableListOf<ReaderBlock>()
    val currentParts = mutableListOf<ReaderBlock.Text>()

    fun flushParagraph() {
        if (currentParts.isEmpty()) return
        val hasVisibleText = currentParts.any { it.value.isNotBlank() }
        if (hasVisibleText) {
            blocks.add(ReaderBlock.Paragraph(currentParts.toList()))
        } else if (currentParts.any { it.value.contains('\n') }) {
            blocks.add(ReaderBlock.LineBreak)
        }
        currentParts.clear()
    }

    fun emitText(raw: String, rubyReading: String? = null) {
        if (raw.isEmpty()) return
        if (currentParts.isEmpty() && raw.isBlank()) return
        val ruby = rubyReading?.takeIf { it.isNotBlank() }?.let { ReaderRuby(raw, it) }
        currentParts.add(ReaderBlock.Text(raw, ruby = ruby))
    }

    fun walk(node: Node) {
        when (node) {
            is TextNode -> emitText(node.text())
            is Element -> when (node.normalName()) {
                in INTEGRITY_IGNORED_TAGS, "rt" -> Unit
                "br" -> {
                    if (currentParts.isNotEmpty()) {
                        currentParts.add(ReaderBlock.Text("\n"))
                    } else {
                        blocks.add(ReaderBlock.LineBreak)
                    }
                }
                "img" -> {
                    val resolved = resolveImageUrl(node)
                    if (resolved.isNotBlank()) {
                        flushParagraph()
                        blocks.add(ReaderBlock.Image(resolved))
                    }
                }
                "ruby" -> visitRubyRuns(node) { base, reading ->
                    for (part in base) {
                        if (part is TextNode) emitText(part.text(), reading)
                        else walk(part)
                    }
                }
                in FALLBACK_BLOCK_TAGS -> {
                    flushParagraph()
                    node.childNodes().forEach(::walk)
                    flushParagraph()
                }
                else -> node.childNodes().forEach(::walk)
            }
        }
    }

    container.childNodes().forEach(::walk)
    flushParagraph()
    return blocks
}
