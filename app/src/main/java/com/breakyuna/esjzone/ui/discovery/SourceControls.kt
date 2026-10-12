package com.breakyuna.esjzone.ui.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.AppMotion
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource

@Composable
internal fun sourceLabel(source: LibrarySource): String = stringResource(
    if (source == LibrarySource.ESJZONE) R.string.source_esjzone else R.string.source_wenku8
)

/** Compact source control shared by page headers. */
@Composable
internal fun SourceSelector(
    selected: LibrarySource?,
    onSelect: (LibrarySource?) -> Unit,
    modifier: Modifier = Modifier,
    includeAll: Boolean = false,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val segmentWidth = 49.dp
    val travel = with(density) { segmentWidth.toPx() }
    val position = remember { Animatable(if (selected == LibrarySource.WENKU8) 1f else 0f) }
    val scope = rememberCoroutineScope()
    var selectionJob by remember { mutableStateOf<Job?>(null) }
    var dragOffset by remember { mutableStateOf<Float?>(null) }
    var previewing by remember { mutableStateOf(false) }
    val currentSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)

    LaunchedEffect(selected, enabled) {
        selectionJob?.cancel()
        dragOffset = null
        previewing = false
        position.animateTo(if (selected == LibrarySource.WENKU8) 1f else 0f, AppMotion.standardSpec())
    }

    fun selectSource(source: LibrarySource) {
        selectionJob?.cancel()
        val releasePosition = dragOffset?.div(travel)
        dragOffset = null
        previewing = true
        selectionJob = scope.launch {
            if (releasePosition != null) position.snapTo(releasePosition)
            // Home and bookshelf replace the whole header when the source changes.
            // Finish the visible movement before invoking that replacement.
            position.animateTo(if (source == LibrarySource.WENKU8) 1f else 0f, AppMotion.standardSpec())
            if (source != currentSelected) currentSelect(source)
            previewing = false
        }
    }

    val progress = dragOffset?.div(travel) ?: position.value
    val showIndicator = selected != null || previewing || dragOffset != null
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (includeAll) {
            TextButton(
                onClick = {
                    selectionJob?.cancel()
                    previewing = false
                    if (selected != null) currentSelect(null)
                },
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (selected == null) colors.primary else colors.onSurfaceVariant
                ),
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.defaultMinSize(minWidth = 1.dp)
                    .semantics { this.selected = selected == null }
            ) {
                Text(stringResource(R.string.source_all), style = AppTypography.labelSmall)
            }
        }
        Box(
            Modifier.size(width = 104.dp, height = 48.dp)
                .alpha(if (enabled) 1f else 0.38f)
                .selectableGroup()
                .draggable(
                    state = rememberDraggableState { delta ->
                        dragOffset = ((dragOffset ?: (position.value * travel)) + delta).coerceIn(0f, travel)
                    },
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    reverseDirection = rtl,
                    onDragStarted = {
                        selectionJob?.cancel()
                        position.stop()
                        dragOffset = position.value * travel
                    },
                    onDragStopped = {
                        selectSource(if ((dragOffset ?: (position.value * travel)) >= travel / 2f) {
                            LibrarySource.WENKU8
                        } else LibrarySource.ESJZONE)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(104.dp, 36.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh)
                    .border(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f), CircleShape)
            ) {
                if (showIndicator) {
                    Box(
                        Modifier.offset {
                            IntOffset((3.dp.toPx() + progress * travel).roundToInt(), 3.dp.roundToPx())
                        }
                            .size(segmentWidth, 30.dp)
                            .shadow(2.dp, CircleShape)
                            .background(colors.primaryContainer, CircleShape)
                            .border(0.5.dp, colors.primary.copy(alpha = 0.12f), CircleShape)
                    )
                }
            }
            Row(Modifier.padding(horizontal = 3.dp)) {
                LibrarySource.entries.forEach { source ->
                    val label = sourceLabel(source)
                    val emphasis = if (!showIndicator) 0f else if (source == LibrarySource.WENKU8) progress else 1f - progress
                    Box(
                        Modifier.size(segmentWidth, 48.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = label }
                            .selectable(
                                selected = selected == source,
                                enabled = enabled,
                                role = Role.Tab,
                                onClick = { selectSource(source) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(if (source == LibrarySource.ESJZONE) R.string.source_esjzone_short else R.string.source_wenku8_short),
                            style = AppTypography.labelSmall,
                            color = lerp(colors.onSurfaceVariant, colors.onPrimaryContainer, emphasis),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SourceTitle(title: String, controls: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(title, modifier = Modifier.weight(1f, fill = false), style = AppTypography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        controls()
    }
}

@Composable
internal fun SourceBadge(source: LibrarySource, modifier: Modifier = Modifier) {
    Surface(modifier, shape = AppShapes.standard,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(sourceLabel(source), Modifier.padding(horizontal = AppSpacing.sm, vertical = 2.dp),
            style = AppTypography.labelMedium, maxLines = 1)
    }
}
