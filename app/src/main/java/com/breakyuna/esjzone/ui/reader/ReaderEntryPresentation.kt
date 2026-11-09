package com.breakyuna.esjzone.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource

/** Keep measuring the hidden body so exact text-line restoration can finish. */
@Composable
internal fun ReaderEntryPresentation(
    contentVisible: Boolean,
    background: Color,
    backgroundPainter: Painter?,
    transparency: Float,
    loadingDescription: String,
    onRetryPosition: (() -> Unit)? = null,
    cropX: Float = 0.5f,
    cropY: Float = 0.5f,
    content: @Composable () -> Unit
) {
    // Freeze the first frame's backdrop too: asynchronous image loading must
    // not replace the loading canvas halfway through the navigation animation.
    val initialBackground = remember { background }
    val initialPainter = remember { backgroundPainter }
    val initialAppearance = remember {
        ReaderImageBackground(transparency = transparency, cropX = cropX, cropY = cropY)
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()
            .graphicsLayer { alpha = if (contentVisible) 1f else 0f }
            .then(if (contentVisible) Modifier else Modifier.clearAndSetSemantics { })
        ) { content() }
        if (!contentVisible) {
            Box(Modifier.fillMaxSize()
                .readerBackdrop(initialBackground, initialPainter, initialAppearance)
                .testTag("reader-entry-loading"), contentAlignment = Alignment.Center
            ) {
                // A sibling blocker lets the foreground retry button receive its
                // own pointer events while the hidden reader remains inaccessible.
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                })
                if (onRetryPosition != null) {
                    Surface {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.reader_restore_position_failed))
                            TextButton(onClick = onRetryPosition, modifier = Modifier.testTag("reader-position-retry")) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                } else {
                    CircularProgressIndicator(
                        Modifier.semantics { contentDescription = loadingDescription },
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }
    }
}
