package com.breakyuna.esjzone.ui.designsystem.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Shared with the actual tab layout so the lens cannot drift when its geometry changes. */
internal object NavigationGlassMetrics {
    val horizontalPadding = 4.dp
    val bottomHeight = 68.dp
    val bottomVerticalPadding = 6.dp
    val bottomItemHeight = 56.dp
    val bottomItemMaxWidth = 72.dp
    val railWidth = 58.dp
    val railItemSize = 48.dp
    val railVerticalPadding = 8.dp
    val railItemGap = 8.dp
}

/**
 * One decorative lens, underneath ALL tab glyphs and hit targets. It samples the page scene,
 * never the outer glass or navigation content, so it cannot refract icons or capture itself.
 * The matched-size Layout does not contribute to the wrap-content rail's measured size.
 */
@Composable
internal fun NavigationSelectionLens(
    scene: AppGlassScene,
    position: State<Float>,
    targetFraction: Float,
    itemCount: Int,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    require(itemCount > 0)
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val shape = remember { RoundedCornerShape(percent = 50) }
    // A slower follower stretches the moving lens, then settles back to its resting shape.
    // Both springs retain their current values on rapid retargeting and honor duration scale 0.
    val tail = animateFloatAsState(
        targetValue = targetFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 90f),
        label = "navigation_lens_tail"
    )
    val spec = remember(colors, dark, shape) {
        AppGlassSpec(
            shape = shape,
            material = AppGlassMaterial.CLEAR,
            tint = colors.primaryContainer,
            alpha = 1f,
            tintAlpha = if (dark) 0.045f else 0.06f,
            fallbackAlpha = 0.10f,
            blurRadius = 0.dp,
            depth = 0f,
            refractionStrength = 0.94f,
            refractionDisplacement = 10.dp,
            refractionHeightFraction = 0.23f,
            refractionFoldStrength = 0.24f,
            edgeSoftness = 0.5.dp,
            specularIntensity = if (dark) 0.72f else 0.74f,
            ambientResponse = if (dark) 0.20f else 0.16f,
            specularExponent = 28f,
            fresnelExponent = 3.5f,
            lightPosition = Alignment.TopStart,
            chromaticAberrationStrength = 0.025f,
            contrast = 0f,
            whitePoint = 0f,
            chromaMultiplier = 1f,
            contentNormalBlend = 0f,
            borderAlpha = 0f,
            borderWidth = 0.dp,
        )
    }

    Layout(
        modifier = modifier,
        content = {
            AppGlassSurface(
                scene = scene,
                spec = spec,
                modifier = Modifier.shadow(
                    elevation = 5.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = if (dark) 0.32f else 0.18f)
                )
            ) {
                Box(
                    Modifier.matchParentSize().navigationCrystalBevel(
                        dark = dark,
                        vertical = vertical,
                        lightPosition = position,
                        bevelWidth = 2.75.dp
                    )
                )
            }
        }
    ) { measurables, constraints ->
        // This overlay is always installed with BoxScope.matchParentSize().
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val horizontalPadding = NavigationGlassMetrics.horizontalPadding.roundToPx()
        val slotWidth = ((width - horizontalPadding * 2).coerceAtLeast(0)).toFloat() / itemCount
        val lensWidth = if (vertical) {
            NavigationGlassMetrics.railItemSize.roundToPx().coerceAtMost(width)
        } else {
            slotWidth.roundToInt().coerceAtMost(NavigationGlassMetrics.bottomItemMaxWidth.roundToPx())
        }
        val lensHeight = if (vertical) {
            NavigationGlassMetrics.railItemSize.roundToPx().coerceAtMost(height)
        } else {
            val availableHeight = (height - NavigationGlassMetrics.bottomVerticalPadding.roundToPx() * 2)
                .coerceAtLeast(0)
            NavigationGlassMetrics.bottomItemHeight.roundToPx().coerceAtMost(availableHeight)
        }
        val lens = measurables.single().measure(Constraints.fixed(lensWidth, lensHeight))
        // Column rounds the item and the gap independently, so do the same here.
        val railStep = NavigationGlassMetrics.railItemSize.roundToPx() +
            NavigationGlassMetrics.railItemGap.roundToPx()
        val railPadding = NavigationGlassMetrics.railVerticalPadding.roundToPx()

        layout(width, height) {
            // Read animated state only in placement: no frame-by-frame recomposition/remeasure.
            val head = position.value
            val index = (head * itemCount - 0.5f).coerceIn(0f, (itemCount - 1).toFloat())
            val stretch = (abs(head - tail.value) * itemCount * 0.16f).coerceIn(0f, 0.12f)
            val along = 1f + stretch
            val across = 1f / along
            val centerX = if (vertical) width / 2f else horizontalPadding + (index + 0.5f) * slotWidth
            val centerY = if (vertical) railPadding + lensHeight / 2f + index * railStep else height / 2f
            // Keep the stretched material inside the capsule even at either end of a jump.
            val halfWidth = lensWidth * (if (vertical) across else along) / 2f
            val halfHeight = lensHeight * (if (vertical) along else across) / 2f
            val safeHalfWidth = halfWidth.coerceAtMost(width / 2f)
            val safeHalfHeight = halfHeight.coerceAtMost(height / 2f)
            val x = centerX.coerceIn(safeHalfWidth, width - safeHalfWidth) - lensWidth / 2f
            val y = centerY.coerceIn(safeHalfHeight, height - safeHalfHeight) - lensHeight / 2f
            lens.placeRelativeWithLayer(x.roundToInt(), y.roundToInt()) {
                // Transform this empty material only; tab content lives in the sibling Row/Column.
                scaleX = if (vertical) across else along
                scaleY = if (vertical) along else across
            }
        }
    }
}
