package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppWindowSizeClass { Compact, Medium, Expanded }

@Immutable
data class AppAdaptiveMetrics(
    val sizeClass: AppWindowSizeClass,
    val horizontalPadding: Dp,
    val contentMaxWidth: Dp,
    val columnGap: Dp,
    val minCardWidth: Dp = AppLayout.compactCardWidth,
    val contentColumns: Int = 1
) {
    /** The width available to a centered content column at this window size. */
    val contentWidth: Dp
        get() = contentMaxWidth

    /** A stable grid count for cards that do not own their own adaptive policy. */
    val gridColumns: Int
        get() = contentColumns
}

@Composable
fun rememberAppAdaptiveMetrics(): AppAdaptiveMetrics {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return remember(width) {
        when {
            width < 600.dp -> AppAdaptiveMetrics(
                AppWindowSizeClass.Compact,
                AppSpacing.lg,
                width - AppSpacing.lg * 2,
                AppSpacing.md,
                contentColumns = 1
            )
            width < 840.dp -> AppAdaptiveMetrics(
                AppWindowSizeClass.Medium,
                AppSpacing.xl,
                720.dp,
                AppSpacing.lg,
                contentColumns = 2
            )
            else -> AppAdaptiveMetrics(
                AppWindowSizeClass.Expanded,
                AppSpacing.xxl,
                960.dp,
                AppSpacing.xl,
                contentColumns = 3
            )
        }
    }
}
