package com.breakyuna.esjzone.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.reader.ReaderTool

internal fun ReaderTool.label(): Int = when (this) {
    ReaderTool.CONTENTS -> R.string.reader_contents
    ReaderTool.SETTINGS -> R.string.reader_settings
    ReaderTool.BOOKMARK -> R.string.reader_add_bookmark
    ReaderTool.COMMENTS -> R.string.comments
    ReaderTool.NOVEL_DETAIL -> R.string.reader_open_novel_detail
    ReaderTool.EYE_PROTECTION -> R.string.reader_eye_protection
    ReaderTool.BRIGHTNESS -> R.string.reader_brightness
    ReaderTool.DARK_MODE -> R.string.reader_dark_mode
    ReaderTool.LAYOUT -> R.string.reader_layout_toggle
    ReaderTool.VOLUME_KEYS -> R.string.reader_volume_paging
    ReaderTool.SCRIPT -> R.string.reader_script
}

@Composable
internal fun ReaderToolbarSettings(tools: List<ReaderTool>, onChange: (List<ReaderTool>) -> Unit) {
    Text(stringResource(R.string.reader_toolbar_selected_count, tools.size, ReaderTool.MAX_VISIBLE),
        style = MaterialTheme.typography.labelLarge)
    val ordered = tools + ReaderTool.entries.filterNot { it in tools }
    ordered.forEach { tool ->
        val index = tools.indexOf(tool)
        val selected = index >= 0
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = selected,
                enabled = selected || tools.size < ReaderTool.MAX_VISIBLE,
                onCheckedChange = { checked -> onChange(if (checked) tools + tool else tools - tool) }
            )
            Text(stringResource(tool.label()), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (selected) {
                fun move(to: Int) {
                    val moved = tools.toMutableList()
                    moved.add(to, moved.removeAt(index))
                    onChange(moved)
                }
                IconButton(enabled = index > 0, onClick = { move(index - 1) }) {
                    Icon(Icons.Default.ArrowUpward, stringResource(R.string.reader_tool_move_before))
                }
                IconButton(enabled = index < tools.lastIndex, onClick = { move(index + 1) }) {
                    Icon(Icons.Default.ArrowDownward, stringResource(R.string.reader_tool_move_after))
                }
            }
        }
    }
}

@Composable
internal fun ReaderBrightnessSheet(
    visible: Boolean,
    brightness: Float,
    onChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp
        ) {
            Column(Modifier.padding(bottom = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.reader_brightness),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.reader_brightness_system), modifier = Modifier.weight(1f))
                    Switch(checked = brightness < 0f, onCheckedChange = { onChange(if (it) -1f else 0.5f) })
                }
                Slider(
                    value = if (brightness < 0f) 0.5f else brightness,
                    onValueChange = onChange, valueRange = 0.01f..1f, enabled = brightness >= 0f,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }
    }
}
