package com.breakyuna.esjzone.ui.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.withFrameNanos

/** Return false and release scrolling when the destination is no longer available. */
internal suspend fun PagerState.animateReaderPageTurn(
    targetPage: () -> Int?,
    durationMillis: () -> Float,
    reducedMotion: Boolean
): Boolean {
    var completed = false
    scroll {
        var progress = 0f
        var easedProgress = 0f
        var duration = durationMillis()
        var lastFrame = withFrameNanos { it }
        while (progress < 1f) {
            val targetAvailable = withFrameNanos { frame ->
                val elapsedMillis = (frame - lastFrame) / 1_000_000f
                lastFrame = frame
                // Resolve the stable destination again after preloads shift page indices.
                val target = targetPage()?.takeIf { it in 0 until pageCount } ?: return@withFrameNanos false
                updateTargetPage(target)
                duration = minOf(duration, durationMillis())
                progress = if (reducedMotion) 1f else (progress + elapsedMillis / duration).coerceAtMost(1f)
                val eased = FastOutSlowInEasing.transform(progress)
                val remaining = (target - currentPage - currentPageOffsetFraction) *
                    (layoutInfo.pageSize + layoutInfo.pageSpacing)
                scrollBy(remaining * ((eased - easedProgress) / (1f - easedProgress).coerceAtLeast(0.000001f)))
                easedProgress = eased
                if (progress == 1f) updateCurrentPage(target, 0f)
                true
            }
            if (!targetAvailable) return@scroll
        }
        completed = true
    }
    return completed
}
