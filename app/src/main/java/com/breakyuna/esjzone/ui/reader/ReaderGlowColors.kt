package com.breakyuna.esjzone.ui.reader

import kotlin.math.*

internal data class ReaderGlowColors(val text: Long, val glow: Long)
internal data class ReaderOklch(val l: Double, val c: Double, val h: Double)

// D65 Oklab matrices: https://bottosson.github.io/posts/oklab/
internal fun readerOklch(color: Long): ReaderOklch {
    fun linear(shift: Int): Double {
        val s = (color shr shift and 255) / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    val r = linear(16); val g = linear(8); val b = linear(0)
    val l = Math.cbrt(0.4122214708*r + 0.5363325363*g + 0.0514459929*b)
    val m = Math.cbrt(0.2119034982*r + 0.6806995451*g + 0.1073969566*b)
    val s = Math.cbrt(0.0883024619*r + 0.2817188376*g + 0.6299787005*b)
    val a = 1.9779984951*l - 2.4285922050*m + 0.4505937099*s
    val bb = 0.0259040371*l + 0.7827717662*m - 0.8086757660*s
    return ReaderOklch(0.2104542553*l + 0.7936177850*m - 0.0040720468*s, hypot(a, bb), atan2(bb, a))
}

/** Map into sRGB by reducing chroma at fixed L/h, never by clipping RGB channels. */
internal fun readerGamutMap(color: ReaderOklch): Long {
    fun rgb(c: Double): DoubleArray {
        val a = c * cos(color.h); val b = c * sin(color.h)
        val l = (color.l + 0.3963377774*a + 0.2158037573*b).pow(3)
        val m = (color.l - 0.1055613458*a - 0.0638541728*b).pow(3)
        val s = (color.l - 0.0894841775*a - 1.2914855480*b).pow(3)
        return doubleArrayOf(4.0767416621*l - 3.3077115913*m + 0.2309699292*s,
            -1.2684380046*l + 2.6097574011*m - 0.3413193965*s,
            -0.0041960863*l - 0.7034186147*m + 1.7076147010*s)
    }
    fun inside(rgb: DoubleArray) = rgb.all { it in 0.0..1.0 }
    var low = 0.0; var high = color.c
    var result = rgb(high)
    if (!inside(result)) {
        repeat(28) {
            val mid = (low + high) / 2
            if (inside(rgb(mid))) low = mid else high = mid
        }
        result = rgb(low)
    }
    fun encode(v: Double): Long = ((if (v <= 0.0031308) 12.92*v else 1.055*v.pow(1/2.4)-0.055)*255).roundToLong()
    return 0xFF000000L or (encode(result[0]) shl 16) or (encode(result[1]) shl 8) or encode(result[2])
}

internal fun readerGlowColors(settings: ReaderImageBackground): ReaderGlowColors {
    val text = settings.textColor
    val original = readerOklch(text)
    fun glowFor(color: Long): Long {
        val tone = readerOklch(color)
        val light = tone.l >= 0.55
        return when (settings.glowMode) {
        ReaderGlowMode.SAME_HUE -> readerGamutMap(tone.copy(
            l = (if (light) settings.lightTextGlowL else settings.darkTextGlowL) / 100.0,
            c = tone.c * settings.glowChromaRetention / 100.0))
        ReaderGlowMode.BLACK_WHITE -> if (light) 0xFF000000L else 0xFFFFFFFFL
        ReaderGlowMode.CUSTOM -> settings.customGlowColor ?: readerGamutMap(tone.copy(
            l = (if (light) settings.lightTextGlowL else settings.darkTextGlowL) / 100.0,
            c = tone.c * settings.glowChromaRetention / 100.0))
    }
    }
    val glow = glowFor(text)
    fun passes(color: Long) = readerContrast(readerPixelLuminance(color.toInt()), readerPixelLuminance(glowFor(color).toInt())) >= 4.5
    if (!settings.autoClampLightness || !settings.glowEnabled || settings.glowStrength <= 0f || passes(text)) {
        return ReaderGlowColors(text, glow)
    }
    // Find the closest passing lightness in either direction; evaluate quantized sRGB contrast.
    for (step in 1..1000) {
        val delta = step / 1000.0
        for (l in listOf(original.l + delta, original.l - delta)) {
            if (l !in 0.0..1.0) continue
            val candidate = readerGamutMap(original.copy(l = l))
            if (passes(candidate)) return ReaderGlowColors(candidate, glowFor(candidate))
        }
    }
    // A fixed sRGB glow always contrasts by >=4.5 with at least one endpoint.
    val endpoint = if (readerPixelLuminance(glow.toInt()) > 0.179) 0xFF000000L else 0xFFFFFFFFL
    return ReaderGlowColors(endpoint, glowFor(endpoint))
}
