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
