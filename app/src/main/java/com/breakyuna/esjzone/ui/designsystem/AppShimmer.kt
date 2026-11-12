package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.valentinilk.shimmer.shimmer

/** Reactive reduced-motion signal provided by AppTheme. */
@Composable
fun appReducedMotion(): Boolean = LocalReduceMotion.current

/**
 * Shared loading placeholder used by feature loading states.
 *
 * The dependency is isolated here so feature pages never configure their own
 * shimmer theme or bypass the app's reduced-motion behavior.
 */
@Composable
fun AppShimmerPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.standard,
    color: Color = appStateColors().containerRaised,
    contentDescription: String? = null,
    animated: Boolean = true
) {
    val reducedMotion = !animated || !rememberMotionActive()
    val description = contentDescription
    val placeholderModifier = modifier
        .clip(shape)
        .then(if (reducedMotion) Modifier else Modifier.shimmer())
        .background(color)
        .then(
            description?.let { value ->
                Modifier.semantics { this.contentDescription = value }
            } ?: Modifier
        )
    Box(modifier = placeholderModifier)
}
