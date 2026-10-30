package com.breakyuna.esjzone.ui.designsystem

import android.content.res.Configuration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key as composableKey
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.floor

internal data class AppViewportSize(val width: Float, val height: Float)
internal val LocalAppViewport = staticCompositionLocalOf<AppViewportSize?> { null }

/** Measure the page after the navigation rail and system insets have taken their space. */
@Composable
internal fun AppViewport(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(LocalAppViewport provides AppViewportSize(maxWidth.value, maxHeight.value)) {
            content()
        }
    }
}

/** Portrait, narrow split-screen and short phone windows keep their existing presentation. */
internal fun useWideLayout(width: Float, height: Float, fontScale: Float): Boolean =
    width >= 840f && height >= 480f && width > height && width / fontScale.coerceAtLeast(1f) >= 760f

@Composable
internal fun appViewportSize(): AppViewportSize {
    val configuration = LocalConfiguration.current
    return LocalAppViewport.current ?: AppViewportSize(
        configuration.screenWidthDp.toFloat(), configuration.screenHeightDp.toFloat()
    )
}

@Composable
internal fun appUsesWideLayout(): Boolean {
    val viewport = appViewportSize()
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        useWideLayout(viewport.width, viewport.height, configuration.fontScale)
}

/** Only wide landscape pages acquire a centered, bounded browsing surface. */
@Composable
internal fun Modifier.wideContentWidth(): Modifier = if (appUsesWideLayout()) {
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = AppLayout.wideContentMaxWidth)
} else this

internal fun wideListColumnCount(width: Float, fontScale: Float, maxWidth: Float = 960f): Int =
    floor((minOf(width, maxWidth) - 32f + 16f) / (360f * fontScale.coerceAtLeast(1f) + 16f))
        .toInt().coerceIn(1, 3)

@Composable
internal fun wideListColumns(maxWidth: Float = 960f): Int =
    if (appUsesWideLayout()) wideListColumnCount(appViewportSize().width, LocalConfiguration.current.fontScale, maxWidth)
    else 1

private class AdaptiveListAnchor(
    var columns: Int,
    val restoredKey: String? = null,
    var keyAtRow: ((Int) -> String?)? = null
) {
    fun firstKey(row: Int): String? {
        val lookup = keyAtRow
        return if (lookup == null) restoredKey else lookup(row)
    }
}

/** Capture the old grouping before remeasure; the saver also covers Activity recreation. */
@Composable
internal fun <T> PreserveAdaptiveListAnchor(
    state: LazyListState,
    items: List<T>,
    columns: Int,
    leadingItemCount: Int = 0,
    key: (T) -> String
) {
    val anchor = rememberSaveable(state, saver = mapSaver(
        save = { saved: AdaptiveListAnchor ->
            mapOf(
                "columns" to saved.columns,
                "key" to saved.firstKey(state.firstVisibleItemIndex)
            )
        },
        restore = { AdaptiveListAnchor(it["columns"] as Int, it["key"] as? String) }
    )) { AdaptiveListAnchor(columns) }

    if (anchor.columns != columns) {
        val firstKey = anchor.firstKey(state.firstVisibleItemIndex)
        val index = items.indexOfFirst { key(it) == firstKey }
        if (index >= 0) {
            state.requestScrollToItem(leadingItemCount + index / columns, state.firstVisibleItemScrollOffset)
        }
    }
    SideEffect {
        anchor.columns = columns
        anchor.keyAtRow = { row ->
            if (row < leadingItemCount) null
            else items.getOrNull((row - leadingItemCount) * columns)?.let(key)
        }
    }
}

/** Full-width headers/feedback keep their lazy keys; only the actual records form rows. */
internal fun <T> LazyListScope.adaptiveItems(
    items: List<T>,
    columns: Int,
    key: (T) -> Any,
    contentType: (T) -> Any? = { null },
    itemContent: @Composable LazyItemScope.(T) -> Unit
) {
    if (columns == 1) {
        items(items, key = key, contentType = contentType, itemContent = itemContent)
    } else {
        items(
            count = (items.size + columns - 1) / columns,
            key = { row -> "wide-row:${key(items[row * columns])}" },
            contentType = { row -> "wide-row:$columns:${contentType(items[row * columns])}" }
        ) { row ->
            val itemScope = this
            Row(Modifier.fillMaxWidth().animateItem(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                repeat(columns) { column ->
                    val index = row * columns + column
                    if (index < items.size) {
                        composableKey(key(items[index])) {
                            Box(Modifier.weight(1f)) { itemScope.itemContent(items[index]) }
                        }
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
