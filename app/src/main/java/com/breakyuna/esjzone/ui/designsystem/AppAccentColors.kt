package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Non-Material roles, paired for readable status text on the active surfaces. */
@Immutable
data class AppAccentColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val favorite: Color,
    val favoriteContainer: Color
)

internal val LightAccentColors = AppAccentColors(
    success = Color(0xFF32734D), successContainer = Color(0xFFDDEFE1),
    warning = Color(0xFF825E1B), warningContainer = Color(0xFFF6E7CC),
    info = Color(0xFF426B92), infoContainer = Color(0xFFE2EDF7),
    favorite = Color(0xFFA44462), favoriteContainer = Color(0xFFF8E1E7)
)

internal val DarkAccentColors = AppAccentColors(
    success = Color(0xFF91D5A7), successContainer = Color(0xFF203D2B),
    warning = Color(0xFFE6C17A), warningContainer = Color(0xFF423321),
    info = Color(0xFFA7C8E7), infoContainer = Color(0xFF243B50),
    favorite = Color(0xFFF0ADC0), favoriteContainer = Color(0xFF4B2B37)
)

@Composable
fun appAccentColors(): AppAccentColors =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkAccentColors else LightAccentColors

private val LightChartColors = listOf(
    Color(0xFF246B61), Color(0xFF426B92), Color(0xFF79629D), Color(0xFF825621),
    Color(0xFFA65349), Color(0xFF596F32), Color(0xFF9A4F78), Color(0xFF3C7780)
)
private val DarkChartColors = listOf(
    Color(0xFF90D5C5), Color(0xFFA7C8E7), Color(0xFFC6B4E5), Color(0xFFE6BE86),
    Color(0xFFE8ADA3), Color(0xFFC0D79A), Color(0xFFE4AED0), Color(0xFF98CCD2)
)

/** Identity-based colors stay stable when rankings or the selected period change. */
internal fun chartColor(key: String, dark: Boolean): Color {
    val palette = if (dark) DarkChartColors else LightChartColors
    return palette[Math.floorMod(key.hashCode(), palette.size)]
}

@Composable
fun appChartColor(key: String): Color =
    chartColor(key, MaterialTheme.colorScheme.surface.luminance() < 0.5f)
