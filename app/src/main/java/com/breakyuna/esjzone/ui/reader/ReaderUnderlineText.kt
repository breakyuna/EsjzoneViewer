package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.domain.reader.ReaderUnderline

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.globalStringResource
import kotlin.math.roundToInt
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
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
    highlights: List<TextRange> = emptyList(),
    showMagnifier: Boolean = true
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val sharedSelection = LocalReaderUnderlineSelection.current
    val selection = sharedSelection ?: remember { ReaderUnderlineSelection() }
    val chapter = LocalReaderUnderlineChapter.current
    var menu by remember(text, enabled) { mutableStateOf<Pair<ReaderUnderline, Offset>?>(null) }
    val currentSave by rememberUpdatedState(onUnderline)
    val currentLayout by rememberUpdatedState(onLayout)
    val currentUnderlines by rememberUpdatedState(underlines)
    val segment = remember(selection, chapter, text, blockIndex, signature, offset) {
        ReaderUnderlineSelection.Segment(chapter, blockIndex, signature, offset, text.text) { mark, remove ->
            currentSave(mark, remove)
        }
    }
    val selectedRange by remember(selection, segment) {
        derivedStateOf(structuralEqualityPolicy()) { selection.ranges[segment] }
    }
    DisposableEffect(selection, segment, enabled) {
        if (enabled) selection.register(segment)
        onDispose { selection.unregister(segment) }
    }
    DisposableEffect(Unit) {
        onDispose { currentLayout(null) }
    }
    val underlineRanges = remember(text, underlines, blockIndex, signature, offset) {
        underlines.flatMap { it.ranges() }.filter { it.blockIndex == blockIndex && it.signature == signature }.mapNotNull { mark ->
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
    val magnifierModifier = if (!showMagnifier) Modifier else {
        val source by remember(selection, segment) {
            derivedStateOf(structuralEqualityPolicy()) {
                selection.target?.takeIf { it.first == segment }?.second ?: Offset.Unspecified
            }
        }
        Modifier.magnifier(
            sourceCenter = { source },
            magnifierCenter = {
                val point = source
                if (!point.isSpecified) Offset.Unspecified else {
                    val windowY = segment.coordinates?.localToWindow(point)?.y ?: 0f
                    point + Offset(0f, if (windowY >= 96.dp.toPx()) -64.dp.toPx() else 64.dp.toPx())
                }
            },
            zoom = 1.8f, size = DpSize(112.dp, 48.dp), cornerRadius = 12.dp
        )
    }
    Text(text = displayed, inlineContent = inlineContent, style = style, color = color,
        overflow = TextOverflow.Visible, onTextLayout = { layout = it; segment.layout = it; onLayout(it) },
        modifier = modifier.onGloballyPositioned { segment.coordinates = it }
            .then(magnifierModifier).drawWithCache {
            val result = layout
            val lineOffset = 3.dp.toPx()
            // The stroke extends above its center; keep its width separate from the baseline offset.
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            val previewColor = color.copy(alpha = 0.18f)
            // These caches live only as long as this text layout and drawing cache.
            val baselines = mutableMapOf<Int, Float>()
            val visibleLineEnds = mutableMapOf<Int, Int>()
            val glyphs = mutableMapOf<Int, Pair<Offset, Offset>>()
            val skippedGlyph = Offset.Unspecified to Offset.Unspecified
            fun appendUnderline(path: Path, range: TextRange) {
                if (result == null) return
                var previousEnd: Offset? = null
                for (index in range.min until range.max) {
                    val (start, end) = glyphs.getOrPut(index) {
                        if (text[index].isWhitespace()) return@getOrPut skippedGlyph
                        val line = result.getLineForOffset(index)
                        val visibleEnd = visibleLineEnds.getOrPut(line) {
                            var end = result.getLineEnd(line, visibleEnd = true)
                            val start = result.getLineStart(line)
                            // Include Unicode whitespace and a final line without a newline.
                            while (end > start && text[end - 1].isWhitespace()) end--
                            end
                        }
                        if (index >= visibleEnd) return@getOrPut skippedGlyph
                        val bounds = result.getBoundingBox(index)
                        if (bounds.left >= bounds.right) return@getOrPut skippedGlyph
                        val y = baselines.getOrPut(line) { result.getLineBaseline(line) + lineOffset }
                        Offset(bounds.left, y) to Offset(bounds.right, y)
                    }
                    if (!start.isSpecified) {
                        previousEnd = null
                        continue
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
                val range = selectedRange
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
        }.pointerInput(text, blockIndex, signature, offset, enabled) {
            // Only consume a completed tap on a saved mark, leaving ordinary reader taps alone.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val result = layout ?: return@awaitEachGesture
                if (text.isEmpty()) return@awaitEachGesture
                val character = readerUnderlineCharacterAt(result, down.position)
                val bounds = result.getBoundingBox(character.min)
                val mark = currentUnderlines.firstOrNull {
                    it.ranges().any { range -> range.blockIndex == blockIndex && range.signature == signature &&
                        offset + character.min in range.start until range.end }
                }
                val up = waitForUpOrCancellation()
                if (mark != null && bounds.inflate(4.dp.toPx()).contains(down.position) && up != null &&
                    (up.position - down.position).getDistance() < viewConfiguration.touchSlop &&
                    up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                    up.consume()
                    menu = mark to bounds.center
                }
            }
        }.then(if (!enabled) Modifier else Modifier.pointerInput(segment, selection) {
            var position = Offset.Zero
            try {
                detectDragGesturesAfterLongPress(
                    onDragStart = { point ->
                        menu = null
                        position = point
                        selection.start(segment, point)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        position += amount
                        selection.move(segment, position)
                    },
                    onDragEnd = { selection.finish() },
                    onDragCancel = { selection.cancel() }
                )
            } finally {
                selection.cancel()
            }
        }))
    menu?.let { (mark, point) ->
        val popupSpacing = with(LocalDensity.current) { 24.dp.toPx() }
        Popup(
            popupPositionProvider = remember(segment, point, popupSpacing) {
                object : PopupPositionProvider {
                    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                        val anchor = segment.coordinates?.takeIf { it.isAttached }?.localToWindow(point) ?: Offset.Zero
                        val x = (anchor.x - popupContentSize.width / 2).roundToInt()
                        val y = (anchor.y - popupContentSize.height - popupSpacing).roundToInt()
                        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                            (if (y >= 0) y else (anchor.y + popupSpacing).roundToInt())
                                .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
                    }
                }
            },
            onDismissRequest = { menu = null },
            properties = PopupProperties(focusable = true)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 6.dp) {
                TextButton(onClick = { currentSave(mark, true); menu = null }) {
                    Text(globalStringResource(R.string.reader_remove_underline))
                }
            }
        }
    }
}

/** A page owns one lens outside its split text nodes and converts the active glyph to its canvas. */
@Composable
internal fun ReaderUnderlineMagnifier(selection: ReaderUnderlineSelection, modifier: Modifier) {
    val visible by remember(selection) {
        derivedStateOf(structuralEqualityPolicy()) { selection.target != null }
    }
    if (!visible) return
    var coordinates by remember(selection) { mutableStateOf<LayoutCoordinates?>(null) }
    fun sourceCenter(): Offset {
        val target = selection.target ?: return Offset.Unspecified
        val canvas = coordinates?.takeIf { it.isAttached } ?: return Offset.Unspecified
        val text = target.first.coordinates?.takeIf { it.isAttached } ?: return Offset.Unspecified
        return canvas.localPositionOf(text, target.second)
    }
    Box(modifier.onGloballyPositioned { coordinates = it }.magnifier(
        sourceCenter = { sourceCenter() },
        magnifierCenter = {
            val target = selection.target
            val source = sourceCenter()
            if (target == null || !source.isSpecified) Offset.Unspecified else {
                val windowY = target.first.coordinates?.takeIf { it.isAttached }?.localToWindow(target.second)?.y ?: 0f
                source + Offset(0f, if (windowY >= 96.dp.toPx()) -64.dp.toPx() else 64.dp.toPx())
            }
        },
        zoom = 1.8f, size = DpSize(112.dp, 48.dp), cornerRadius = 12.dp
    ))
}

internal fun readerUnderlineCharacterRange(text: String, index: Int): TextRange {
    if (text.isEmpty()) return TextRange.Zero
    var start = index.coerceIn(0, text.lastIndex)
    if (text[start].isLowSurrogate() && start > 0 && text[start - 1].isHighSurrogate()) start--
    return TextRange(start, start + Character.charCount(Character.codePointAt(text, start)))
}
