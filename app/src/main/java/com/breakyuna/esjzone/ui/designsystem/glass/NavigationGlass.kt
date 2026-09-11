package com.breakyuna.esjzone.ui.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
 * Navigation-only crystal glass. Keep the reader's existing surface defaults independent.
 *
 * Haze 2.0.0-beta02: backgroundColor fills BEHIND the input; tint colors the processed
 * input. An opaque source fill prevents the original sharp pixels leaking through gaps.
 * Low tint and shallow depth retain the backdrop; the bezel supplies the optical thickness.
 * Readability support belongs near each item's content, not across the entire clear pane.
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
            material = AppGlassMaterial.CLEAR,
            tint = colors.surfaceContainerLow,
            alpha = 1f,
            tintAlpha = if (dark) 0.16f else 0.10f,
            fallbackAlpha = 0.96f,
            // A small blurred contribution takes the edge off detail without frosting it.
            blurRadius = 4.dp,
            depth = 0.24f,
            refractionStrength = 0.80f,
            refractionDisplacement = if (vertical) 10.dp else 12.dp,
            refractionHeightFraction = 0.22f,
            refractionFoldStrength = 0.24f,
            edgeSoftness = 1.dp,
            specularIntensity = if (dark) 0.64f else 0.70f,
            ambientResponse = if (dark) 0.24f else 0.30f,
            specularExponent = 32f,
            fresnelExponent = 4f,
            lightPosition = Alignment.TopStart,
            chromaticAberrationStrength = 0.025f,
            contrast = 0f,
            whitePoint = 0f,
            chromaMultiplier = 1f,
            contentNormalBlend = 0f,
            borderWidth = 0.75.dp,
            borderBrush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.64f else 0.86f),
                0.28f to Color.White.copy(alpha = if (dark) 0.16f else 0.24f),
                0.58f to Color.White.copy(alpha = 0.04f),
                0.84f to Color.Black.copy(alpha = if (dark) 0.20f else 0.12f),
                1f to Color.White.copy(alpha = if (dark) 0.28f else 0.48f)
            )
        )
    }
    val sheen = remember(dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.10f else 0.14f),
            0.18f to Color.White.copy(alpha = 0.02f),
            0.36f to Color.Transparent,
            0.80f to Color.Transparent,
            1f to Color.White.copy(alpha = if (dark) 0.02f else 0.04f)
        )
    }

    AppGlassSurface(
        modifier = modifier.shadow(
            elevation = if (dark) 8.dp else 10.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.10f else 0.04f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.20f else 0.10f)
        ),
        spec = spec,
        scene = scene
    ) {
        // BoxScope member: no package-level import. It must NOT size the wrap-content rail.
        Box(Modifier.matchParentSize().background(sheen))
        content()
    }
}
