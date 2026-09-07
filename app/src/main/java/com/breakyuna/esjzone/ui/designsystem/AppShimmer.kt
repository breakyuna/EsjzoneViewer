package com.breakyuna.esjzone.ui.designsystem

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.shape.Shape
import com.valentinilk.shimmer.shimmer

/**
 * Returns whether the platform has disabled animator/transition motion.
 *
 * This is intentionally a small, static snapshot for loading placeholders:
 * when Android's "remove animations" setting is active, placeholders remain a
 * static surface instead of starting an independent shimmer animation.
 */
@Composable
fun appReducedMotion(): Boolean {
    val view = LocalView.current
    return remember(view) {
        if (view.isInEditMode) {
            true
        } else {
            val resolver = view.context.contentResolver
            val animatorScale = Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            val transitionScale = Settings.Global.getFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            )
            val windowScale = Settings.Global.getFloat(
                resolver,
                Settings.Global.WINDOW_ANIMATION_SCALE,
                1f
            )
            animatorScale == 0f || transitionScale == 0f || windowScale == 0f
        }
    }
}

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
    contentDescription: String? = null
) {
    val reducedMotion = appReducedMotion()
    val description = contentDescription
    val placeholderModifier = modifier
        .clip(shape)
        .then(if (reducedMotion) Modifier else Modifier.shimmer())
        .background(color)
        .then(
            description?.let { value ->
                Modifier.semantics { contentDescription = value }
            } ?: Modifier
        )
    Box(modifier = placeholderModifier)
}
