package com.breakyuna.esjzone.ui.reader

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.data.repository.toReaderBlocks
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.ReaderTextStyle
import com.breakyuna.esjzone.network.external.isAllowedWenkuImageUrl
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import kotlin.math.ceil

/**
 * Presentation adapter for the already parsed chapter model. The parser and
 * persistence contracts stay in their existing layers; Reader UI consumes the
 * stable domain AST below this seam.
 */
fun DetailedChapter.toReaderDocument(chapter: Chapter): ReaderChapterDocument {
    val isWenku = chapter.source == ChapterSource.WENKU8
    fun imageUrl(original: String): String {
        val resolved = imageLocations[original] ?: original
        val downloaded = resolved != original && (resolved.startsWith("file:") || resolved.startsWith("content:"))
        // Keep the block index for anchors in older snapshots while suppressing disallowed requests.
        return if (!isWenku || downloaded || isAllowedWenkuImageUrl(resolved)) resolved else ""
    }
    val blocks = body?.readerBlocks(::imageUrl) ?: content.flatMap(Component::toReaderBlocks).let { legacy ->
        if (!isWenku) legacy else legacy.map { block ->
            if (block is ReaderBlock.Image) block.copy(url = imageUrl(block.url)) else block
        }
    }
    return ReaderChapterDocument(
        chapter = ReaderChapterRef(chapter.name, chapter.url),
        blocks = blocks,
        previous = previous?.let { ReaderChapterRef(it.name, it.url) },
        next = next?.let { ReaderChapterRef(it.name, it.url) },
        contentHtml = null,
        sourceUrl = sourceUrl,
        contentFingerprint = body?.fingerprint.orEmpty(),
        links = readerChapterLinks(
            blocks = blocks,
            html = contentHtml,
            baseUrl = sourceUrl?.takeIf(String::isNotBlank) ?: chapter.url,
            components = content
        ),
        formatting = body?.formatting ?: com.breakyuna.esjzone.domain.reader.ReaderChapterFormatting(),
        legacyPositions = body?.legacyPositions
    )
}

/** Text used in signatures saved before line indents were removed. */
internal fun ReaderBlock.untrimmedReaderText(textTransform: (String) -> String): String = when (this) {
    is ReaderBlock.Paragraph -> parts.joinToString("") { textTransform(it.value) }
    is ReaderBlock.Text -> textTransform(value)
    else -> ""
}

internal fun ReaderBlock.isBlankLine(): Boolean = when (this) {
    is ReaderBlock.Paragraph -> parts.all { it.isPlainBlank() }
    is ReaderBlock.Text -> isPlainBlank()
    ReaderBlock.LineBreak -> true
    is ReaderBlock.Image -> false
}

private fun ReaderBlock.Text.isPlainBlank(): Boolean = value.isBlank() && ruby == null && styles.isEmpty()

/** A following blank line already supplies separation unless a divider comes first. */
internal fun ReaderSettings.paragraphSpacingBefore(
    nextBlock: ReaderBlock?,
    blankLineHeight: Dp,
    hasDivider: Boolean
): Dp {
    val spacing = paragraphSpacingDp.dp
    return if (!hasDivider && nextBlock?.isBlankLine() == true) maxOf(0.dp, spacing - blankLineHeight)
    else spacing
}

internal fun ReaderBlock.Paragraph.toAnnotatedReaderParagraph(
    style: ComposeTextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    textTransform: (String) -> String,
    disclosureTitleParts: Int?,
    checkCancelled: () -> Unit = {}
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inline = linkedMapOf<String, InlineTextContent>()
    val lineIndent = ReaderLineIndent()
    val text = buildAnnotatedString {
        var titleEnd = 0
        parts.forEachIndexed { index, part ->
            checkCancelled()
            val (value, content) = part.toAnnotatedReaderText(
                style, textMeasurer, density, textTransform, inlineKey = "reader-ruby-$index",
                lineIndent = lineIndent
            )
            append(value)
            inline.putAll(content)
            if (index + 1 == disclosureTitleParts) titleEnd = length
        }
        if (titleEnd > 0) {
            // Paragraph styles create a line boundary without inserting a character
            // that would change text offsets for links, bookmarks or underlines.
            val titleHeight = if (inline.isEmpty() && style.lineHeight.isSp) {
                (style.lineHeight.value * 1.18f).sp
            } else TextUnit.Unspecified
            addStyle(ParagraphStyle(textAlign = TextAlign.Start, textIndent = TextIndent.None,
                lineHeight = titleHeight), 0, titleEnd)
            addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), 0, titleEnd)
            if (titleEnd < length) {
                addStyle(ParagraphStyle(textIndent = TextIndent.None), titleEnd, length)
            }
        }
    }
    return text to inline
}

/** Converts one domain text block to an annotated string while preserving inline ruby. */
internal fun ReaderBlock.Text.toAnnotatedReaderText(
    baseStyle: ComposeTextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    textTransform: (String) -> String,
    inlineKey: String = "reader-ruby",
    lineIndent: ReaderLineIndent = ReaderLineIndent()
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val displayed = lineIndent.trim(textTransform(value))
    if (displayed.isEmpty()) return AnnotatedString("") to emptyMap()

    val span = styles.fold(SpanStyle()) { current, style ->
        current.merge(style.toSpanStyle())
    }
    val rubyValue = ruby
    if (rubyValue == null) {
        return buildAnnotatedString {
            withStyle(span) { append(displayed) }
        } to emptyMap()
    }

    // A ruby is inline within a paragraph; the paragraph's first-line indent
    // and alignment must not be applied a second time inside the placeholder.
    val effectiveStyle = baseStyle.merge(span).copy(textIndent = TextIndent.None, textAlign = TextAlign.Start)
    val displayedReading = textTransform(rubyValue.reading)
    val rubyStyle = effectiveStyle.copy(
        fontSize = (effectiveStyle.fontSize.value * 0.56f).coerceAtLeast(8f).sp,
        lineHeight = (effectiveStyle.fontSize.value * 0.65f).coerceAtLeast(9f).sp
    )
    val baseLayout = textMeasurer.measure(displayed, effectiveStyle, softWrap = false, maxLines = 1)
    val readingLayout = textMeasurer.measure(displayedReading, rubyStyle, softWrap = false, maxLines = 1)
    val measuredWidth = with(density) { maxOf(baseLayout.size.width, readingLayout.size.width).toDp().toSp() }
    val width = if (measuredWidth.value < 1f) 1.sp else measuredWidth
    // The placeholder bottom is the surrounding text baseline. Its lower
    // glyph descent is drawn outside the box, as ordinary text would be.
    val heightPx = ceil(readingLayout.size.height + baseLayout.firstBaseline).toInt().coerceAtLeast(1)
    val height = with(density) { heightPx.toDp().toSp() }
    val inline = InlineTextContent(
        placeholder = androidx.compose.ui.text.Placeholder(
            width = width,
            height = height,
            placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.AboveBaseline
        ),
        children = {
            Layout(content = {
                ReaderGlowBodyText(
                    text = displayedReading,
                    style = rubyStyle,
                    maxLines = 1
                )
                ReaderGlowBodyText(text = displayed, style = effectiveStyle, maxLines = 1)
            }) { measurables, constraints ->
                val childConstraints = Constraints(maxWidth = constraints.maxWidth)
                val reading = measurables[0].measure(childConstraints)
                val base = measurables[1].measure(childConstraints)
                val measuredBaseline = base[FirstBaseline]
                val baseline = if (measuredBaseline == AlignmentLine.Unspecified) base.height else measuredBaseline
                val baseY = constraints.maxHeight - baseline
                layout(constraints.maxWidth, constraints.maxHeight) {
                    reading.placeRelative((constraints.maxWidth - reading.width) / 2, baseY - reading.height)
                    base.placeRelative((constraints.maxWidth - base.width) / 2, baseY)
                }
            }
        }
    )
    return buildAnnotatedString {
        withStyle(span) { appendInlineContent(inlineKey, displayed) }
    } to mapOf(inlineKey to inline)
}

/** Reserve room on every line because the current Compose line-height span reuses the first line's metrics. */
internal fun readerRubyParagraphStyle(
    block: ReaderBlock,
    style: ComposeTextStyle,
    inline: Map<String, InlineTextContent>,
    textMeasurer: TextMeasurer,
    density: Density,
    textTransform: (String) -> String
): ComposeTextStyle {
    if (inline.isEmpty()) return style
    val parts = when (block) {
        is ReaderBlock.Paragraph -> block.parts
        is ReaderBlock.Text -> listOf(block)
        else -> return style
    }
    val descents = parts.filter { it.ruby != null }.mapNotNull { part ->
        val displayed = textTransform(part.value).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val span = part.styles.fold(SpanStyle()) { current, item -> current.merge(item.toSpanStyle()) }
        val inlineStyle = style.merge(span).copy(textIndent = TextIndent.None, textAlign = TextAlign.Start)
        val measured = textMeasurer.measure(displayed, inlineStyle, softWrap = false, maxLines = 1)
        (measured.size.height - measured.firstBaseline).coerceAtLeast(0f)
    }
    val tallest = inline.values.maxOf { with(density) { it.placeholder.height.toPx() } }
    val currentHeight = if (style.lineHeight.isSp) with(density) { style.lineHeight.toPx() } else 0f
    val heightPx = maxOf(currentHeight, tallest + (descents.maxOrNull() ?: 0f) + 2f)
    return style.copy(
        lineHeight = with(density) { heightPx.toDp().toSp() },
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Bottom, LineHeightStyle.Trim.None)
    )
}

private fun ReaderTextStyle.toSpanStyle(): SpanStyle = when (this) {
    ReaderTextStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    ReaderTextStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    ReaderTextStyle.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
    ReaderTextStyle.StrikeThrough -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    is ReaderTextStyle.ForegroundColor -> SpanStyle(color = Color(red, green, blue))
    is ReaderTextStyle.BackgroundColor -> SpanStyle(background = Color(red, green, blue))
    is ReaderTextStyle.FontSizePx -> SpanStyle(fontSize = value.coerceIn(8, 96).sp)
}
