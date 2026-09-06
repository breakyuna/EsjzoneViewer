package com.breakyuna.esjzone.ui.designsystem.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.designsystem.AppShapes

/** Stable glass contract. Haze is intentionally confined to this package in a later adapter. */
@Immutable
data class AppGlassSpec(
    val tint: Color? = null,
    val alpha: Float = 0.92f,
    val shape: RoundedCornerShape = AppShapes.standard,
    val borderAlpha: Float = 0.14f
)

/**
 * Glass surface seam for feature code. The current fallback is a translucent Material surface;
 * a Haze implementation may replace only this file without leaking experimental APIs.
 */
@Composable
fun AppGlassSurface(
    modifier: Modifier = Modifier,
    spec: AppGlassSpec = AppGlassSpec(),
    content: @Composable BoxScope.() -> Unit
) {
    val base = spec.tint ?: MaterialTheme.colorScheme.surface
    Surface(
        modifier = modifier,
        shape = spec.shape,
        color = base.copy(alpha = spec.alpha.coerceIn(0f, 1f)),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = spec.borderAlpha.coerceIn(0f, 1f))
        )
    ) {
        Box(content = content)
    }
}
