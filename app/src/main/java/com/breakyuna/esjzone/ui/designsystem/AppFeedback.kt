package com.breakyuna.esjzone.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** A compact feedback block for inline validation and one-off messages. */
@Composable
fun AppFeedback(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppSpacing.xl)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Text(title, style = AppTypography.titleMedium, color = appStateColors().content)
        Text(message, style = AppTypography.bodyMedium, color = appStateColors().contentMuted)
        if (actionLabel != null && onAction != null) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** Full-size loading state for data-driven destinations. */
@Composable
fun AppLoadingState(
    modifier: Modifier = Modifier.fillMaxSize(),
    message: String? = null
) {
    Box(
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            message?.takeIf(String::isNotBlank)?.let {
                Text(it, style = AppTypography.bodyMedium, color = appStateColors().contentMuted)
            }
        }
    }
}

/** Small inline loading indicator for buttons, rows, and compact cards. */
@Composable
fun AppLoading(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    CircularProgressIndicator(
        modifier = modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        strokeWidth = 2.5.dp
    )
}

/** Empty state with an optional action. The title is the accessible state label. */
@Composable
fun AppEmptyState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    AppStateMessage(
        title = title,
        message = message,
        modifier = modifier,
        icon = icon,
        actionLabel = actionLabel,
        onAction = onAction
    )
}

/** Error state with an explicit retry action. */
@Composable
fun AppErrorState(
    title: String,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.WarningAmber
) {
    AppStateMessage(
        title = title,
        message = message,
        modifier = modifier,
        icon = icon,
        actionLabel = retryLabel,
        onAction = onRetry,
        accent = appStateColors().danger
    )
}

/** Offline state that can still host cached content or a reconnect action. */
@Composable
fun AppOfflineState(
    title: String,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.CloudOff
) {
    AppStateMessage(
        title = title,
        message = message,
        modifier = modifier,
        icon = icon,
        actionLabel = retryLabel,
        onAction = onRetry,
        accent = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
private fun AppStateMessage(
    title: String,
    message: String?,
    modifier: Modifier,
    icon: ImageVector,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xxxl)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = accent.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
        }
        Text(title, style = AppTypography.titleMedium, color = appStateColors().content)
        message?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = AppTypography.bodyMedium,
                color = appStateColors().contentMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = AppSpacing.xs),
                shape = RoundedCornerShape(20.dp)
            ) { Text(actionLabel) }
        }
    }
}

/** Standard confirmation dialog used by destructive and account actions. */
@Composable
fun AppDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = if (dismissLabel != null && onDismiss != null) {
            { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
        } else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    if (visible) {
        ModalBottomSheet(onDismissRequest = onDismissRequest) { content() }
    }
}

enum class AppSideSheetEdge {
    START,
    END
}

@Composable
fun AppSideSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    edge: AppSideSheetEdge = AppSideSheetEdge.START,
    shape: Shape = if (edge == AppSideSheetEdge.START) {
        RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    },
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 2.dp,
    shadowElevation: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppMotion.standardSpec()),
        exit = fadeOut(AppMotion.standardSpec()),
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        BackHandler(onBack = onDismissRequest)
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .pointerInput(onDismissRequest) {
                        detectTapGestures { onDismissRequest() }
                    }
            )

            Surface(
                modifier = Modifier
                    .align(if (edge == AppSideSheetEdge.START) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
                    .widthIn(min = 280.dp, max = 340.dp)
                    .animateEnterExit(
                        enter = slideInHorizontally(
                            animationSpec = AppMotion.expressiveSpec(),
                            initialOffsetX = { fullWidth -> if (edge == AppSideSheetEdge.START) -fullWidth else fullWidth }
                        ),
                        exit = slideOutHorizontally(
                            animationSpec = AppMotion.standardSpec(),
                            targetOffsetX = { fullWidth -> if (edge == AppSideSheetEdge.START) -fullWidth else fullWidth }
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    },
                shape = shape,
                color = containerColor,
                contentColor = contentColor,
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation
            ) {
                content()
            }
        }
    }
}

@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier)
}
