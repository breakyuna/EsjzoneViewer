package com.breakyuna.esjzone.ui.designsystem.glass

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Visual policy shared by the app's backdrop surfaces. */
@Immutable
data class AppGlassSpec(
    val tint: Color? = null,
    /** Backdrop surface opacity. Navigation supplies a lower value for a more transparent island. */
    val alpha: Float = 0.92f,
    val shape: RoundedCornerShape = AppShapes.standard,
    val borderAlpha: Float = 0.14f,
    val tintAlpha: Float = 0.16f,
    val edgeSoftness: Dp = 2.dp,
    val specularIntensity: Float = 0.4f,
    val material: AppGlassMaterial = AppGlassMaterial.REGULAR
)

enum class AppGlassMaterial {
    REGULAR,
    CLEAR
}

/** A dedicated capture scene for an overlay that must sample only its backdrop siblings. */
@Immutable
class AppGlassScene internal constructor(internal val hazeState: HazeState)

private val LocalAppHazeState = compositionLocalOf<HazeState?> { null }

@Composable
fun rememberAppGlassScene(): AppGlassScene {
    val hazeState = rememberHazeState()
    return remember(hazeState) { AppGlassScene(hazeState) }
}

fun Modifier.appGlassSource(scene: AppGlassScene): Modifier = hazeSource(scene.hazeState)

/** Installs one source-backed Haze 2 scene for the application shell. */
@Composable
fun AppGlassHost(content: @Composable () -> Unit) {
    val hazeState = rememberHazeState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalAppHazeState provides hazeState,
            content = content
        )
    }
}

/**
 * Draws a source-backed glass surface with a Material fallback when no host is installed.
 * Haze 2's reduced-motion policy disables interaction transforms when Android's animator scale is
 * zero while retaining the backdrop effect itself. When no host is present, the same geometry and
 * alpha are rendered through a Material surface fallback.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun AppGlassSurface(
    modifier: Modifier = Modifier,
    spec: AppGlassSpec = AppGlassSpec(),
    scene: AppGlassScene? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState = scene?.hazeState ?: LocalAppHazeState.current
    val reducedMotion = rememberSystemReducedMotion()
    val base = spec.tint ?: MaterialTheme.colorScheme.surface

    if (hazeState == null) {
        MaterialFallbackSurface(modifier, spec, content)
        return
    }

    val glassStyle = remember(spec, reducedMotion, base) {
        val materialStyle = when (spec.material) {
            AppGlassMaterial.REGULAR -> GlassStyle.regular
            AppGlassMaterial.CLEAR -> GlassStyle.clear
        }
        materialStyle.then {
            backgroundColor(base.copy(alpha = spec.alpha.coerceIn(0f, 1f)))
            tint((spec.tint ?: base).copy(alpha = spec.tintAlpha.coerceIn(0f, 1f)))
            shape(spec.shape)
            edgeSoftness(if (reducedMotion) 1.dp else spec.edgeSoftness)
            specularIntensity(
                if (reducedMotion) {
                    (spec.specularIntensity * 0.45f).coerceIn(0f, 1f)
                } else {
                    spec.specularIntensity.coerceIn(0f, 1f)
                }
            )
        }
    }
    val motionPolicy = if (reducedMotion) {
        GlassReducedMotionPolicy.Reduced
    } else {
        GlassReducedMotionPolicy.System
    }

    Box(
        modifier = modifier
            .clip(spec.shape)
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = glassStyle,
                interactionReducedMotionPolicy = motionPolicy
            )
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(
                        alpha = spec.borderAlpha.coerceIn(0f, 1f)
                    )
                ),
                shape = spec.shape
            ),
        content = content
    )
}

@Composable
private fun MaterialFallbackSurface(
    modifier: Modifier,
    spec: AppGlassSpec,
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

@Composable
private fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) {
        val resolver = context.contentResolver
        fun scale(name: String): Float = runCatching {
            Settings.Global.getFloat(resolver, name)
        }.getOrDefault(1f)
        scale(Settings.Global.ANIMATOR_DURATION_SCALE) <= 0f ||
            scale(Settings.Global.TRANSITION_ANIMATION_SCALE) <= 0f ||
            scale(Settings.Global.WINDOW_ANIMATION_SCALE) <= 0f
    }
}
