package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Product geometry. Feature code must use these tokens instead of inventing local constants. */
object AppSpacing {
    val zero = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/** Layout dimensions shared by feature shells and reusable components. */
object AppLayout {
    val pageHorizontal = AppSpacing.lg
    val pageVertical = AppSpacing.lg
    val section = AppSpacing.xxl
    val item = AppSpacing.md
    val divider = 1.dp
    val contentMaxWidth = 960.dp
    val compactCardWidth = 148.dp
    val compactCardHeight = 196.dp
    /** Fixed home-rail tile geometry: the cover itself is a strict 2:3 portrait. */
    val homeTileWidth = 116.dp
    val homeTileCoverHeight = 174.dp
    val listCoverWidth = 88.dp
    val listCoverHeight = 120.dp
    val featureCoverWidth = 104.dp
    val featureCoverHeight = 140.dp
}

object AppShapes {
    val none = RoundedCornerShape(0.dp)
    val compact = RoundedCornerShape(8.dp)
    val standard = RoundedCornerShape(16.dp)
    val prominent = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(50)
}

object AppTypography {
    private val display = FontFamily.SansSerif
    private val body = FontFamily.SansSerif

    val displayLarge = TextStyle(
        fontFamily = display,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold
    )
    val displayMedium = TextStyle(
        fontFamily = display,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold
    )
    val titleLarge = TextStyle(
        fontFamily = display,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold
    )
    val titleMedium = TextStyle(
        fontFamily = display,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    )
    val bodyLarge = TextStyle(fontFamily = body, fontSize = 16.sp, lineHeight = 24.sp)
    val bodyMedium = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 20.sp)
    val bodySmall = TextStyle(fontFamily = body, fontSize = 12.sp, lineHeight = 16.sp)
    val labelLarge = TextStyle(
        fontFamily = body,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    )
    val labelMedium = TextStyle(
        fontFamily = body,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
}

object AppElevation {
    val flat = 0.dp
    val raised = 2.dp
    val floating = 6.dp
}

object AppTouchTarget {
    val minimum = 48.dp
}

/**
 * Semantic colors for states that are not represented by a single Material role.
 * They intentionally derive from the active Material color scheme so dynamic color
 * and dark mode remain coherent.
 */

@Immutable
data class AppStateColors(
    val content: Color,
    val contentMuted: Color,
    val contentDisabled: Color,
    val container: Color,
    val containerRaised: Color,
    val containerAccent: Color,
    val danger: Color
)

@Composable
fun appStateColors(): AppStateColors = AppStateColors(
    content = MaterialTheme.colorScheme.onBackground,
    contentMuted = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDisabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    container = MaterialTheme.colorScheme.surface,
    containerRaised = MaterialTheme.colorScheme.surfaceContainer,
    containerAccent = MaterialTheme.colorScheme.primaryContainer,
    danger = MaterialTheme.colorScheme.error
)

/** Stable colors for content cards, tags, and separators. */
@Immutable
data class AppSurfaceColors(
    val card: Color,
    val cardRaised: Color,
    val subtle: Color,
    val divider: Color,
    val scrim: Color
)

@Composable
fun appSurfaceColors(): AppSurfaceColors = AppSurfaceColors(
    card = MaterialTheme.colorScheme.surfaceContainer,
    cardRaised = MaterialTheme.colorScheme.surfaceContainerHigh,
    subtle = MaterialTheme.colorScheme.surfaceContainerLow,
    divider = MaterialTheme.colorScheme.outlineVariant,
    scrim = MaterialTheme.colorScheme.scrim
)

/** Stable identifiers for lazy content. Use with `key` and `contentType`. */
object AppContentType {
    const val novel = "novel"
    const val novelCompact = "novel_compact"
    const val chapter = "chapter"
    const val chapterGroup = "chapter_group"
    const val comment = "comment"
    const val download = "download"
    const val history = "history"
    const val bookshelf = "bookshelf"
    const val loading = "loading"
    const val empty = "empty"
    const val error = "error"
    const val offline = "offline"
}
