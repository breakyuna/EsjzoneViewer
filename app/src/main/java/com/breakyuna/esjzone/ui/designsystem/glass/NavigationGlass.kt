package com.breakyuna.esjzone.ui.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Navigation-only frosted glass. Keep the reader's existing surface defaults independent.
 *
 * Haze 2.0.0-beta02: backgroundColor fills BEHIND the input; tint colors the processed
 * input. An opaque source fill prevents the original sharp pixels leaking through gaps.
 * depth = 1 removes the sharp/blur mix, while the narrow bezel still refracts the backdrop.
 * Do not replace this with Modifier.blur(), which would blur the icons and labels too.
 */
@Composable
fun AppNavigationGlassSurface(
    scene: AppGlassScene,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val shape = if (vertical) RoundedCornerShape(percent = 50) else RoundedCornerShape(34.dp)
    val spec = remember(colors, dark, shape, vertical) {
        AppGlassSpec(
            shape = shape,
            material = AppGlassMaterial.LENS,
            tint = colors.surfaceContainerLow,
            alpha = 1f,
            tintAlpha = if (dark) 0.78f else 0.72f,
            fallbackAlpha = 0.96f,
            // Fixed optics are capped at 38.5 physical px by this Haze version.
            // Raising dp indefinitely will not add more blur on high-density devices.
            blurRadius = 14.dp,
            depth = 1f,
            refractionStrength = 0.55f,
            refractionDisplacement = if (vertical) 6.dp else 8.dp,
            refractionHeightFraction = 0.18f,
            refractionFoldStrength = 0.12f,
            edgeSoftness = 1.5.dp,
            specularIntensity = if (dark) 0.38f else 0.44f,
            ambientResponse = if (dark) 0.18f else 0.26f,
            specularExponent = 24f,
            fresnelExponent = 3f,
            lightPosition = Alignment.TopStart,
            chromaticAberrationStrength = 0f,
            contrast = -0.04f,
            whitePoint = 0f,
            chromaMultiplier = 0.95f,
            contentNormalBlend = 0f,
            borderWidth = 0.75.dp,
            borderBrush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.34f else 0.70f),
                0.38f to Color.White.copy(alpha = if (dark) 0.08f else 0.22f),
                0.72f to colors.onSurface.copy(alpha = 0.06f),
                1f to Color.Black.copy(alpha = if (dark) 0.24f else 0.12f)
            )
        )
    }
    val sheen = remember(dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.06f else 0.10f),
            0.40f to Color.Transparent,
            1f to Color.Black.copy(alpha = if (dark) 0.025f else 0.015f)
        )
    }

    AppGlassSurface(
        modifier = modifier.shadow(
            elevation = if (dark) 10.dp else 14.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.14f else 0.06f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.24f else 0.12f)
        ),
        spec = spec,
        scene = scene
    ) {
        // Relative to the measured material, never a fixed-height white slab.
        Box(Modifier.matchParentSize().background(sheen))
        content()
    }
}
