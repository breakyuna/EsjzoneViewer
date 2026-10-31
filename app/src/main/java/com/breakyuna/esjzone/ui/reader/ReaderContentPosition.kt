package com.breakyuna.esjzone.ui.reader

/** Page starts use source block indices plus the fraction within a text block. */
internal fun readerItemForContentPosition(starts: List<Float>, position: Float): Int? =
    starts.indexOfLast { it <= position }.takeIf { it >= 0 }
        ?: starts.indices.firstOrNull()

internal fun readerBodyListIndex(
    bodyIndex: Int,
    hasPreviousVerification: Boolean,
    hasPreviousRetry: Boolean
): Int = bodyIndex + (if (hasPreviousVerification) 1 else 0) + (if (hasPreviousRetry) 1 else 0)

internal data class ReaderScrollAnchor(val key: String, val index: Int, val offset: Int)

/** Preserve the visible paragraph even when a whole chapter moves it beyond LazyColumn's key map. */
internal fun readerRebasedScrollAnchor(
    previousHeadKey: String?,
    currentHeadKey: String?,
    visible: ReaderScrollAnchor?,
    bodyIndices: Map<String, Int>,
    bodyStartOffset: Int
): ReaderScrollAnchor? {
    if (previousHeadKey == null || previousHeadKey == currentHeadKey || visible == null) return null
    val bodyIndex = bodyIndices[visible.key] ?: return null
    return visible.copy(index = bodyIndex + bodyStartOffset)
}
