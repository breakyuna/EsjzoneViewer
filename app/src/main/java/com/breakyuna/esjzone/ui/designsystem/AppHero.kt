package com.breakyuna.esjzone.ui.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.novellibrary.novel.CoverState

private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Immersive hero background: a tinted base, the blurred cover and a dark scrim so
 * foreground text can always use white. Devices without RenderEffect blur get a
 * heavier scrim instead.
 */
@Composable
fun AppCoverBackdrop(
    coverUrl: String,
    modifier: Modifier = Modifier,
    coverState: CoverState? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scrim = appSemanticColors().heroScrim
    Box(
        modifier = modifier.clipToBounds().background(
            Brush.linearGradient(listOf(colors.primary, colors.tertiary))
        )
    ) {
        if (coverUrl.isNotBlank()) {
            AppCoverImage(
                model = coverUrl,
                contentDescription = null,
                sourceState = coverState,
                contentScale = ContentScale.Crop,
                loading = {},
                error = {},
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }
                    .then(if (BlurSupported) Modifier.blur(28.dp) else Modifier)
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        if (BlurSupported) listOf(scrim.copy(alpha = 0.18f), scrim.copy(alpha = 0.58f))
                        else listOf(scrim.copy(alpha = 0.5f), scrim.copy(alpha = 0.72f))
                    )
                )
        )
        content()
    }
}
