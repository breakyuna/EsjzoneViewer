@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package com.breakyuna.esjzone.ui.designsystem.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.glass.SurfaceProfile

/**
 * Thin navigation glass: a mostly clear center, restrained edge refraction and
 * directional reflections that still describe its thickness on a plain page.
 */
@Composable
fun AppNavigationGlassSurface(
    scene: AppGlassScene,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    selectedFraction: Float = 0.125f,
    itemCount: Int = 4,
    shape: RoundedCornerShape = RoundedCornerShape(percent = 50),
    content: @Composable BoxScope.() -> Unit
) {
    require(itemCount > 0)
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val interaction = remember { NavigationGlassInteraction() }
    val backdrop = rememberNavigationBackdrop(
        dark = dark,
        backing = colors.surface,
        vertical = vertical,
        itemCount = itemCount,
        density = LocalDensity.current.density
    )
    // Finite, selection-driven motion only. Compose respects the system duration scale.
    // Read in placement/drawing, so frames do not rebuild the Haze style/capture.
    val lightPosition = animateFloatAsState(
        targetValue = selectedFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "navigation_glass_light"
    )
    val spec = remember(colors, dark, shape) {
        AppGlassSpec(
            shape = shape,
            material = AppGlassMaterial.REGULAR,
            tint = colors.surfaceContainerLow,
            // Opaque source-fill is BEHIND captured pixels. The modest tint and depth
            // mix soften busy covers without turning the entire dock into frosted glass.
            alpha = 1f,
            tintAlpha = if (dark) 0.14f else 0.18f,
            fallbackAlpha = 0.96f,
            blurRadius = 16.dp,
            depth = 0.10f,
            refractionStrength = 0.34f,
            refractionDisplacement = 12.dp,
            refractionHeightFraction = 0.26f,
            refractionFoldStrength = 0f,
            edgeSoftness = 1.dp,
            specularIntensity = 0.42f,
            ambientResponse = 0.48f,
            specularExponent = 32f,
            fresnelExponent = 2.4f,
            surfaceProfile = SurfaceProfile.Circle,
            lightPosition = Alignment.TopStart,
            chromaticAberrationStrength = 0.02f,
            contrast = 0f,
            whitePoint = 0f,
            chromaMultiplier = 1f,
            contentNormalBlend = 0f,
            // The cached bevel below owns the directional border; no flat outline.
            borderAlpha = 0f,
            borderWidth = 0.dp,
            interactive = true,
        )
    }

    Box(
        modifier = modifier.shadow(
            elevation = 6.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.14f else 0.06f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.22f else 0.12f)
        ).clip(shape).onGloballyPositioned { interaction.origin = it.positionInRoot() }
    ) {
        // The GPU backdrop pass processes ONLY the material, never glyphs or selection.
        // A single source-backed Haze node owns all refraction and press illumination.
        AppGlassSurface(
            modifier = Modifier.matchParentSize().graphicsLayer {
                renderEffect = backdrop?.effect(size)
            },
            spec = spec,
            scene = scene,
            interactionSource = interaction.source
        ) { }
        // BoxScope member: must not participate in measuring the wrap-content side rail.
        Box(
            Modifier.matchParentSize().navigationCrystalBevel(
                dark = dark,
                vertical = vertical
            )
        )
        NavigationSelectionLens(
            position = lightPosition,
            targetFraction = selectedFraction,
            itemCount = itemCount,
            vertical = vertical,
            modifier = Modifier.matchParentSize()
        )
        CompositionLocalProvider(
            LocalNavigationGlassInteraction provides interaction,
            LocalNavigationAdaptiveBackdrop provides (backdrop != null)
        ) {
            content()
        }
    }
}

/**
 * Subtle dock-only thickness cue. Haze owns the live highlights; two inset strokes
 * keep the outline visible on plain pages without multiple bright concentric rims.
 */
internal fun Modifier.navigationCrystalBevel(
    dark: Boolean,
    vertical: Boolean,
    bandWidth: Dp = 2.dp
): Modifier = drawWithCache {
    val width = bandWidth.toPx().coerceAtMost(size.minDimension / 4f)
    val rimWidth = 0.45.dp.toPx().coerceAtMost(width)
    val rtl = layoutDirection == LayoutDirection.Rtl
    val startX = if (rtl) size.width else 0f
    val endX = if (rtl) 0f else size.width
    // Light crosses the short axis, so the two long edges stay coherent on a rail too.
    val lightStart = if (vertical) Offset(startX, 0f) else Offset.Zero
    val lightEnd = if (vertical) Offset(endX, 0f) else Offset(0f, size.height)
    val band = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.10f else 0.12f),
        0.40f to Color.Transparent,
        0.70f to Color.Black.copy(alpha = if (dark) 0.08f else 0.04f),
        1f to Color.Black.copy(alpha = if (dark) 0.14f else 0.09f),
        start = lightStart,
        end = lightEnd
    )
    val outerRim = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.22f else 0.16f),
        0.35f to Color.White.copy(alpha = 0.04f),
        0.65f to Color.Transparent,
        1f to Color.Black.copy(alpha = if (dark) 0.16f else 0.12f),
        start = Offset(startX, 0f),
        end = Offset(endX, size.height)
    )

    onDrawBehind {
        if (width <= 0f) return@onDrawBehind
        val bandInset = width / 2f + rimWidth
        drawRoundRect(
            brush = band,
            topLeft = Offset(bandInset, bandInset),
            size = Size(size.width - bandInset * 2, size.height - bandInset * 2),
            cornerRadius = CornerRadius(size.minDimension / 2f - bandInset),
            style = Stroke(width)
        )
        val outerInset = rimWidth / 2f
        val rimSize = Size(size.width - rimWidth, size.height - rimWidth)
        val rimRadius = CornerRadius(size.minDimension / 2f - outerInset)
        drawRoundRect(
            brush = outerRim,
            topLeft = Offset(outerInset, outerInset),
            size = rimSize,
            cornerRadius = rimRadius,
            style = Stroke(rimWidth)
        )
    }
}
