package com.breakyuna.esjzone.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderBrightnessSheet(
    visible: Boolean,
    brightness: Float,
    onChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val followsSystem = brightness < 0f
    val brightnessLabel = stringResource(R.string.reader_brightness)
    val sliderColors = SliderDefaults.colors(
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
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
            Column(Modifier.padding(bottom = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(brightnessLabel,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = followsSystem,
                        onClick = { onChange(if (followsSystem) 0.5f else -1f) },
                        label = { Text(stringResource(R.string.reader_brightness_system)) }
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (followsSystem) 0.38f else 1f)
                    Icon(Icons.Default.BrightnessLow, contentDescription = null,
                        tint = tint, modifier = Modifier.size(20.dp))
                    Slider(
                        value = if (followsSystem) 0.5f else brightness,
                        onValueChange = onChange,
                        valueRange = 0.01f..1f,
                        enabled = !followsSystem,
                        colors = sliderColors,
                        thumb = {
                            Surface(
                                modifier = Modifier.size(20.dp),
                                shape = CircleShape,
                                color = if (followsSystem) MaterialTheme.colorScheme.outlineVariant
                                    else MaterialTheme.colorScheme.primary
                            ) { }
                        },
                        track = { state ->
                            SliderDefaults.Track(
                                sliderState = state,
                                modifier = Modifier.height(8.dp),
                                enabled = !followsSystem,
                                colors = sliderColors,
                                thumbTrackGapSize = 0.dp,
                                drawStopIndicator = null
                            )
                        },
                        modifier = Modifier.weight(1f).semantics { contentDescription = brightnessLabel }
                    )
                    Icon(Icons.Default.BrightnessHigh, contentDescription = null,
                        tint = tint, modifier = Modifier.size(20.dp))
                    Text(
                        text = if (followsSystem) "—" else "${(brightness * 100).roundToInt()}%",
                        modifier = Modifier.width(44.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = tint
                    )
                }
            }
        }
    }
}
