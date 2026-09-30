package com.breakyuna.esjzone.ui.reader

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource

internal data class ReaderPage(val segments: List<ReaderPageSegment>)

internal sealed interface ReaderPageSegment {
    data class Heading(val name: String) : ReaderPageSegment
    data class TextLine(
        val text: AnnotatedString,
        val inlineContent: Map<String, InlineTextContent>,
        val bottomSpacing: Dp
    ) : ReaderPageSegment
    data class Image(val url: String) : ReaderPageSegment
    data class Gap(val height: Dp) : ReaderPageSegment
}

/** Split at measured text-line boundaries so a fixed page never ends mid-line. */
internal fun paginateReaderChapter(
    chapterName: String,
    blocks: List<ReaderBlock>,
    settings: ReaderSettings,
    textStyle: TextStyle,
    headingStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    widthPx: Int,
    heightPx: Int,
    textTransform: (String) -> String
): List<ReaderPage> {
    if (widthPx <= 0 || heightPx <= 0) return emptyList()
    val pages = mutableListOf<ReaderPage>()
    val current = mutableListOf<ReaderPageSegment>()
    var usedHeight = 0
    val paragraphGap = with(density) { settings.paragraphSpacingDp.dp.roundToPx() }
    fun flush() {
        if (current.isNotEmpty()) {
            pages += ReaderPage(current.toList())
            current.clear()
            usedHeight = 0
        }
    }
    fun add(segment: ReaderPageSegment, height: Int) {
        if (current.isNotEmpty() && usedHeight + height > heightPx) flush()
        current += segment
        usedHeight += height
    }
    fun placeholders(
        text: AnnotatedString,
        content: Map<String, InlineTextContent>
    ): List<AnnotatedString.Range<Placeholder>> = text.getStringAnnotations(0, text.length)
        .mapNotNull { range ->
            content[range.item]?.let { AnnotatedString.Range(it.placeholder, range.start, range.end) }
        }
    fun addText(text: AnnotatedString, inline: Map<String, InlineTextContent>) {
        if (text.isEmpty()) {
            add(ReaderPageSegment.Gap(settings.paragraphSpacingDp.dp), paragraphGap)
            return
        }
        val layout = textMeasurer.measure(
            text = text,
            style = textStyle,
            placeholders = placeholders(text, inline),
            constraints = Constraints(maxWidth = widthPx)
        )
        var startLine = 0
        while (startLine < layout.lineCount) {
            if (current.isNotEmpty() && usedHeight >= heightPx) flush()
            var endLine = startLine
            var lineHeight = 0
            while (endLine < layout.lineCount) {
                val nextHeight = layout.getLineBottom(endLine).toInt() - layout.getLineTop(startLine).toInt()
                if (current.isNotEmpty() && usedHeight + nextHeight > heightPx) break
                if (current.isEmpty() && nextHeight > heightPx && endLine > startLine) break
                lineHeight = nextHeight
                endLine++
                if (usedHeight + nextHeight >= heightPx) break
            }
            if (endLine == startLine) {
                flush()
                continue
            }
            val start = layout.getLineStart(startLine)
            val end = layout.getLineEnd(endLine - 1)
            val last = endLine == layout.lineCount
            val gap = if (last && usedHeight + lineHeight + paragraphGap <= heightPx) {
                settings.paragraphSpacingDp.dp
            } else 0.dp
            add(ReaderPageSegment.TextLine(text.subSequence(start, end), inline, gap),
                lineHeight + with(density) { gap.roundToPx() })
            startLine = endLine
            if (last && gap == 0.dp && paragraphGap > 0) flush()
        }
    }

    val headingHeight = textMeasurer.measure(
        text = textTransform(chapterName),
        style = headingStyle,
        constraints = Constraints(maxWidth = widthPx)
    ).size.height + with(density) { (settings.paragraphSpacingDp + 8f).dp.roundToPx() }
    add(ReaderPageSegment.Heading(chapterName), headingHeight)

    blocks.forEach { block ->
        when (block) {
            is ReaderBlock.Paragraph -> {
                val inline = linkedMapOf<String, InlineTextContent>()
                val text = androidx.compose.ui.text.buildAnnotatedString {
                    block.parts.forEach { part ->
                        val (value, content) = part.toAnnotatedReaderText(textStyle, textMeasurer, density, textTransform)
                        append(value)
                        inline.putAll(content)
                    }
                }
                addText(text, inline)
            }
            is ReaderBlock.Text -> {
                val (text, inline) = block.toAnnotatedReaderText(textStyle, textMeasurer, density, textTransform)
                addText(text, inline)
            }
            is ReaderBlock.Image -> {
                flush()
                pages += ReaderPage(listOf(ReaderPageSegment.Image(block.url)))
            }
            ReaderBlock.LineBreak -> {
                val gap = with(density) { settings.lineHeightSp.sp.toDp() }
                add(ReaderPageSegment.Gap(gap), with(density) { gap.roundToPx() })
            }
        }
    }
    flush()
    return pages
}

@Composable
internal fun ReaderPageContent(
    page: ReaderPage,
    settings: ReaderSettings,
    textStyle: TextStyle,
    contentColor: Color,
    textTransform: (String) -> String,
    availableHeight: Dp
) {
    SelectionContainer {
        Column(Modifier.fillMaxWidth().heightIn(max = availableHeight)
            .verticalScroll(rememberScrollState())) {
            page.segments.forEach { segment ->
                when (segment) {
                    is ReaderPageSegment.Heading -> ReaderChapterHeading(
                        segment.name, settings, contentColor, textTransform
                    )
                    is ReaderPageSegment.TextLine -> Text(
                        text = segment.text,
                        inlineContent = segment.inlineContent,
                        style = textStyle,
                        color = contentColor,
                        modifier = Modifier.padding(bottom = segment.bottomSpacing)
                    )
                    is ReaderPageSegment.Image -> ReaderImage(
                        url = segment.url,
                        contentDescription = stringResource(R.string.reader_open_image),
                        modifier = Modifier.heightIn(max = availableHeight),
                        contentScale = ContentScale.Fit
                    )
                    is ReaderPageSegment.Gap -> Spacer(Modifier.height(segment.height))
                }
            }
        }
    }
}
