package com.breakyuna.esjzone.ui.reader

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal enum class ReaderSidePanel(val openingSign: Float) {
    CONTENTS(1f), COMMENTS(-1f)
}

internal enum class ReaderDragAxis { HORIZONTAL, VERTICAL }

/** Decide once after touch slop; ambiguous diagonals belong to vertical reading. */
internal fun readerDragAxis(dx: Float, dy: Float, touchSlop: Float): ReaderDragAxis? = when {
    maxOf(abs(dx), abs(dy)) <= touchSlop -> null
    abs(dx) > abs(dy) * 2f -> ReaderDragAxis.HORIZONTAL
    else -> ReaderDragAxis.VERTICAL
}

/** A fling needs meaningful travel too, so a tiny fast twitch cannot open a panel. */
internal fun readerSidePanelShouldOpen(
    fraction: Float,
    wasOpen: Boolean,
    travelDp: Float,
    openingVelocityDp: Float
): Boolean = if (fraction <= 0f) {
    false
} else if (abs(travelDp) >= 32f && abs(openingVelocityDp) >= 900f) {
    openingVelocityDp > 0f
} else {
    fraction >= if (wasOpen) 0.6f else 0.4f
}

@Stable
internal class ReaderSidePanelsState(private val scope: CoroutineScope) {
    var panel by mutableStateOf<ReaderSidePanel?>(null)
        private set
    var fraction by mutableFloatStateOf(0f)
        private set
    var isOpen by mutableStateOf(false)
        private set
    var contentsWidth by mutableFloatStateOf(1f)
    var commentsWidth by mutableFloatStateOf(1f)
    private var animation: Job? = null

    val isVisible: Boolean get() = panel != null
    fun width(panel: ReaderSidePanel): Float =
        if (panel == ReaderSidePanel.CONTENTS) contentsWidth else commentsWidth

    fun beginDrag(panel: ReaderSidePanel) {
        animation?.cancel()
        if (this.panel != panel) fraction = 0f
        this.panel = panel
    }

    fun dragTo(fraction: Float) { this.fraction = fraction.coerceIn(0f, 1f) }

    fun open(panel: ReaderSidePanel) {
        beginDrag(panel)
        settle(true)
    }

    fun close() { if (isVisible) settle(false) }

    fun settle(open: Boolean) {
        animation?.cancel()
        isOpen = open
        animation = scope.launch {
            animate(
                initialValue = fraction,
                targetValue = if (open) 1f else 0f,
                animationSpec = spring(dampingRatio = 1f, stiffness = 450f)
            ) { value, _ -> fraction = value }
            if (!open) panel = null
        }
    }
}

@Composable
internal fun rememberReaderSidePanelsState(): ReaderSidePanelsState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ReaderSidePanelsState(scope) }
}

/** Inline panels keep the original pointer stream alive while their content is revealed. */
@Composable
internal fun ReaderSidePanels(
    state: ReaderSidePanelsState,
    gesturesEnabled: Boolean,
    commentsEnabled: Boolean,
    onOpening: (ReaderSidePanel) -> Unit,
    contents: @Composable () -> Unit,
    comments: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val currentOnOpening by rememberUpdatedState(onOpening)
    Box(
        Modifier.fillMaxSize().clipToBounds()
            .onSizeChanged { size ->
                state.contentsWidth = minOf(size.width * 0.82f, with(density) { 340.dp.toPx() }).coerceAtLeast(1f)
                state.commentsWidth = minOf(size.width * 0.92f, with(density) { 480.dp.toPx() }).coerceAtLeast(1f)
            }
            .pointerInput(state, gesturesEnabled, commentsEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val start = down.position
                    val wasOpen = state.isOpen
                    val startFraction = state.fraction
                    val existingPanel = state.panel
                    var axis: ReaderDragAxis? = null
                    var draggingPanel: ReaderSidePanel? = null
                    var rejected = down.isConsumed || (!gesturesEnabled && existingPanel == null)
                    var released = false
                    var dx = 0f
                    val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, start) }
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.size > 1 || change.isConsumed) rejected = true
                            dx = change.position.x - start.x
                            val dy = change.position.y - start.y
                            velocity.addPosition(change.uptimeMillis, change.position)
                            if (!rejected && axis == null) {
                                if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                                    rejected = true
                                } else {
                                    axis = readerDragAxis(dx, dy, viewConfiguration.touchSlop)
                                    if (axis == ReaderDragAxis.HORIZONTAL && change.pressed) {
                                        val target = existingPanel ?: if (dx > 0f) ReaderSidePanel.CONTENTS else ReaderSidePanel.COMMENTS
                                        if (target == ReaderSidePanel.COMMENTS && !commentsEnabled && existingPanel == null) {
                                            rejected = true
                                        } else {
                                            draggingPanel = target
                                            if (existingPanel == null) currentOnOpening(target)
                                            state.beginDrag(target)
                                        }
                                    }
                                }
                            }
                            val panel = draggingPanel
                            if (panel != null && !rejected) {
                                change.consume()
                                val movement = dx - sign(dx) * minOf(abs(dx), viewConfiguration.touchSlop)
                                state.dragTo(startFraction + movement * panel.openingSign / state.width(panel))
                            }
                            if (!change.pressed) released = true
                            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                            if (draggingPanel == null && finalEvent.changes.firstOrNull { it.id == down.id }?.isConsumed == true) {
                                rejected = true
                            }
                        } while (event.changes.any { it.pressed })
                        draggingPanel?.let { panel ->
                            val open = if (released && !rejected) readerSidePanelShouldOpen(
                                fraction = state.fraction,
                                wasOpen = wasOpen,
                                travelDp = dx / density.density,
                                openingVelocityDp = velocity.calculateVelocity().x * panel.openingSign / density.density
                            ) else wasOpen
                            state.settle(open)
                            draggingPanel = null
                        }
                    } finally {
                        // Cancellation, mode changes and multi-touch return to the last settled side.
                        if (draggingPanel != null) state.settle(wasOpen)
                    }
                }
            }
    ) {
        Box(Modifier.fillMaxSize().then(if (state.isVisible) Modifier.clearAndSetSemantics { } else Modifier), content = content)
        state.panel?.let { panel ->
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * state.fraction))
                    .clickable(onClick = state::close)
            )
            Surface(
                modifier = Modifier
                    .align(if (panel == ReaderSidePanel.CONTENTS) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(with(density) { state.width(panel).toDp() })
                    .offset { IntOffset((-panel.openingSign * state.width(panel) * (1f - state.fraction)).roundToInt(), 0) }
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = if (panel == ReaderSidePanel.CONTENTS) RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                    else RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                if (panel == ReaderSidePanel.CONTENTS) contents() else comments()
            }
        }
    }
}
