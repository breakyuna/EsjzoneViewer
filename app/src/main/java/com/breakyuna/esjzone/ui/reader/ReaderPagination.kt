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
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterFormatting
import com.breakyuna.esjzone.domain.reader.ReaderLink
import com.breakyuna.esjzone.domain.reader.sourceText
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
        val startOffset: Int = 0,
        val sourceSignature: String = signature,
        val alignment: String? = null,
        val paragraphStyle: TextStyle? = null,
        val maxLines: Int = Int.MAX_VALUE
    ) : ReaderPageSegment
    data class Image(val url: String) : ReaderPageSegment
    data class Gap(val height: Dp) : ReaderPageSegment
    data class Divider(val blockIndex: Int, val startOffset: Int) : ReaderPageSegment
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
    textTransform: (String) -> String,
    formatting: ReaderChapterFormatting = ReaderChapterFormatting(),
    checkCancelled: () -> Unit = {}
): List<ReaderPage> {
    checkCancelled()
    if (widthPx <= 0 || heightPx <= 0) return emptyList()
    val pages = mutableListOf<ReaderPage>()
    val current = mutableListOf<ReaderPageSegment>()
    var usedHeight = 0
    var pageContentPosition = -1f
    val blankLineHeight = with(density) { settings.lineHeightSp.sp.toDp() }
    val blankLineHeightPx = with(density) { blankLineHeight.roundToPx() }
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
    fun addText(text: AnnotatedString, inline: Map<String, InlineTextContent>, blockIndex: Int,
        paragraphStyle: TextStyle, alignment: String?, paragraphSpacing: Dp) {
        val paragraphGap = with(density) { paragraphSpacing.roundToPx() }
        if (text.isEmpty()) {
            val gap = if (blocks[blockIndex].sourceText().isBlank()) blankLineHeight + paragraphSpacing
                else paragraphSpacing
            add(ReaderPageSegment.Gap(gap), with(density) { gap.roundToPx() }, blockIndex.toFloat())
            return
        }
        val layoutStyle = readerRubyParagraphStyle(blocks[blockIndex], paragraphStyle, inline,
            textMeasurer, density, textTransform)
        checkCancelled()
        val layout = textMeasurer.measure(
            text = text,
            style = layoutStyle,
            placeholders = placeholders(text, inline),
            constraints = Constraints(maxWidth = widthPx)
        )
        val signature = ReaderUnderlines.signature(blocks[blockIndex], text.text)
        val sourceSignature = ReaderUnderlines.sourceSignature(blocks[blockIndex])
        val usesBlockIndent = layoutStyle.textIndent?.let { it != TextIndent.None } == true &&
            text.paragraphStyles.isEmpty()
        var nextHardBreak = if (usesBlockIndent) text.text.indexOf('\n') else -1
        var startLine = 0
        while (startLine < layout.lineCount) {
            checkCancelled()
            if (current.isNotEmpty() && usedHeight >= heightPx) flush()
            val start = layout.getLineStart(startLine)
            val softContinuation = start > 0 && text.text[start - 1] != '\n'
            val fragmentStyle = if (softContinuation) layoutStyle.copy(textIndent = TextIndent.None) else layoutStyle
            // A soft-wrapped continuation shares the same Text node only until the next hard break.
            // The rest of the page can then use the ordinary first-line indent again.
            if (nextHardBreak in 0 until start) nextHardBreak = text.text.indexOf('\n', start)
            val endLimit = if (softContinuation && nextHardBreak in start until text.text.lastIndex) {
                layout.getLineForOffset(nextHardBreak) + 1
            } else layout.lineCount
            var endLine = startLine
            while (endLine < endLimit) {
                checkCancelled()
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
            // A substring is a new paragraph to Text: first/last-line metrics and
            // trailing newlines can differ from the original paragraph's line box.
            // Measure exactly what we render before committing it to this page.
            var segmentText: AnnotatedString
            var maxLines: Int
            var lineHeight: Int
            while (true) {
                checkCancelled()
                val end = layout.getLineEnd(endLine - 1)
                segmentText = text.subSequence(start, end)
                val ranges = placeholders(segmentText, inline)
                val constraints = Constraints(maxWidth = widthPx)
                val fragment = textMeasurer.measure(
                    segmentText, fragmentStyle, placeholders = ranges, constraints = constraints
                )
                val lastLine = fragment.lineCount - 1
                // Keep the newline in the source slice and omit the empty layout line created by cutting there.
                maxLines = if (endLine < layout.lineCount && lastLine > 0 &&
                    fragment.getLineStart(lastLine) == segmentText.length) lastLine else Int.MAX_VALUE
                lineHeight = if (maxLines == Int.MAX_VALUE) fragment.size.height else textMeasurer.measure(
                    segmentText, fragmentStyle, maxLines = maxLines, placeholders = ranges, constraints = constraints
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
                paragraphSpacing
            } else 0.dp
            add(ReaderPageSegment.TextLine(segmentText, inline, gap, blockIndex, signature, start,
                sourceSignature, alignment, fragmentStyle, maxLines),
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

    fun addDivider(blockIndex: Int, offset: Int) {
        if (blocks.isEmpty()) return
        val index = blockIndex.coerceIn(0, blocks.lastIndex)
        val position = index + if (offset > 0) 0.999f else 0f
        add(ReaderPageSegment.Divider(index, offset), with(density) { ReaderHorizontalRuleHeight.roundToPx() }, position)
    }
    repeat(formatting.dividersAt.count { it == 0 }) { addDivider(0, 0) }
    blocks.forEachIndexed { blockIndex, block ->
        checkCancelled()
        val alignment = formatting.alignments[blockIndex]
        val paragraphStyle = readerParagraphStyle(textStyle, alignment)
        val paragraphSpacing = settings.paragraphSpacingBefore(
            blocks.getOrNull(blockIndex + 1), blankLineHeight, blockIndex + 1 in formatting.dividersAt
        )
        var displayedLength = 0
        when {
            block.isBlankLine() ->
                add(ReaderPageSegment.Gap(blankLineHeight), blankLineHeightPx, blockIndex.toFloat())
            block is ReaderBlock.Paragraph -> {
                val (text, inline) = block.toAnnotatedReaderParagraph(paragraphStyle, textMeasurer, density,
                    textTransform, formatting.disclosureTitleParts[blockIndex], checkCancelled)
                displayedLength = text.length
                addText(text, inline, blockIndex, paragraphStyle, alignment, paragraphSpacing)
            }
            block is ReaderBlock.Text -> {
                val (text, inline) = block.toAnnotatedReaderText(paragraphStyle, textMeasurer, density, textTransform)
                displayedLength = text.length
                addText(text, inline, blockIndex, paragraphStyle, alignment, paragraphSpacing)
            }
            block is ReaderBlock.Image -> {
                flush()
                pages += ReaderPage(listOf(ReaderPageSegment.Image(block.url)), blockIndex.toFloat())
            }
        }
        repeat(formatting.dividersAt.count { it == blockIndex + 1 }) { addDivider(blockIndex, displayedLength) }
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
    textOffsets: (Int) -> com.breakyuna.esjzone.domain.reader.ReaderTextOffsets? = { null },
    links: Map<Int, List<ReaderLink>> = emptyMap(),
    onLinkClick: (String) -> Unit = {},
    bookmarkedBlocks: Set<Int> = emptySet(),
    bookmarkPicking: Boolean = false,
    onBookmarkSelect: (Int, Int) -> Unit = { _, _ -> }
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
                        style = segment.paragraphStyle ?: readerParagraphStyle(textStyle, segment.alignment),
                        maxLines = segment.maxLines,
                        color = contentColor,
                        modifier = (if (segment.alignment != null) Modifier.fillMaxWidth() else Modifier)
                            .padding(bottom = segment.bottomSpacing),
                        enabled = settings.longPressUnderline,
                        blockIndex = segment.blockIndex,
                        signature = segment.signature,
                        offset = segment.startOffset,
                        underlines = underlines,
                        onUnderline = onUnderline,
                        bookmarkPicking = bookmarkPicking,
                        onBookmarkSelect = onBookmarkSelect,
                        showMagnifier = false,
                        bookmarkMargin = if (ReaderBookmarkMarker.showsOn(segment.blockIndex, segment.startOffset, bookmarkedBlocks))
                            settings.horizontalPaddingDp.dp else null,
                        links = links[segment.blockIndex].orEmpty(),
                        linkOffsets = if (links[segment.blockIndex].isNullOrEmpty()) null else textOffsets(segment.blockIndex),
                        onLinkClick = onLinkClick,
                        sourceSignature = segment.sourceSignature,
                        textOffsets = textOffsets(segment.blockIndex),
                        highlights = highlights.filter { it.blockIndex == segment.blockIndex }.mapNotNull { range ->
                            textOffsets(segment.blockIndex)?.let { offsets ->
                                androidx.compose.ui.text.TextRange(
                                    (offsets.toDisplay(range.start) - segment.startOffset).coerceIn(0, segment.text.length),
                                    (offsets.toDisplayEnd(range.end) - segment.startOffset).coerceIn(0, segment.text.length))
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
                    is ReaderPageSegment.Divider -> ReaderHorizontalRule(contentColor)
                }
            }
        }
    }
    val underlineSelection = remember(page) { ReaderUnderlineSelection() }
    CompositionLocalProvider(LocalReaderUnderlineSelection provides underlineSelection,
        LocalContentColor provides contentColor) {
        Box {
            if (settings.longPressUnderline || bookmarkPicking) DisableSelection { content() } else SelectionContainer(content = content)
            ReaderUnderlineMagnifier(underlineSelection, Modifier.matchParentSize())
        }
    }
}
