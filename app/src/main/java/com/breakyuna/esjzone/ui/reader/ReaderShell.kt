package com.breakyuna.esjzone.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

/** A deliberate swipe must retain the axis on which the drag started. */
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
    horizontalSwipeEnabled: Boolean = false,
    onHorizontalSwipe: (forward: Boolean) -> Unit = {},
    deliberateHorizontalSwipe: Boolean = false,
    verticalSwipeEnabled: Boolean = false,
    onVerticalSwipe: (up: Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val currentTap by rememberUpdatedState(onReadingAreaTap)
    val currentSwipe by rememberUpdatedState(onHorizontalSwipe)
    val currentVerticalSwipe by rememberUpdatedState(onVerticalSwipe)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(horizontalSwipeEnabled, deliberateHorizontalSwipe, verticalSwipeEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var last = start
                    var childConsumed = down.isConsumed
                    var released: Offset? = null
                    var maxDistance = 0f
                    var multiTouch = false
                    var duration = 0L
                    var horizontalAxis: Boolean? = null
                    var longPress = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        multiTouch = multiTouch || event.changes.size > 1
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        childConsumed = childConsumed || change.isConsumed
                        last = change.position
                        maxDistance = maxOf(maxDistance, (last - start).getDistance())
                        duration = change.uptimeMillis - down.uptimeMillis
                        if (horizontalAxis == null) {
                            if (duration >= viewConfiguration.longPressTimeoutMillis) longPress = true
                            val movement = last - start
                            if (movement.getDistance() >= viewConfiguration.touchSlop) {
                                horizontalAxis = abs(movement.x) > abs(movement.y)
                            }
                        }
                        if (!change.pressed) released = change.position
                    } while (event.changes.any { it.pressed })

                    val delta = last - start
                    val horizontalThreshold = if (deliberateHorizontalSwipe)
                        maxOf(72.dp.toPx(), size.width * 0.2f) else maxOf(32.dp.toPx(), viewConfiguration.touchSlop * 4f)
                    val horizontalDirection = readerSwipeDirection(delta.x, delta.y, true, horizontalThreshold)
                    val verticalDirection = readerSwipeDirection(
                        delta.x, delta.y, false, maxOf(96.dp.toPx(), size.height * 0.12f)
                    )
                    val release = released
                    val swipeAllowed = release != null && !childConsumed && !multiTouch && !longPress
                    when {
                        swipeAllowed && horizontalAxis == true && horizontalSwipeEnabled && horizontalDirection != null ->
                            currentSwipe(horizontalDirection)
                        swipeAllowed && horizontalAxis == false && verticalSwipeEnabled && verticalDirection != null ->
                            currentVerticalSwipe(verticalDirection)
                        release != null && !childConsumed && !multiTouch &&
                            duration < viewConfiguration.longPressTimeoutMillis && maxDistance < viewConfiguration.touchSlop -> {
                            currentTap(
                                (release.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                (release.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            )
                        }
                    }
                }
            },
        content = content
    )
}
