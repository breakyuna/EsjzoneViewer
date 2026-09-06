package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.breakyuna.esjzone.R

/** Accent palettes owned by the rebuilt design system. */
enum class AppThemeVariant(
    val lightAccent: Color,
    val darkAccent: Color,
    val labelRes: Int
) {
    OCEAN(Color(0xFF315F90), Color(0xFFA2C9FF), R.string.settings_theme_ocean),
    MINT(Color(0xFF356A54), Color(0xFF8FD8B2), R.string.settings_theme_mint),
    SUNSET(Color(0xFF8A4F28), Color(0xFFFFB787), R.string.settings_theme_sunset),
    LAVENDER(Color(0xFF675080), Color(0xFFD7B8F5), R.string.settings_theme_lavender);

    companion object {
        val DEFAULT: AppThemeVariant = OCEAN

        /** Unknown values intentionally fall back so old or corrupt cache data is harmless. */
        fun fromPersistedName(value: String?): AppThemeVariant =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

private fun lightColors(variant: AppThemeVariant) = lightColorScheme(
    primary = variant.lightAccent,
    onPrimary = Color.White,
    primaryContainer = variant.lightAccent.copy(alpha = 0.16f),
    onPrimaryContainer = Color(0xFF102033),
    secondary = Color(0xFF535F70),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1DAFF),
    onTertiaryContainer = Color(0xFF251431),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0)
)

private fun darkColors(variant: AppThemeVariant) = darkColorScheme(
    primary = variant.darkAccent,
    onPrimary = Color(0xFF00315C),
    primaryContainer = variant.darkAccent.copy(alpha = 0.22f),
    onPrimaryContainer = Color(0xFFE1EEFF),
    secondary = Color(0xFFBBC7DA),
    tertiary = Color(0xFFD5BEE6),
    onTertiary = Color(0xFF3A2948),
    tertiaryContainer = Color(0xFF513E5F),
    onTertiaryContainer = Color(0xFFF1DAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F)
)

/** Root visual contract for the rebuilt presentation layer. */
@Composable
fun AppTheme(
    variant: AppThemeVariant = AppThemeVariant.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        dynamicColor -> if (darkTheme) darkColors(variant) else lightColors(variant)
        darkTheme -> darkColors(variant)
        else -> lightColors(variant)
    }
    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(
            displayLarge = AppTypography.displayLarge,
            displayMedium = AppTypography.displayMedium,
            titleLarge = AppTypography.titleLarge,
            titleMedium = AppTypography.titleMedium,
            bodyLarge = AppTypography.bodyLarge,
            bodyMedium = AppTypography.bodyMedium,
            bodySmall = AppTypography.bodySmall,
            labelLarge = AppTypography.labelLarge,
            labelMedium = AppTypography.labelMedium
        ),
        shapes = androidx.compose.material3.Shapes(
            extraSmall = AppShapes.compact,
            small = AppShapes.compact,
            medium = AppShapes.standard,
            large = AppShapes.prominent,
            extraLarge = AppShapes.prominent
        ),
        content = content
    )
}
