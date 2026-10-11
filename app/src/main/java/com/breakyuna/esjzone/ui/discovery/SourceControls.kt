package com.breakyuna.esjzone.ui.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
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
    val wenku = selected == LibrarySource.WENKU8
    val sourceName = selected?.let { sourceLabel(it) } ?: stringResource(R.string.source_all)
    val density = LocalDensity.current
    val travel = with(density) { 32.dp.toPx() }
    var dragOffset by remember { mutableStateOf<Float?>(null) }
    val currentSelect by rememberUpdatedState(onSelect)
    val position by animateFloatAsState(
        targetValue = if (wenku) 1f else 0f,
        animationSpec = tween(180), label = "sourcePosition"
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (includeAll) {
            TextButton(
                onClick = { onSelect(null) }, enabled = enabled,
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.defaultMinSize(minWidth = 1.dp).semantics { this.selected = selected == null }
            ) {
                Text(stringResource(R.string.source_all), style = AppTypography.labelSmall,
                    color = if (selected == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            Modifier.size(width = 60.dp, height = 48.dp)
                .semantics { contentDescription = sourceName }
                .toggleable(value = wenku, enabled = enabled, role = Role.Switch,
                    onValueChange = { onSelect(if (selected != null && it) LibrarySource.WENKU8 else LibrarySource.ESJZONE) })
                .draggable(
                    state = rememberDraggableState { delta ->
                        dragOffset = ((dragOffset ?: if (wenku) travel else 0f) + delta).coerceIn(0f, travel)
                    },
                    orientation = Orientation.Horizontal, enabled = enabled,
                    onDragStarted = { dragOffset = if (wenku) travel else 0f },
                    onDragStopped = {
                        val target = if ((dragOffset ?: 0f) >= travel / 2f) LibrarySource.WENKU8 else LibrarySource.ESJZONE
                        dragOffset = null
                        if (target != selected) currentSelect(target)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(60.dp, 28.dp).clearAndSetSemantics {}.clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .alpha(if (enabled) 1f else 0.38f)) {
                Box(Modifier.absoluteOffset(x = 2.dp, y = 2.dp).size(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.source_esjzone_short), style = AppTypography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.absoluteOffset(x = 34.dp, y = 2.dp).size(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.source_wenku8_short), style = AppTypography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected != null) Box(
                    Modifier.absoluteOffset { IntOffset((2.dp.toPx() + (dragOffset ?: (position * travel))).roundToInt(), 2.dp.roundToPx()) }
                        .size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(if (wenku) R.string.source_wenku8_short else R.string.source_esjzone_short), style = AppTypography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
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
