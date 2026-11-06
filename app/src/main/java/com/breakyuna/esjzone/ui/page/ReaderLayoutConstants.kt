package com.breakyuna.esjzone.ui.page

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reader-only geometry for immersive chrome reservations. These values are
 * deliberately separate from the application shell's navigation insets.
 */
internal object ReaderLayout {
    val pagedContentPadding = 2.dp
    val contentTopPadding = 52.dp
    val contentBottomPadding = 80.dp
    val progressPreviewBottomPadding = 150.dp
    val previousLoadingTopPadding = 42.dp
}

/**
 * Vertical scroll draws through the whole viewport, so the chapter-status overlay
 * has to shorten the list. [contentEndPadding] only applies after the last item.
 * Subtracting the overlay keeps that end gap when it already cleared the bar, and
 * still keeps the body above an overlay taller than the fixed gap.
 *
 * Returns viewport inset, then the remaining end-of-chapter content padding.
 */
internal fun readerScrollBottomSpace(contentEndPadding: Dp, statusInset: Dp): Pair<Dp, Dp> {
    val inset = statusInset.coerceAtLeast(0.dp)
    return inset to (contentEndPadding - inset).coerceAtLeast(0.dp)
}
