package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.domain.reader.ReaderUnderline

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Long press anchors one character; dragging selects through the character under the finger. */
@Composable
internal fun ReaderUnderlineText(
    text: AnnotatedString,
    inlineContent: Map<String, InlineTextContent>,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
    enabled: Boolean,
    blockIndex: Int,
    signature: String,
    offset: Int = 0,
    underlines: List<ReaderUnderline>,
    onUnderline: (ReaderUnderline, Boolean) -> Unit,
    onLayout: (TextLayoutResult?) -> Unit = {},
    highlights: List<TextRange> = emptyList()
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var selection by remember(text, enabled) { mutableStateOf<TextRange?>(null) }
    val currentSave by rememberUpdatedState(onUnderline)
    val currentLayout by rememberUpdatedState(onLayout)
    DisposableEffect(Unit) {
        onDispose { currentLayout(null) }
    }
    val underlineRanges = remember(text, underlines, blockIndex, signature, offset) {
        underlines.filter { it.blockIndex == blockIndex && it.signature == signature }.mapNotNull { mark ->
            val start = (mark.start - offset).coerceAtLeast(0)
            val end = (mark.end - offset).coerceAtMost(text.length)
            if (start < end) TextRange(start, end) else null
        }
    }
    val displayed = remember(text, highlights, color) {
        if (highlights.isEmpty()) text else buildAnnotatedString {
            append(text)
            highlights.forEach { range ->
                val start = range.min.coerceIn(0, text.length)
                val end = range.max.coerceIn(start, text.length)
                if (start < end) addStyle(SpanStyle(background = color.copy(alpha = 0.18f)), start, end)
            }
        }
    }
    Text(text = displayed, inlineContent = inlineContent, style = style, color = color,
        overflow = TextOverflow.Visible, onTextLayout = { layout = it; onLayout(it) },
        modifier = modifier.drawWithCache {
            val result = layout
            val lineOffset = 2.dp.toPx()
            val stroke = Stroke(width = lineOffset, cap = StrokeCap.Round)
            val previewColor = color.copy(alpha = 0.18f)
            // These caches live only as long as this text layout and drawing cache.
            val baselines = mutableMapOf<Int, Float>()
            val glyphs = mutableMapOf<Int, Pair<Offset, Offset>>()
            fun appendUnderline(path: Path, range: TextRange) {
                if (result == null) return
                var previousEnd: Offset? = null
                for (index in range.min until range.max) {
                    if (text[index] == '\n' || text[index] == '\r') {
                        previousEnd = null
                        continue
                    }
                    val (start, end) = glyphs.getOrPut(index) {
                        val bounds = result.getBoundingBox(index)
                        val line = result.getLineForOffset(index)
                        val y = baselines.getOrPut(line) { result.getLineBaseline(line) + lineOffset }
                        Offset(bounds.left, y) to Offset(bounds.right, y)
                    }
                    // Join only touching glyph boxes on the same baseline. Other
                    // directions and inline placeholders keep their actual boxes.
                    if (previousEnd != start) path.moveTo(start.x, start.y)
                    path.lineTo(end.x, end.y)
                    previousEnd = end
                }
            }
            val savedUnderline = Path().apply { underlineRanges.forEach { appendUnderline(this, it) } }
            val previewUnderline = Path()
            var previewRange: TextRange? = null
            var previewBackground: Path? = null
            onDrawWithContent {
                // Read drag state here so selection updates only invalidate drawing,
                // without rebuilding Text or the saved underline geometry.
                val range = selection
                if (range != previewRange) {
                    previewRange = range
                    previewUnderline.reset()
                    previewBackground = range?.let { result?.getPathForRange(it.min, it.max) }
                    range?.let { appendUnderline(previewUnderline, it) }
                }
                previewBackground?.let { drawPath(it, previewColor) }
                drawContent()
                drawPath(savedUnderline, Color.Red, style = stroke)
                drawPath(previewUnderline, Color.Red, style = stroke)
            }
        }.then(if (!enabled) Modifier else Modifier.pointerInput(text, blockIndex, signature, offset) {
            var anchor = TextRange.Zero
            var position = Offset.Zero
            fun characterAt(result: TextLayoutResult, point: Offset): TextRange {
                var index = result.getOffsetForPosition(point).coerceIn(0, text.lastIndex)
                // Hit testing returns a caret boundary. Use the glyph actually touched,
                // including its right half, instead of selecting the next character.
                if (index > 0 && !result.getBoundingBox(index).contains(point) &&
                    result.getBoundingBox(index - 1).contains(point)) index--
                return readerUnderlineCharacterRange(text.text, index)
            }
            detectDragGesturesAfterLongPress(
                onDragStart = { point ->
                    layout?.takeIf { text.isNotEmpty() }?.let { result ->
                        position = point
                        anchor = characterAt(result, point)
                        selection = anchor
                    }
                },
                onDrag = { change, amount ->
                    change.consume()
                    position += amount
                    layout?.takeIf { text.isNotEmpty() }?.let { result ->
                        val target = characterAt(result, position)
                        selection = TextRange(minOf(anchor.min, target.min), maxOf(anchor.max, target.max))
                    }
                },
                onDragEnd = {
                    selection?.takeUnless { it.collapsed }?.let { range ->
                        currentSave(ReaderUnderline(blockIndex, signature,
                            offset + range.min, offset + range.max), false)
                    }
                    selection = null
                },
                onDragCancel = { selection = null }
            )
        }))
}

internal fun readerUnderlineCharacterRange(text: String, index: Int): TextRange {
    if (text.isEmpty()) return TextRange.Zero
    var start = index.coerceIn(0, text.lastIndex)
    if (text[start].isLowSurrogate() && start > 0 && text[start - 1].isHighSurrogate()) start--
    return TextRange(start, start + Character.charCount(Character.codePointAt(text, start)))
}
