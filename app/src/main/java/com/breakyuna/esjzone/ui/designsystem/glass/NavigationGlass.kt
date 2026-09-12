package com.breakyuna.esjzone.ui.designsystem.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * A clear lens plus an independently lit, rounded glass bevel. Refraction cannot create
 * detail from a uniform source: reflected light/dark surroundings supply thickness there.
 * All decoration is behind the sharp, accessible navigation content.
 * No blur, noise, or broad translucent surface wash belongs in this material.
 */
@Composable
fun AppNavigationGlassSurface(
    scene: AppGlassScene,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    selectedFraction: Float = 0.125f,
    itemCount: Int = 4,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val shape = remember { RoundedCornerShape(percent = 50) }
    // Finite, selection-driven motion only. Compose respects the system duration scale.
    // Read in the draw phase, so each frame does not rebuild the Haze style/capture.
    val lightPosition = animateFloatAsState(
        targetValue = selectedFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "navigation_glass_light"
    )
    val spec = remember(colors, dark, shape, vertical) {
        AppGlassSpec(
            shape = shape,
            material = AppGlassMaterial.CLEAR,
            tint = colors.surfaceContainerLow,
            // This fills BEHIND the source, not on top of the transparent pane.
            alpha = 1f,
            tintAlpha = if (dark) 0.05f else 0.035f,
            fallbackAlpha = 0.96f,
            // BOTH must be zero: depth > 0 asks Haze for an extra blurred sample.
            blurRadius = 0.dp,
            depth = 0f,
            refractionStrength = 0.90f,
            refractionDisplacement = if (vertical) 14.dp else 18.dp,
            refractionHeightFraction = 0.18f,
            refractionFoldStrength = 0.38f,
            edgeSoftness = 0.5.dp,
            specularIntensity = if (dark) 0.60f else 0.64f,
            ambientResponse = if (dark) 0.16f else 0.12f,
            specularExponent = 40f,
            fresnelExponent = 5f,
            lightPosition = Alignment.TopStart,
            chromaticAberrationStrength = 0.035f,
            contrast = 0f,
            whitePoint = 0f,
            chromaMultiplier = 1f,
            contentNormalBlend = 0f,
            // The shaped bevel below owns the rim; no flat outline over it.
            borderAlpha = 0f,
            borderWidth = 0.dp,
        )
    }

    AppGlassSurface(
        modifier = modifier.shadow(
            elevation = if (dark) 12.dp else 14.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.16f else 0.10f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.36f else 0.24f)
        ),
        spec = spec,
        scene = scene
    ) {
        // BoxScope member: must not participate in measuring the wrap-content side rail.
        Box(Modifier.matchParentSize().navigationCrystalBevel(dark, vertical, lightPosition))
        NavigationSelectionLens(
            scene = scene,
            position = lightPosition,
            targetFraction = selectedFraction,
            itemCount = itemCount,
            vertical = vertical,
            modifier = Modifier.matchParentSize()
        )
        content()
    }
}

/** Cached geometry/brushes work on older renderers too, even without shader refraction. */
internal fun Modifier.navigationCrystalBevel(
    dark: Boolean,
    vertical: Boolean,
    lightPosition: State<Float>,
    bevelWidth: Dp = 5.5.dp
): Modifier = drawWithCache {
    val thickness = bevelWidth.toPx().coerceAtMost(size.minDimension / 4f)
    val hairline = 0.65.dp.toPx()
    val rtl = layoutDirection == LayoutDirection.Rtl
    val lightStart = Offset(if (rtl) size.width else 0f, 0f)
    val lightEnd = Offset(if (rtl) 0f else size.width, size.height)
    // Light across the SHORT axis keeps long top/bottom edges coherent. A diagonal-only
    // gradient on a wide capsule would incorrectly turn half of the upper rim dark.
    val crossStart = if (vertical) lightStart else Offset.Zero
    val crossEnd = if (vertical) Offset(lightEnd.x, 0f) else Offset(0f, size.height)

    // Alternating bright/dark reflection across the curved edge, fading into clear glass.
    // Darkness is essential on white backgrounds; white reflection is essential on black.
    val bevelBrush = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.46f else 0.70f),
        0.22f to Color.White.copy(alpha = if (dark) 0.24f else 0.40f),
        0.46f to Color.Black.copy(alpha = if (dark) 0.22f else 0.20f),
        0.72f to Color.Black.copy(alpha = if (dark) 0.38f else 0.28f),
        1f to Color.White.copy(alpha = if (dark) 0.42f else 0.62f),
        start = crossStart,
        end = crossEnd
    )
    val outerReflection = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.94f),
        0.32f to Color.White.copy(alpha = if (dark) 0.52f else 0.80f),
        0.55f to Color.Black.copy(alpha = if (dark) 0.32f else 0.24f),
        0.78f to Color.White.copy(alpha = 0.18f),
        1f to Color.White.copy(alpha = 0.82f),
        start = lightStart,
        end = lightEnd
    )
    val innerReflection = Brush.linearGradient(
        0f to Color.Black.copy(alpha = if (dark) 0.30f else 0.19f),
        0.38f to Color.Transparent,
        0.68f to Color.White.copy(alpha = if (dark) 0.10f else 0.18f),
        1f to Color.White.copy(alpha = if (dark) 0.64f else 0.84f),
        start = crossStart,
        end = crossEnd
    )
    val bands = List(10) { index ->
        val fraction = (index + 0.5f) / 10f
        // A curved intensity profile avoids a flat, solid metallic frame.
        val strength = sin(fraction * Math.PI).toFloat() * (1f - fraction * 0.65f)
        (hairline + thickness * fraction) to strength
    }
    // A reflected strip light travels along the bevel when the tab changes.
    // Scaling the radial gradient too prevents a hard-edged white oval.
    val glintRadius = (size.minDimension * 0.90f).coerceAtLeast(1f)
    val glintBrush = Brush.radialGradient(
        0f to Color.White.copy(alpha = if (dark) 0.50f else 0.65f),
        0.35f to Color.White.copy(alpha = if (dark) 0.24f else 0.30f),
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = glintRadius
    )
    val glintCompression = thickness * 1.5f / glintRadius

    onDrawBehind {
        if (size.minDimension <= hairline * 4f) return@onDrawBehind
        for ((inset, strength) in bands) {
            drawInsetRim(bevelBrush, inset, thickness / 10f + 0.35f, strength)
        }
        drawInsetRim(outerReflection, hairline, hairline)
        drawInsetRim(innerReflection, thickness + hairline, hairline)

        val progress = lightPosition.value.coerceIn(0f, 1f)
        val glintCenter = if (vertical) {
            Offset(if (rtl) size.width else 0f, size.height * progress)
        } else {
            Offset(size.width * (if (rtl) 1f - progress else progress), 0f)
        }
        translate(left = glintCenter.x, top = glintCenter.y) {
            scale(
                scaleX = if (vertical) glintCompression else 1f,
                scaleY = if (vertical) 1f else glintCompression,
                pivot = Offset.Zero
            ) {
                drawCircle(brush = glintBrush, radius = glintRadius, center = Offset.Zero)
            }
        }
    }
}

private fun DrawScope.drawInsetRim(
    brush: Brush,
    inset: Float,
    width: Float,
    alpha: Float = 1f
) {
    val rimSize = Size(size.width - inset * 2f, size.height - inset * 2f)
    if (rimSize.minDimension <= 0f) return
    val radius = rimSize.minDimension / 2f
    drawRoundRect(
        brush = brush,
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = CornerRadius(radius, radius),
        alpha = alpha,
        style = Stroke(width)
    )
}
