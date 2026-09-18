package com.breakyuna.esjzone.ui.designsystem.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.designsystem.rememberSystemReducedMotion
import kotlin.math.abs
import kotlin.math.roundToInt

/** Shared with the actual tab layout so the lens cannot drift when its geometry changes. */
internal object NavigationGlassMetrics {
    val horizontalPadding = 4.dp
    val bottomHeight = 60.dp
    val bottomCornerRadius = 30.dp
    val bottomVerticalPadding = 4.dp
    val bottomItemHeight = 52.dp
    val bottomMaxWidth = 392.dp
    val selectionHorizontalInset = 2.dp
    val railWidth = 58.dp
    val railItemSize = 48.dp
    val railVerticalPadding = 8.dp
    val railItemGap = 8.dp
}

/**
 * One persistent thin selection overlay inside the dock material.
 * No independent backdrop, blur, bevel or shadow; glyphs and hit targets are siblings.
 */
@Composable
internal fun NavigationSelectionLens(
    position: State<Float>,
    targetFraction: Float,
    itemCount: Int,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    require(itemCount > 0)
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val reducedMotion = rememberSystemReducedMotion()
    // A slower follower stretches the moving lens, then settles back to its resting shape.
    // Both springs retain their current values on rapid retargeting and honor duration scale 0.
    val tail = animateFloatAsState(
        targetValue = targetFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 90f),
        label = "navigation_lens_tail"
    )
    Layout(
        modifier = modifier,
        content = {
            Box(
                Modifier.drawWithCache {
                    val sheen = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.03f), Color.Transparent)
                    )
                    onDrawBehind {
                        // Continuous spring energy; never switch optics between two states.
                        val energy = if (reducedMotion) 0f else
                            (abs(position.value - tail.value) * itemCount * 2f).coerceIn(0f, 1f)
                        val radius = CornerRadius(size.minDimension / 2f)
                        drawRoundRect(
                            color = colors.onSurface.copy(
                                alpha = (if (dark) 0.08f else 0.07f) + energy * 0.025f
                            ),
                            cornerRadius = radius
                        )
                        drawRoundRect(brush = sheen, cornerRadius = radius, alpha = energy)
                    }
                }
            )
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
            (slotWidth - NavigationGlassMetrics.selectionHorizontalInset.toPx() * 2)
                .roundToInt().coerceIn(0, width)
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
            val stretch = if (reducedMotion) 0f else
                (abs(head - tail.value) * itemCount * 0.12f).coerceIn(0f, 0.08f)
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
                // Only the decorative selection moves; tab content and hit targets remain fixed.
                scaleX = if (vertical) across else along
                scaleY = if (vertical) along else across
            }
        }
    }
}
