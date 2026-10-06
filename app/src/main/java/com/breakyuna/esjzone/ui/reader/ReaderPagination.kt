package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource

internal data class ReaderPage(
    val segments: List<ReaderPageSegment>,
    val contentPosition: Float,
    val isOversized: Boolean = false
)

internal sealed interface ReaderPageSegment {
    data class Heading(val name: String) : ReaderPageSegment
    data class TextLine(
        val text: AnnotatedString,
        val inlineContent: Map<String, InlineTextContent>,
        val bottomSpacing: Dp,
        val blockIndex: Int = 0,
        val signature: String = "",
        val startOffset: Int = 0
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
    var pageContentPosition = -1f
    val paragraphGap = with(density) { settings.paragraphSpacingDp.dp.roundToPx() }
    fun flush() {
        if (current.isNotEmpty()) {
            pages += ReaderPage(current.toList(), pageContentPosition, isOversized = usedHeight > heightPx)
            current.clear()
            usedHeight = 0
        }
    }
    fun add(segment: ReaderPageSegment, height: Int, position: Float) {
        if (current.isNotEmpty() && usedHeight + height > heightPx) flush()
        if (current.isEmpty()) pageContentPosition = position
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
    fun addText(text: AnnotatedString, inline: Map<String, InlineTextContent>, blockIndex: Int) {
        if (text.isEmpty()) {
            add(ReaderPageSegment.Gap(settings.paragraphSpacingDp.dp), paragraphGap, blockIndex.toFloat())
            return
        }
        val layout = textMeasurer.measure(
            text = text,
            style = textStyle,
            placeholders = placeholders(text, inline),
            constraints = Constraints(maxWidth = widthPx)
        )
        val signature = ReaderUnderlines.signature(blocks[blockIndex], text.text)
        var startLine = 0
        while (startLine < layout.lineCount) {
            if (current.isNotEmpty() && usedHeight >= heightPx) flush()
            var endLine = startLine
            while (endLine < layout.lineCount) {
                val nextHeight = layout.getLineBottom(endLine).toInt() - layout.getLineTop(startLine).toInt()
                if (current.isNotEmpty() && usedHeight + nextHeight > heightPx) break
                if (current.isEmpty() && nextHeight > heightPx && endLine > startLine) break
                endLine++
                if (usedHeight + nextHeight >= heightPx) break
            }
            if (endLine == startLine) {
                flush()
                continue
            }
            val start = layout.getLineStart(startLine)
            // A substring is a new paragraph to Text: first/last-line metrics and
            // trailing newlines can differ from the original paragraph's line box.
            // Measure exactly what we render before committing it to this page.
            var segmentText: AnnotatedString
            var lineHeight: Int
            while (true) {
                segmentText = text.subSequence(start, layout.getLineEnd(endLine - 1))
                lineHeight = textMeasurer.measure(
                    text = segmentText,
                    style = textStyle,
                    placeholders = placeholders(segmentText, inline),
                    constraints = Constraints(maxWidth = widthPx)
                ).size.height
                if (usedHeight + lineHeight <= heightPx) break
                if (endLine == startLine + 1) break
                endLine--
            }
            if (current.isNotEmpty() && usedHeight + lineHeight > heightPx) {
                flush()
                continue
            }
            val last = endLine == layout.lineCount
            val gap = if (last && usedHeight + lineHeight + paragraphGap <= heightPx) {
                settings.paragraphSpacingDp.dp
            } else 0.dp
            add(ReaderPageSegment.TextLine(segmentText, inline, gap, blockIndex, signature, start),
                lineHeight + with(density) { gap.roundToPx() }, blockIndex + start.toFloat() / text.length)
            startLine = endLine
            if (last && gap == 0.dp && paragraphGap > 0) flush()
        }
    }

    val headingHeight = textMeasurer.measure(
        text = textTransform(chapterName),
        style = headingStyle,
        constraints = Constraints(maxWidth = widthPx)
    ).size.height + with(density) { (settings.paragraphSpacingDp + 8f).dp.roundToPx() }
    add(ReaderPageSegment.Heading(chapterName), headingHeight, -1f)

    blocks.forEachIndexed { blockIndex, block ->
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
                addText(text, inline, blockIndex)
            }
            is ReaderBlock.Text -> {
                val (text, inline) = block.toAnnotatedReaderText(textStyle, textMeasurer, density, textTransform)
                addText(text, inline, blockIndex)
            }
            is ReaderBlock.Image -> {
                flush()
                pages += ReaderPage(listOf(ReaderPageSegment.Image(block.url)), blockIndex.toFloat())
            }
            ReaderBlock.LineBreak -> {
                val gap = with(density) { settings.lineHeightSp.sp.toDp() }
                add(ReaderPageSegment.Gap(gap), with(density) { gap.roundToPx() }, blockIndex.toFloat())
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
    availableHeight: Dp,
    underlines: List<ReaderUnderline> = emptyList(),
    onUnderline: (ReaderUnderline, Boolean) -> Unit = { _, _ -> },
    highlights: List<com.breakyuna.esjzone.domain.reader.ReaderHighlight> = emptyList(),
    textOffsets: (Int) -> com.breakyuna.esjzone.domain.reader.ReaderTextOffsets? = { null }
) {
    val content: @Composable () -> Unit = {
        // Ordinary pages keep their unclipped margins. A title or single line
        // taller than the viewport needs scrolling to remain fully readable.
        val contentModifier = if (page.isOversized) {
            Modifier.fillMaxWidth().heightIn(max = availableHeight)
                .verticalScroll(rememberScrollState())
        } else Modifier.fillMaxWidth()
        Column(contentModifier) {
            page.segments.forEach { segment ->
                when (segment) {
                    is ReaderPageSegment.Heading -> ReaderChapterHeading(
                        segment.name, settings, contentColor, textTransform, overflow = TextOverflow.Visible
                    )
                    is ReaderPageSegment.TextLine -> ReaderUnderlineText(
                        text = segment.text,
                        inlineContent = segment.inlineContent,
                        style = textStyle,
                        color = contentColor,
                        modifier = Modifier.padding(bottom = segment.bottomSpacing),
                        enabled = settings.longPressUnderline,
                        blockIndex = segment.blockIndex,
                        signature = segment.signature,
                        offset = segment.startOffset,
                        underlines = underlines,
                        onUnderline = onUnderline,
                        showMagnifier = false,
                        highlights = highlights.filter { it.blockIndex == segment.blockIndex }.mapNotNull { range ->
                            textOffsets(segment.blockIndex)?.let { offsets ->
                                androidx.compose.ui.text.TextRange(
                                    (offsets.toDisplay(range.start) - segment.startOffset).coerceIn(0, segment.text.length),
                                    (offsets.toDisplay(range.end) - segment.startOffset).coerceIn(0, segment.text.length))
                            }
                        }
                    )
                    is ReaderPageSegment.Image -> ReaderImage(
                        url = segment.url,
                        contentDescription = stringResource(R.string.reader_open_image),
                        contentColor = contentColor,
                        modifier = Modifier.heightIn(max = availableHeight),
                        contentScale = ContentScale.Fit
                    )
                    is ReaderPageSegment.Gap -> Spacer(Modifier.height(segment.height))
                }
            }
        }
    }
    val underlineSelection = remember(page) { ReaderUnderlineSelection() }
    CompositionLocalProvider(LocalReaderUnderlineSelection provides underlineSelection) {
        Box {
            if (settings.longPressUnderline) DisableSelection { content() } else SelectionContainer { content() }
            ReaderUnderlineMagnifier(underlineSelection, Modifier.matchParentSize())
        }
    }
}
