package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope

internal class CircularRevealOrigins {
    val bounds = mutableStateMapOf<ContainerTransformTarget, Rect>()
}

internal val LocalCircularRevealOrigins = compositionLocalOf<CircularRevealOrigins?> { null }

internal fun usesCircularReveal(from: Scene<NavKey>, to: Scene<NavKey>): Boolean {
    val source = containerTransformRoute(from)
    val destination = containerTransformRoute(to)
    return ContainerTransformTarget.forKey(destination, origin = source)?.let {
        it.origin == source && it.circularReveal
    } == true || ContainerTransformTarget.forKey(source, origin = destination)?.let {
        it.origin == destination && it.circularReveal
    } == true
}

@Composable
internal fun Modifier.circularRevealSource(target: ContainerTransformTarget): Modifier {
    val origins = LocalCircularRevealOrigins.current ?: return this
    return onGloballyPositioned { origins.bounds[target] = it.boundsInWindow() }
}

@Composable
internal fun Modifier.circularRevealDestination(target: ContainerTransformTarget): Modifier {
    val origins = LocalCircularRevealOrigins.current
    // Retain the measured entry point after the outgoing page leaves composition.
    val source = remember(target) { origins?.bounds?.get(target) } ?: return this
    val visibility = LocalNavAnimatedContentScope.current
    val progress = visibility.transition.animateFloat(
        transitionSpec = { tween(ContainerTransformDurationMillis, easing = FastOutSlowInEasing) },
        label = "${target.name}Reveal"
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    var pagePosition by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { pagePosition = it.positionInWindow() }
        .drawWithCache {
            val center = source.center - pagePosition
            // The farthest corner must be inside the circle, even in landscape or a pane.
            val fullRadius = maxOf(
                center.getDistance(),
                (Offset(size.width, 0f) - center).getDistance(),
                (Offset(0f, size.height) - center).getDistance(),
                (Offset(size.width, size.height) - center).getDistance()
            )
            val initialRadius = minOf(source.width, source.height) / 2f
            val path = Path()
            onDrawWithContent {
                val fraction = progress.value
                if (fraction >= 1f) {
                    drawContent()
                } else {
                    val radius = initialRadius + (fullRadius - initialRadius) * fraction
                    path.reset()
                    path.addOval(Rect(center = center, radius = radius))
                    clipPath(path) { this@onDrawWithContent.drawContent() }
                }
            }
        }
}
