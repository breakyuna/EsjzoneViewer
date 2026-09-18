package com.breakyuna.esjzone.ui.designsystem.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt

/**
 * A small GPU-only pass over the dock material, BEFORE decorations and glyphs.
 * Five local samples estimate brightness/detail. No window capture, bitmap readback,
 * extra Haze source, frame timer, or background-luminance state is required.
 */
internal interface NavigationBackdrop {
    fun effect(size: Size): androidx.compose.ui.graphics.RenderEffect
}

@Composable
internal fun rememberNavigationBackdrop(
    dark: Boolean,
    backing: Color,
    vertical: Boolean,
    itemCount: Int,
    density: Float
): NavigationBackdrop? {
    val accelerated = LocalView.current.isHardwareAccelerated
    return remember(dark, backing, vertical, itemCount, density, accelerated) {
        if (accelerated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Unsupported shader implementations retain the conventional local halo.
            runCatching { ShaderNavigationBackdrop(dark, backing, vertical, itemCount, density) }.getOrNull()
        } else {
            null
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ShaderNavigationBackdrop(
    dark: Boolean,
    backing: Color,
    vertical: Boolean,
    itemCount: Int,
    density: Float
) : NavigationBackdrop {
    private val shader = RuntimeShader(NAVIGATION_BACKDROP_SHADER).apply {
        setFloatUniform("bounds", 1f, 1f)
        setFloatUniform("dark", if (dark) 1f else 0f)
        setColorUniform("backing", backing.toArgb())
        setFloatUniform("vertical", if (vertical) 1f else 0f)
        setFloatUniform("itemCount", itemCount.toFloat())
        setFloatUniform("density", density)
        // Share geometry with the actual selectable slots, including rail gap rounding.
        setFloatUniform("padding", (NavigationGlassMetrics.horizontalPadding.value * density).roundToInt().toFloat())
        val itemPixels = (NavigationGlassMetrics.railItemSize.value * density).roundToInt()
        val gapPixels = (NavigationGlassMetrics.railItemGap.value * density).roundToInt()
        val paddingPixels = (NavigationGlassMetrics.railVerticalPadding.value * density).roundToInt()
        setFloatUniform("railStart", paddingPixels + itemPixels / 2f)
        setFloatUniform("railStep", (itemPixels + gapPixels).toFloat())
    }
    private var previousSize = Size.Unspecified
    private var renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop").asComposeRenderEffect()

    override fun effect(size: Size): androidx.compose.ui.graphics.RenderEffect {
        if (previousSize != size) {
            shader.setFloatUniform("bounds", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
            // Android snapshots the uniforms into the effect. Recreate only on resize.
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop").asComposeRenderEffect()
            previousSize = size
        }
        return renderEffect
    }
}

internal const val NAVIGATION_BACKDROP_SHADER = """
uniform shader backdrop;
uniform float2 bounds;
uniform float density;
uniform float padding;
uniform float railStart;
uniform float railStep;
uniform float itemCount;
uniform float vertical;
uniform float dark;
layout(color) uniform half4 backing;

float luminanceAt(float2 p) {
    half4 sampleColor = backdrop.eval(clamp(p, float2(1.0), max(bounds - 1.0, float2(1.0))));
    float3 rgb = float3(sampleColor.rgb) / max(float(sampleColor.a), 0.001);
    return dot(rgb, float3(0.2126, 0.7152, 0.0722));
}

half4 main(float2 p) {
    half4 pixel = backdrop.eval(p);
    if (pixel.a <= 0.001) return pixel;
    float slot = max((bounds.x - padding * 2.0) / itemCount, 1.0);
    float index = clamp(floor((p.x - padding) / slot), 0.0, itemCount - 1.0);
    float2 center = float2(padding + (index + 0.5) * slot, bounds.y * 0.5);
    float2 halfSize = float2(min(slot * 0.44, 36.0 * density), min(bounds.y * 0.4, 24.0 * density));
    if (vertical > 0.5) {
        index = clamp(floor((p.y - railStart) / railStep + 0.5), 0.0, itemCount - 1.0);
        center = float2(bounds.x * 0.5, railStart + index * railStep);
        halfSize = float2(18.0 * density);
    }
    float radius = min(14.0 * density, min(halfSize.x, halfSize.y));
    float2 q = abs(p - center) - halfSize + radius;
    float distance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float shield = 1.0 - smoothstep(-density, 5.0 * density, distance);
    // Leave most of the pane untouched, including its refractive perimeter.
    if (shield <= 0.001) return pixel;

    float2 dx = float2(halfSize.x * 0.6, 0.0);
    float2 dy = float2(0.0, halfSize.y * 0.6);
    float a = luminanceAt(center);
    float b = luminanceAt(center - dx);
    float c = luminanceAt(center + dx);
    float d = luminanceAt(center - dy);
    float e = luminanceAt(center + dy);
    float average = (a + b + c + d + e) * 0.2;
    float low = min(a, min(b, min(c, min(d, e))));
    float high = max(a, max(b, max(c, max(d, e))));
    float detail = smoothstep(0.08, 0.45, high - low);
    float conflict = mix(1.0 - smoothstep(0.18, 0.72, average), smoothstep(0.10, 0.62, average), dark);
    float protection = clamp(0.04 + conflict * 0.70 + detail * 0.08, 0.04, 0.74);
    half3 rgb = pixel.rgb / pixel.a;
    rgb = mix(rgb, backing.rgb, half(shield * protection));
    return half4(rgb * pixel.a, pixel.a);
}
"""
