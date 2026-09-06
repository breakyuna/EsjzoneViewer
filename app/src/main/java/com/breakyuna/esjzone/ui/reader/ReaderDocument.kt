package com.breakyuna.esjzone.ui.reader

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
import com.breakyuna.esjzone.novellibrary.component.BackgroundColorTextStyle
import com.breakyuna.esjzone.novellibrary.component.BoldTextStyle
import com.breakyuna.esjzone.novellibrary.component.ColorTextStyle
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.component.FontSizeTextStyle
import com.breakyuna.esjzone.novellibrary.component.FuriganaTextStyle
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.ItalicTextStyle
import com.breakyuna.esjzone.novellibrary.component.LineThroughTextStyle
import com.breakyuna.esjzone.novellibrary.component.NewLineComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.TextStyle
import com.breakyuna.esjzone.novellibrary.component.UnderlineTextStyle
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
        blocks = content.flatMap(Component::toReaderBlocks),
        previous = previous?.let { ReaderChapterRef(it.name, it.url) },
        next = next?.let { ReaderChapterRef(it.name, it.url) },
        contentHtml = contentHtml,
        sourceUrl = sourceUrl
    )

private fun Component.toReaderBlocks(): List<ReaderBlock> = when (this) {
    is ImageComponent -> listOf(ReaderBlock.Image(url))
    is NewLineComponent -> listOf(ReaderBlock.LineBreak)
    is TextComponent -> {
        val reading = getStyles()
            .filterIsInstance<FuriganaTextStyle>()
            .firstOrNull()
            ?.readingText()
            ?.let { it.text + it.getExtras().joinToString("") { extra -> extra.text } }
        listOf(
            ReaderBlock.Text(
                value = text,
                styles = getStyles().mapNotNull(TextStyle::toReaderStyle).toSet(),
                ruby = reading?.let {
                    com.breakyuna.esjzone.domain.reader.ReaderRuby(text, it)
                }
            )
        ) + getExtras().flatMap(TextComponent::toReaderBlocks)
    }
    else -> emptyList()
}

private fun TextStyle.toReaderStyle(): ReaderTextStyle? = when {
    this === BoldTextStyle -> ReaderTextStyle.Bold
    this === ItalicTextStyle -> ReaderTextStyle.Italic
    this === UnderlineTextStyle -> ReaderTextStyle.Underline
    this === LineThroughTextStyle -> ReaderTextStyle.StrikeThrough
    this is FontSizeTextStyle -> ReaderTextStyle.FontSizePx(size())
    this is ColorTextStyle -> color().toReaderColor { red, green, blue ->
        ReaderTextStyle.ForegroundColor(red, green, blue)
    }
    this is BackgroundColorTextStyle -> color().toReaderColor { red, green, blue ->
        ReaderTextStyle.BackgroundColor(red, green, blue)
    }
    this is FuriganaTextStyle -> null
    else -> null
}

private fun Color.toReaderColor(factory: (Int, Int, Int) -> ReaderTextStyle): ReaderTextStyle =
    factory((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

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
    if (ruby == null) {
        return buildAnnotatedString {
            withStyle(span) { append(displayed) }
        } to emptyMap()
    }

    val key = "reader-ruby-${value.hashCode()}-${ruby.reading.hashCode()}"
    val measured = textMeasurer.measure(displayed, baseStyle.merge(span)).size
    val measuredWidth = with(density) { measured.width.toDp().toSp() }
    val width = if (measuredWidth.value < 1f) 1.sp else measuredWidth
    val height = (baseStyle.lineHeight.value * 1.45f).coerceAtLeast(18f).sp
    val inline = InlineTextContent(
        placeholder = androidx.compose.ui.text.Placeholder(
            width = width,
            height = height,
            placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.AboveBaseline
        ),
        children = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = textTransform(ruby.reading),
                    style = baseStyle.copy(
                        fontSize = (baseStyle.fontSize.value * 0.56f).coerceAtLeast(8f).sp,
                        lineHeight = (baseStyle.fontSize.value * 0.65f).coerceAtLeast(9f).sp
                    ),
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
