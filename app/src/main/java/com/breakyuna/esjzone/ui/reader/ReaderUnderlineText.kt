package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource

/** Long press selects a word; dragging extends the range without changing text layout or ruby. */
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
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var anchorHeight by remember { mutableIntStateOf(0) }
    var selection by remember(text, enabled) { mutableStateOf<TextRange?>(null) }
    var menu by remember(text, enabled) { mutableStateOf(false) }
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
                if (!range.collapsed) addStyle(SpanStyle(background = color.copy(alpha = 0.2f)), range.min, range.max)
            }
        }
    }
    val menuOffset = layout?.let { result ->
        selection?.takeIf { !it.collapsed && text.isNotEmpty() }?.let { range ->
            val bounds = result.getBoundingBox(range.min.coerceIn(0, text.lastIndex))
            with(density) {
                DpOffset(
                    (if (layoutDirection == LayoutDirection.Ltr) bounds.left
                        else result.size.width - bounds.right).toDp(),
                    (bounds.bottom - anchorHeight).toDp()
                )
            }
        }
    } ?: DpOffset.Zero
    Box(Modifier.onSizeChanged { anchorHeight = it.height }) {
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
                    onDragEnd = { menu = selection?.collapsed == false },
                    onDragCancel = { selection = null; menu = false }
                )
            }))
        DropdownMenu(expanded = menu, offset = menuOffset,
            onDismissRequest = { menu = false; selection = null }) {
            val range = selection
            if (range != null && !range.collapsed) {
                val mark = ReaderUnderline(blockIndex, signature, offset + range.min, offset + range.max)
                DropdownMenuItem(text = { Text(stringResource(android.R.string.copy)) }, onClick = {
                    clipboard.setText(text.subSequence(range.min, range.max)); menu = false; selection = null
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.reader_save_underline)) }, onClick = {
                    currentSave(mark, false); menu = false; selection = null
                })
                if (underlines.any { ReaderUnderlines.overlaps(it, mark) }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.reader_remove_underline)) }, onClick = {
                        currentSave(mark, true); menu = false; selection = null
                    })
                }
            }
        }
    }
}
