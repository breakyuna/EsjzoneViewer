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

private fun buildLightScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    background: Color,
    onBackground: Color,
    surface: Color,
    onSurface: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    surfaceDim: Color,
    surfaceBright: Color,
    surfaceContainerLowest: Color,
    surfaceContainerLow: Color,
    surfaceContainer: Color,
    surfaceContainerHigh: Color,
    surfaceContainerHighest: Color,
    outline: Color,
    outlineVariant: Color
) = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = Color.White,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceDim = surfaceDim,
    surfaceBright = surfaceBright,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    outline = outline,
    outlineVariant = outlineVariant
)

private fun buildDarkScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    background: Color,
    onBackground: Color,
    surface: Color,
    onSurface: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    surfaceDim: Color,
    surfaceBright: Color,
    surfaceContainerLowest: Color,
    surfaceContainerLow: Color,
    surfaceContainer: Color,
    surfaceContainerHigh: Color,
    surfaceContainerHighest: Color,
    outline: Color,
    outlineVariant: Color
) = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceDim = surfaceDim,
    surfaceBright = surfaceBright,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    outline = outline,
    outlineVariant = outlineVariant
)

private fun lightColors(variant: AppThemeVariant) = when (variant) {
    AppThemeVariant.OCEAN -> buildLightScheme(
        primary = Color(0xFF315F90),
        primaryContainer = Color(0xFFD3E4FF),
        onPrimaryContainer = Color(0xFF001C3A),
        secondary = Color(0xFF535F70),
        secondaryContainer = Color(0xFFD7E3F8),
        onSecondaryContainer = Color(0xFF101C2B),
        tertiary = Color(0xFF386568),
        tertiaryContainer = Color(0xFFBCEBEF),
        onTertiaryContainer = Color(0xFF002022),
        background = Color(0xFFF7F9FC),
        onBackground = Color(0xFF181C20),
        surface = Color(0xFFF7F9FC),
        onSurface = Color(0xFF181C20),
        surfaceVariant = Color(0xFFDEE3EB),
        onSurfaceVariant = Color(0xFF42474E),
        surfaceDim = Color(0xFFD8DAE0),
        surfaceBright = Color(0xFFF7F9FC),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF1F4F8),
        surfaceContainer = Color(0xFFEBEEF3),
        surfaceContainerHigh = Color(0xFFE5E9EE),
        surfaceContainerHighest = Color(0xFFDFE3E8),
        outline = Color(0xFF72787E),
        outlineVariant = Color(0xFFC2C7CE)
    )
    AppThemeVariant.MINT -> buildLightScheme(
        primary = Color(0xFF356A54),
        primaryContainer = Color(0xFFB8F1D5),
        onPrimaryContainer = Color(0xFF002114),
        secondary = Color(0xFF4E6355),
        secondaryContainer = Color(0xFFD1E8D7),
        onSecondaryContainer = Color(0xFF0C1F15),
        tertiary = Color(0xFF3B656E),
        tertiaryContainer = Color(0xFFBEEAF5),
        onTertiaryContainer = Color(0xFF001F25),
        background = Color(0xFFF6FAF6),
        onBackground = Color(0xFF171D19),
        surface = Color(0xFFF6FAF6),
        onSurface = Color(0xFF171D19),
        surfaceVariant = Color(0xFFDCE5DC),
        onSurfaceVariant = Color(0xFF404943),
        surfaceDim = Color(0xFFD6DDD6),
        surfaceBright = Color(0xFFF6FAF6),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF0F5F0),
        surfaceContainer = Color(0xFFEAF0EA),
        surfaceContainerHigh = Color(0xFFE4EBE4),
        surfaceContainerHighest = Color(0xFFDFE5DF),
        outline = Color(0xFF707973),
        outlineVariant = Color(0xFFC0C9C1)
    )
    AppThemeVariant.SUNSET -> buildLightScheme(
        primary = Color(0xFF8A4F28),
        primaryContainer = Color(0xFFFFDBCB),
        onPrimaryContainer = Color(0xFF331200),
        secondary = Color(0xFF755848),
        secondaryContainer = Color(0xFFFFDCCB),
        onSecondaryContainer = Color(0xFF2B160B),
        tertiary = Color(0xFF675E2E),
        tertiaryContainer = Color(0xFFF0E3A6),
        onTertiaryContainer = Color(0xFF211B00),
        background = Color(0xFFFFF8F5),
        onBackground = Color(0xFF211A17),
        surface = Color(0xFFFFF8F5),
        onSurface = Color(0xFF211A17),
        surfaceVariant = Color(0xFFF3DFD5),
        onSurfaceVariant = Color(0xFF52443C),
        surfaceDim = Color(0xFFE6D6CE),
        surfaceBright = Color(0xFFFFF8F5),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFFF1EB),
        surfaceContainer = Color(0xFFF9ECE5),
        surfaceContainerHigh = Color(0xFFF3E6DF),
        surfaceContainerHighest = Color(0xFFEDE0D9),
        outline = Color(0xFF84746B),
        outlineVariant = Color(0xFFD7C3B8)
    )
    AppThemeVariant.LAVENDER -> buildLightScheme(
        primary = Color(0xFF675080),
        primaryContainer = Color(0xFFEDDCFF),
        onPrimaryContainer = Color(0xFF220B38),
        secondary = Color(0xFF625B6C),
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1E1927),
        tertiary = Color(0xFF7A5260),
        tertiaryContainer = Color(0xFFFFD8E4),
        onTertiaryContainer = Color(0xFF31101D),
        background = Color(0xFFFFF7FF),
        onBackground = Color(0xFF1D1A20),
        surface = Color(0xFFFFF7FF),
        onSurface = Color(0xFF1D1A20),
        surfaceVariant = Color(0xFFE6E0EC),
        onSurfaceVariant = Color(0xFF48454E),
        surfaceDim = Color(0xFFDFD7E1),
        surfaceBright = Color(0xFFFFF7FF),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF9F1FB),
        surfaceContainer = Color(0xFFF3EBF5),
        surfaceContainerHigh = Color(0xFFEDE5EF),
        surfaceContainerHighest = Color(0xFFE7E0EA),
        outline = Color(0xFF7A757F),
        outlineVariant = Color(0xFFC9C4D0)
    )
}

private fun darkColors(variant: AppThemeVariant) = when (variant) {
    AppThemeVariant.OCEAN -> buildDarkScheme(
        primary = Color(0xFFA2C9FF),
        onPrimary = Color(0xFF00315C),
        primaryContainer = Color(0xFF144777),
        onPrimaryContainer = Color(0xFFD3E4FF),
        secondary = Color(0xFFBBC7DB),
        onSecondary = Color(0xFF253141),
        secondaryContainer = Color(0xFF3C4858),
        onSecondaryContainer = Color(0xFFD7E3F8),
        tertiary = Color(0xFFA0CFD2),
        onTertiary = Color(0xFF00373A),
        tertiaryContainer = Color(0xFF1E4D50),
        onTertiaryContainer = Color(0xFFBCEBEF),
        background = Color(0xFF101418),
        onBackground = Color(0xFFE0E2E8),
        surface = Color(0xFF101418),
        onSurface = Color(0xFFE0E2E8),
        surfaceVariant = Color(0xFF42474E),
        onSurfaceVariant = Color(0xFFC2C7CE),
        surfaceDim = Color(0xFF101418),
        surfaceBright = Color(0xFF36393E),
        surfaceContainerLowest = Color(0xFF0B0E12),
        surfaceContainerLow = Color(0xFF181C20),
        surfaceContainer = Color(0xFF1C2024),
        surfaceContainerHigh = Color(0xFF272A2F),
        surfaceContainerHighest = Color(0xFF31353A),
        outline = Color(0xFF8C9198),
        outlineVariant = Color(0xFF42474E)
    )
    AppThemeVariant.MINT -> buildDarkScheme(
        primary = Color(0xFF8FD8B2),
        onPrimary = Color(0xFF003824),
        primaryContainer = Color(0xFF1C513D),
        onPrimaryContainer = Color(0xFFB8F1D5),
        secondary = Color(0xFFB5CCBC),
        onSecondary = Color(0xFF213529),
        secondaryContainer = Color(0xFF374B3E),
        onSecondaryContainer = Color(0xFFD1E8D7),
        tertiary = Color(0xFFA2CED8),
        onTertiary = Color(0xFF03363E),
        tertiaryContainer = Color(0xFF224D55),
        onTertiaryContainer = Color(0xFFBEEAF5),
        background = Color(0xFF0F1511),
        onBackground = Color(0xFFDFE4DF),
        surface = Color(0xFF0F1511),
        onSurface = Color(0xFFDFE4DF),
        surfaceVariant = Color(0xFF404943),
        onSurfaceVariant = Color(0xFFC0C9C1),
        surfaceDim = Color(0xFF0F1511),
        surfaceBright = Color(0xFF353B36),
        surfaceContainerLowest = Color(0xFF0A0F0C),
        surfaceContainerLow = Color(0xFF171D19),
        surfaceContainer = Color(0xFF1B211D),
        surfaceContainerHigh = Color(0xFF252C27),
        surfaceContainerHighest = Color(0xFF303732),
        outline = Color(0xFF8A938C),
        outlineVariant = Color(0xFF404943)
    )
    AppThemeVariant.SUNSET -> buildDarkScheme(
        primary = Color(0xFFFFB787),
        onPrimary = Color(0xFF502400),
        primaryContainer = Color(0xFF6E3914),
        onPrimaryContainer = Color(0xFFFFDBCB),
        secondary = Color(0xFFE5BEAC),
        onSecondary = Color(0xFF422B1E),
        secondaryContainer = Color(0xFF5B4133),
        onSecondaryContainer = Color(0xFFFFDCCB),
        tertiary = Color(0xFFD3C78C),
        onTertiary = Color(0xFF373004),
        tertiaryContainer = Color(0xFF4E4619),
        onTertiaryContainer = Color(0xFFF0E3A6),
        background = Color(0xFF18120F),
        onBackground = Color(0xFFEDE0D9),
        surface = Color(0xFF18120F),
        onSurface = Color(0xFFEDE0D9),
        surfaceVariant = Color(0xFF52443C),
        onSurfaceVariant = Color(0xFFD7C3B8),
        surfaceDim = Color(0xFF18120F),
        surfaceBright = Color(0xFF3F3733),
        surfaceContainerLowest = Color(0xFF120D0B),
        surfaceContainerLow = Color(0xFF211A17),
        surfaceContainer = Color(0xFF251E1A),
        surfaceContainerHigh = Color(0xFF302824),
        surfaceContainerHighest = Color(0xFF3B332F),
        outline = Color(0xFF9F8D84),
        outlineVariant = Color(0xFF52443C)
    )
    AppThemeVariant.LAVENDER -> buildDarkScheme(
        primary = Color(0xFFD7B8F5),
        onPrimary = Color(0xFF372150),
        primaryContainer = Color(0xFF4F3867),
        onPrimaryContainer = Color(0xFFEDDCFF),
        secondary = Color(0xFFCCC3D8),
        onSecondary = Color(0xFF332D3D),
        secondaryContainer = Color(0xFF4A4354),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEBB8C8),
        onTertiary = Color(0xFF472532),
        tertiaryContainer = Color(0xFF603B49),
        onTertiaryContainer = Color(0xFFFFD8E4),
        background = Color(0xFF151218),
        onBackground = Color(0xFFE7E0EA),
        surface = Color(0xFF151218),
        onSurface = Color(0xFFE7E0EA),
        surfaceVariant = Color(0xFF48454E),
        onSurfaceVariant = Color(0xFFC9C4D0),
        surfaceDim = Color(0xFF151218),
        surfaceBright = Color(0xFF3B373E),
        surfaceContainerLowest = Color(0xFF100D13),
        surfaceContainerLow = Color(0xFF1D1A20),
        surfaceContainer = Color(0xFF221E25),
        surfaceContainerHigh = Color(0xFF2C2930),
        surfaceContainerHighest = Color(0xFF37333B),
        outline = Color(0xFF948F9A),
        outlineVariant = Color(0xFF48454E)
    )
}

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
