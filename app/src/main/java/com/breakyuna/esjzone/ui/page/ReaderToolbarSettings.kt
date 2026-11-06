package com.breakyuna.esjzone.ui.page

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import com.breakyuna.esjzone.ui.designsystem.AppMotion
import com.breakyuna.esjzone.ui.designsystem.AppTouchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
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
}

internal fun ReaderTool.icon(isBookmarked: Boolean = false): ImageVector = when (this) {
    ReaderTool.CONTENTS -> Icons.Filled.List
    ReaderTool.SETTINGS -> Icons.Filled.Settings
    ReaderTool.BOOKMARK -> if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder
    ReaderTool.COMMENTS -> Icons.Filled.Forum
    ReaderTool.NOVEL_DETAIL -> Icons.AutoMirrored.Filled.MenuBook
    ReaderTool.EYE_PROTECTION -> Icons.Filled.Visibility
    ReaderTool.BRIGHTNESS -> Icons.Filled.Brightness6
    ReaderTool.DARK_MODE -> Icons.Filled.DarkMode
}

@Composable
internal fun ReaderToolbarSettings(
    tools: List<ReaderTool>,
    order: List<ReaderTool>,
    onOrderChange: (List<ReaderTool>) -> Unit,
    onChange: (List<ReaderTool>) -> Unit
) {
    var ordered by remember { mutableStateOf(order) }
    val savedOrder by rememberUpdatedState(order)
    var dragging by remember { mutableStateOf<ReaderTool?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(0) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val rowHeightDp = AppTouchTarget.minimum
    val rowHeight = with(LocalDensity.current) { rowHeightDp.toPx() }
    val commitOrder by rememberUpdatedState(onOrderChange)
    LaunchedEffect(order) { ordered = order }
    Text(stringResource(R.string.reader_toolbar_selected_count, tools.size, ReaderTool.MAX_VISIBLE),
        style = MaterialTheme.typography.labelLarge)
    Box(Modifier.fillMaxWidth().height(rowHeightDp * ordered.size)) {
        // Keyed rows retain their animation state as the display order changes.
        ordered.forEach { tool ->
            key(tool) {
                val selected = tool in tools
                val isDragging = dragging == tool
                val position = animateFloatAsState(
                    targetValue = if (isDragging) dragStartIndex * rowHeight + dragDistance
                        else ordered.indexOf(tool) * rowHeight,
                    animationSpec = if (isDragging) snap() else tween(AppMotion.standard, easing = AppMotion.standardEasing),
                    label = "ToolbarToolPosition"
                )
                Row(
                    Modifier.fillMaxWidth().height(rowHeightDp)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = position.value
                        }
                        .background(if (isDragging) MaterialTheme.colorScheme.surface else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selected,
                        enabled = selected || tools.size < ReaderTool.MAX_VISIBLE,
                        onCheckedChange = { checked ->
                            val selection = if (checked) tools + tool else tools - tool
                            onChange(ordered.filter { it in selection })
                        }
                    )
                    Icon(
                        tool.icon(),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(stringResource(tool.label()), modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    if (selected) {
                        val dragLabel = stringResource(R.string.reader_tool_drag)
                        Box(
                            Modifier.size(rowHeightDp)
                                .semantics { contentDescription = dragLabel }
                                .pointerInput(tool, rowHeight) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        // Release or another gesture consuming input before the hold
                                        // finishes leaves the surrounding settings page scrollable.
                                        val endedEarly = withTimeoutOrNull(150L) {
                                            waitForUpOrCancellation()
                                            true
                                        } != null
                                        if (endedEarly) return@awaitEachGesture
                                        val pointer = currentEvent.changes.firstOrNull { it.id == down.id && it.pressed }
                                            ?: currentEvent.changes.firstOrNull { it.pressed }
                                            ?: return@awaitEachGesture

                                        dragging = tool
                                        dragStartIndex = ordered.indexOf(tool)
                                        dragDistance = 0f
                                        var completed = false
                                        try {
                                            if (drag(pointer.id) { change ->
                                                    dragDistance += change.positionChange().y
                                                    change.consume()
                                                    val target = (dragStartIndex + dragDistance / rowHeight)
                                                        .roundToInt().coerceIn(0, ordered.lastIndex)
                                                    val current = ordered.indexOf(tool)
                                                    if (target != current) {
                                                        ordered = ordered.toMutableList().apply {
                                                            add(target, removeAt(current))
                                                        }
                                                    }
                                                }) {
                                                currentEvent.changes.filter { it.changedToUp() }.forEach { it.consume() }
                                                commitOrder(ordered)
                                                completed = true
                                            }
                                        } finally {
                                            if (!completed) ordered = savedOrder
                                            dragging = null
                                            dragDistance = 0f
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
