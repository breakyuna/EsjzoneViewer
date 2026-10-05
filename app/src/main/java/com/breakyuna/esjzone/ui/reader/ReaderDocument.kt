package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.data.repository.toReaderBlocks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.ReaderTextStyle
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter

/**
 * Presentation adapter for the already parsed chapter model. The parser and
 * persistence contracts stay in their existing layers; Reader UI consumes the
 * stable domain AST below this seam.
 */
fun DetailedChapter.toReaderDocument(chapter: Chapter): ReaderChapterDocument =
    ReaderChapterDocument(
        chapter = ReaderChapterRef(chapter.name, chapter.url),
        blocks = body?.readerBlocks { imageLocations[it] ?: it } ?: content.flatMap(Component::toReaderBlocks),
        previous = previous?.let { ReaderChapterRef(it.name, it.url) },
        next = next?.let { ReaderChapterRef(it.name, it.url) },
        contentHtml = null,
        sourceUrl = sourceUrl,
        contentFingerprint = body?.fingerprint.orEmpty()
    )

private data class RubyMeasureKey(
    val text: String,
    val reading: String,
    val fontSizeSp: Float,
    val fontWeight: FontWeight?,
    val fontStyle: FontStyle?,
    val fontFamily: String?,
    val densityDensity: Float,
    val fontScale: Float
)

/** Ruby inline content retains its base text as the annotated string's alternate text. */
internal fun ReaderBlock.renderedReaderText(textTransform: (String) -> String): String = when (this) {
    is ReaderBlock.Paragraph -> parts.joinToString("") { it.renderedReaderText(textTransform) }
    is ReaderBlock.Text -> textTransform(value)
    else -> ""
}

private val rubyMeasureCache = object : LinkedHashMap<RubyMeasureKey, Pair<androidx.compose.ui.unit.TextUnit, androidx.compose.ui.unit.TextUnit>>(128, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RubyMeasureKey, Pair<androidx.compose.ui.unit.TextUnit, androidx.compose.ui.unit.TextUnit>>?): Boolean {
        return size > 256
    }
}

/** Converts one domain text block to an annotated string while preserving inline ruby. */
internal fun ReaderBlock.Text.toAnnotatedReaderText(
    baseStyle: ComposeTextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    textTransform: (String) -> String
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val displayed = textTransform(value)
    val span = styles.fold(SpanStyle()) { current, style ->
        current.merge(style.toSpanStyle())
    }
    val rubyValue = ruby
    if (rubyValue == null) {
        return buildAnnotatedString {
            withStyle(span) { append(displayed) }
        } to emptyMap()
    }

    val key = "reader-ruby-${value.hashCode()}-${rubyValue.reading.hashCode()}"
    val effectiveStyle = baseStyle.merge(span)
    val displayedReading = textTransform(rubyValue.reading)
    val measureKey = RubyMeasureKey(
        text = displayed,
        reading = displayedReading,
        fontSizeSp = effectiveStyle.fontSize.value,
        fontWeight = effectiveStyle.fontWeight,
        fontStyle = effectiveStyle.fontStyle,
        fontFamily = effectiveStyle.fontFamily?.toString(),
        densityDensity = density.density,
        fontScale = density.fontScale
    )
    val rubyStyle = baseStyle.copy(
        fontSize = (baseStyle.fontSize.value * 0.56f).coerceAtLeast(8f).sp,
        lineHeight = (baseStyle.fontSize.value * 0.65f).coerceAtLeast(9f).sp
    )
    val (width, height) = synchronized(rubyMeasureCache) {
        rubyMeasureCache[measureKey]
    } ?: run {
        val baseWidth = textMeasurer.measure(displayed, effectiveStyle).size.width
        val rubyWidth = textMeasurer.measure(displayedReading, rubyStyle).size.width
        val measuredWidth = with(density) { maxOf(baseWidth, rubyWidth).toDp().toSp() }
        val w = if (measuredWidth.value < 1f) 1.sp else measuredWidth
        val h = (baseStyle.lineHeight.value * 1.45f).coerceAtLeast(18f).sp
        val dimensions = w to h
        synchronized(rubyMeasureCache) {
            rubyMeasureCache[measureKey] = dimensions
        }
        dimensions
    }
    val inline = InlineTextContent(
        placeholder = androidx.compose.ui.text.Placeholder(
            width = width,
            height = height,
            placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.AboveBaseline
        ),
        children = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = textTransform(rubyValue.reading),
                    style = rubyStyle,
                    maxLines = 1
                )
                Text(text = displayed, style = baseStyle.merge(span), maxLines = 1)
            }
        }
    )
    return buildAnnotatedString {
        withStyle(span) { appendInlineContent(key, displayed) }
    } to mapOf(key to inline)
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
