package com.breakyuna.esjzone.ui.reader

import kotlin.math.floor
import kotlin.math.pow

internal val readerGlowRadiiEm = listOf(0.07f, 0.17f, 0.30f, 0.40f)
internal const val READER_GLOW_ALPHA = 0.9f

internal fun readerPixelLuminance(pixel: Int): Double {
    fun linear(value: Int): Double {
        val s = value / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(pixel shr 16 and 255) + 0.7152 * linear(pixel shr 8 and 255) + 0.0722 * linear(pixel and 255)
}

internal fun readerContrast(a: Double, b: Double): Double = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)

/** Lower fifth percentile: ignore a few worst pixels, rather than taking the best 95th percentile. */
internal fun readerGlowRequired(samples: List<Double>): Boolean = samples.isNotEmpty() &&
    samples.sorted()[floor((samples.size - 1) * 0.05).toInt()] < 4.5

/** Two consecutive two-second windows below 45 fps, observed only during actual motion. */
internal class ReaderGlowFrameTracker {
    private var start = 0L
    private var count = 0
    private var badWindows = 0
    fun frame(time: Long): Boolean {
        if (start == 0L) { start = time; return false }
        count++
        val elapsed = time - start
        if (elapsed < 2_000_000_000L) return false
        badWindows = if (count * 1_000_000_000.0 / elapsed < 45.0) badWindows + 1 else 0
        start = time
        count = 0
        return badWindows >= 2
    }
}

