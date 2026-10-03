package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.domain.reader.ReaderUnderline

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

/** Long press underlines a word; dragging extends the range, and release saves it. */
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
    onUnderline: (ReaderUnderline, Boolean) -> Unit
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var selection by remember(text, enabled) { mutableStateOf<TextRange?>(null) }
    val currentSave by rememberUpdatedState(onUnderline)
    val displayed = remember(text, underlines, blockIndex, signature, offset, selection) {
        buildAnnotatedString {
            append(text)
            underlines.filter { it.blockIndex == blockIndex && it.signature == signature }.forEach { mark ->
                val start = (mark.start - offset).coerceAtLeast(0)
                val end = (mark.end - offset).coerceAtMost(text.length)
                if (start < end) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            }
            selection?.let { range ->
                if (!range.collapsed) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), range.min, range.max)
            }
        }
    }
    Text(text = displayed, inlineContent = inlineContent, style = style, color = color,
        overflow = TextOverflow.Visible, onTextLayout = { layout = it },
        modifier = modifier.then(if (!enabled) Modifier else Modifier.pointerInput(text, blockIndex, signature, offset) {
            var anchor = TextRange.Zero
            var position = Offset.Zero
            detectDragGesturesAfterLongPress(
                onDragStart = { point ->
                    layout?.takeIf { text.isNotEmpty() }?.let { result ->
                        position = point
                        val index = result.getOffsetForPosition(point).coerceIn(0, text.lastIndex)
                        anchor = result.getWordBoundary(index)
                        selection = anchor
                    }
                },
                onDrag = { change, amount ->
                    change.consume()
                    position += amount
                    layout?.let { result ->
                        val end = result.getOffsetForPosition(position).coerceIn(0, text.length)
                        selection = if (end < anchor.min) TextRange(end, anchor.max)
                            else TextRange(anchor.min, maxOf(end, anchor.max))
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
