package com.breakyuna.esjzone.ui.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Returns the page direction for a tap in the reader canvas; null keeps the toolbar action. */
fun readerTapPageDirection(xFraction: Float, enabled: Boolean, leftTapForward: Boolean): Boolean? {
    if (!enabled) return null
    return when {
        xFraction < 0.28f -> leftTapForward
        xFraction > 0.72f -> !leftTapForward
        else -> null
    }
}

/** Require deliberate movement on the selected axis before claiming a swipe. */
internal fun readerSwipeDirection(
    deltaX: Float, deltaY: Float, horizontal: Boolean, threshold: Float
): Boolean? {
    val primary = if (horizontal) deltaX else deltaY
    val secondary = if (horizontal) deltaY else deltaX
    return if (abs(primary) >= threshold && abs(primary) > abs(secondary) * 1.5f)
        primary < 0f else null
}

/**
 * Root presentation boundary for immersive reading. It deliberately owns only
 * the reader canvas and tap surface; Navigation 3 owns the route and back
 * stack outside this shell.
 */
@Composable
fun ReaderShell(
    background: Color,
    onReadingAreaTap: (xFraction: Float, yFraction: Float) -> Unit,
    pagedGesturesEnabled: Boolean = false,
    verticalContentScrollable: Boolean = false,
    bookmarkPullEnabled: Boolean = false,
    onBookmarkPull: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val currentTap by rememberUpdatedState(onReadingAreaTap)
    val currentBookmarkPull by rememberUpdatedState(onBookmarkPull)
    var pullingBookmark by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableFloatStateOf(0f) }
    val pullTranslation by animateFloatAsState(
        targetValue = pullOffset,
        animationSpec = if (pullingBookmark) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "readerBookmarkPull"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(pagedGesturesEnabled, verticalContentScrollable, bookmarkPullEnabled) {
                awaitEachGesture {
                    try {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val verticalThreshold = maxOf(96.dp.toPx(), size.height * 0.12f)
                        val start = down.position
                        var last = start
                        var childConsumed = down.isConsumed
                        var released: Offset? = null
                        var maxDistance = 0f
                        var multiTouch = false
                        var duration = 0L
                        var axis: ReaderDragAxis? = null
                        var ownsSwipe = false
                        var longPress = false
                        do {
                            // Lock direction before Pager can claim incidental horizontal drift.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            multiTouch = multiTouch || event.changes.size > 1
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            childConsumed = childConsumed || change.isConsumed
                            last = change.position
                            maxDistance = maxOf(maxDistance, (last - start).getDistance())
                            duration = change.uptimeMillis - down.uptimeMillis
                            if (!ownsSwipe && axis == null) {
                                if (duration >= viewConfiguration.longPressTimeoutMillis) longPress = true
                                val movement = last - start
                                if (!childConsumed && !multiTouch && !longPress && change.pressed) {
                                    if (pagedGesturesEnabled) {
                                        axis = readerDragAxis(movement.x, movement.y, viewConfiguration.touchSlop)
                                        // Horizontal drags belong to Pager. Oversized pages keep their
                                        // nested vertical scroll; ordinary pages reserve it for bookmarks.
                                        ownsSwipe = axis == ReaderDragAxis.VERTICAL && !verticalContentScrollable
                                    } else if (bookmarkPullEnabled && readerSwipeDirection(
                                            movement.x, movement.y, false, verticalThreshold) == false) {
                                        axis = ReaderDragAxis.VERTICAL
                                        ownsSwipe = true
                                    }
                                }
                            }
                            if (ownsSwipe && !childConsumed && !multiTouch) change.consume()
                            if (ownsSwipe && bookmarkPullEnabled && !childConsumed && !multiTouch) {
                                pullingBookmark = true
                                val distance = (last.y - start.y - viewConfiguration.touchSlop).coerceAtLeast(0f)
                                val limit = 80.dp.toPx()
                                pullOffset = limit * distance / (limit + distance)
                            }
                            if (!change.pressed) released = change.position
                            // Only count consumption by children, not our own claimed swipe.
                            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                            if (!ownsSwipe) {
                                childConsumed = childConsumed ||
                                    finalEvent.changes.firstOrNull { it.id == down.id }?.isConsumed == true
                            }
                        } while (event.changes.any { it.pressed })

                        val delta = last - start
                        val verticalDirection = readerSwipeDirection(
                            delta.x, delta.y, false, verticalThreshold
                        )
                        val release = released
                        val swipeAllowed = ownsSwipe && release != null && !childConsumed && !multiTouch && !longPress
                        when {
                            swipeAllowed && axis == ReaderDragAxis.VERTICAL && bookmarkPullEnabled && verticalDirection == false ->
                                currentBookmarkPull()
                            release != null && !childConsumed && !multiTouch &&
                                duration < viewConfiguration.longPressTimeoutMillis && maxDistance < viewConfiguration.touchSlop -> {
                                currentTap(
                                    (release.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                    (release.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                )
                            }
                        }
                    } finally {
                        pullingBookmark = false
                        pullOffset = 0f
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = pullTranslation },
            content = content
        )
    }
}
