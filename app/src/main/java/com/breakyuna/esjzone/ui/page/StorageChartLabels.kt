package com.breakyuna.esjzone.ui.page

import androidx.compose.ui.graphics.Color

internal data class StorageRingCallout(val label: String, val color: Color, val angle: Float)

/** Input follows the anchors from top to bottom; keep that order while separating labels. */
internal fun storageLabelTops(
    centers: List<Float>, heights: List<Float>, top: Float, bottom: Float, gap: Float
): List<Float> {
    val positions = mutableListOf<Float>()
    var next = top
    centers.forEachIndexed { index, center ->
        val position = maxOf(next, center - heights[index] / 2)
        positions += position
        next = position + heights[index] + gap
    }
    // Resolve overflow upwards without allowing labels to cross their neighbours.
    next = bottom
    positions.indices.reversed().forEach { index ->
        positions[index] = minOf(positions[index], next - heights[index])
        next = positions[index] - gap
    }
    return positions
}
