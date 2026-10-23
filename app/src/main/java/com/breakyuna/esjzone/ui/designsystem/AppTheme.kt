package com.breakyuna.esjzone.ui.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.breakyuna.esjzone.AppPalette

private val MonochromeLightColors = lightColorScheme(
    primary = Color(0xFF111111), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDEDED), onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF3D3D3D), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E8E8), onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF5A5A5A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0F0F0), onTertiaryContainer = Color(0xFF151515),
    error = Color(0xFF111111), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFE7E7E7), onErrorContainer = Color(0xFF111111),
    background = Color(0xFFF5F5F5), onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFEAEAEA), onSurfaceVariant = Color(0xFF686868),
    surfaceDim = Color(0xFFE4E4E4), surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF2F2F2), surfaceContainerHigh = Color(0xFFECECEC),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    outline = Color(0xFF777777), outlineVariant = Color(0xFFE2E2E2), scrim = Color(0xFF000000)
)

private val MonochromeDarkColors = darkColorScheme(
    primary = Color(0xFFF5F5F5), onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF292929), onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFFD0D0D0), onSecondary = Color(0xFF171717),
    secondaryContainer = Color(0xFF242424), onSecondaryContainer = Color(0xFFF0F0F0),
    tertiary = Color(0xFFB5B5B5), onTertiary = Color(0xFF111111),
    tertiaryContainer = Color(0xFF202020), onTertiaryContainer = Color(0xFFEAEAEA),
    error = Color(0xFFF5F5F5), onError = Color(0xFF111111),
    errorContainer = Color(0xFF303030), onErrorContainer = Color(0xFFF5F5F5),
    background = Color(0xFF0B0B0B), onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF151515), onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF292929), onSurfaceVariant = Color(0xFFA6A6A6),
    surfaceDim = Color(0xFF0B0B0B), surfaceBright = Color(0xFF303030),
    surfaceContainerLowest = Color(0xFF090909), surfaceContainerLow = Color(0xFF111111),
    surfaceContainer = Color(0xFF151515), surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF242424),
    outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF2A2A2A), scrim = Color(0xFF000000)
)

/** 墨韵：warm paper neutrals, indigo ink primary and a vermilion seal accent. */
private val InkLightColors = lightColorScheme(
    primary = Color(0xFF3A5BA0), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4F6), onPrimaryContainer = Color(0xFF0E2350),
    inversePrimary = Color(0xFFAFC4FF),
    secondary = Color(0xFF6B5E4E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE6D8), onSecondaryContainer = Color(0xFF2A2116),
    tertiary = Color(0xFFA0523A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8DDD3), onTertiaryContainer = Color(0xFF3D1206),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F4EE), onBackground = Color(0xFF1F1D1A),
    surface = Color(0xFFFFFDF9), onSurface = Color(0xFF1F1D1A),
    surfaceVariant = Color(0xFFEDE7DC), onSurfaceVariant = Color(0xFF6E675D),
    surfaceTint = Color(0xFF3A5BA0),
    inverseSurface = Color(0xFF34312C), inverseOnSurface = Color(0xFFF5F0E8),
    surfaceDim = Color(0xFFE3DED5), surfaceBright = Color(0xFFFFFDF9),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFAF7F1),
    surfaceContainer = Color(0xFFF2EEE6), surfaceContainerHigh = Color(0xFFECE7DE),
    surfaceContainerHighest = Color(0xFFE6E0D6),
    outline = Color(0xFF8C857A), outlineVariant = Color(0xFFE3DCD0), scrim = Color(0xFF000000)
)

private val InkDarkColors = darkColorScheme(
    primary = Color(0xFFAFC4FF), onPrimary = Color(0xFF102A5C),
    primaryContainer = Color(0xFF2A4478), onPrimaryContainer = Color(0xFFDCE4FF),
    inversePrimary = Color(0xFF3A5BA0),
    secondary = Color(0xFFD5C6B2), onSecondary = Color(0xFF392F22),
    secondaryContainer = Color(0xFF50463A), onSecondaryContainer = Color(0xFFF1E3CE),
    tertiary = Color(0xFFF2B59F), onTertiary = Color(0xFF5A1D0B),
    tertiaryContainer = Color(0xFF7A3420), onTertiaryContainer = Color(0xFFFFDBCF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141311), onBackground = Color(0xFFECE6DD),
    surface = Color(0xFF1F1D1A), onSurface = Color(0xFFECE6DD),
    surfaceVariant = Color(0xFF3A362F), onSurfaceVariant = Color(0xFFC2BAAE),
    surfaceTint = Color(0xFFAFC4FF),
    inverseSurface = Color(0xFFECE6DD), inverseOnSurface = Color(0xFF32302C),
    surfaceDim = Color(0xFF141311), surfaceBright = Color(0xFF3B3934),
    surfaceContainerLowest = Color(0xFF0F0E0C), surfaceContainerLow = Color(0xFF1A1916),
    surfaceContainer = Color(0xFF1F1D1A), surfaceContainerHigh = Color(0xFF292724),
    surfaceContainerHighest = Color(0xFF34322E),
    outline = Color(0xFF8F887D), outlineVariant = Color(0xFF403C35), scrim = Color(0xFF000000)
)

/** 樱：soft blush neutrals, sakura primary and a lavender accent. */
private val SakuraLightColors = lightColorScheme(
    primary = Color(0xFFB83A63), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2), onPrimaryContainer = Color(0xFF3F0019),
    inversePrimary = Color(0xFFFFB1C6),
    secondary = Color(0xFF75565E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E1), onSecondaryContainer = Color(0xFF2B151B),
    tertiary = Color(0xFF6E5A9C), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9DDFF), onTertiaryContainer = Color(0xFF26154F),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F9), onBackground = Color(0xFF24191C),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF24191C),
    surfaceVariant = Color(0xFFF3DDE2), onSurfaceVariant = Color(0xFF82696F),
    surfaceTint = Color(0xFFB83A63),
    inverseSurface = Color(0xFF3A2E31), inverseOnSurface = Color(0xFFFFECEF),
    surfaceDim = Color(0xFFEBD6DA), surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFF3F5),
    surfaceContainer = Color(0xFFFBECEF), surfaceContainerHigh = Color(0xFFF6E5E9),
    surfaceContainerHighest = Color(0xFFF0DFE3),
    outline = Color(0xFF9C8389), outlineVariant = Color(0xFFF0D8DE), scrim = Color(0xFF000000)
)

private val SakuraDarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C6), onPrimary = Color(0xFF5E1131),
    primaryContainer = Color(0xFF7E2948), onPrimaryContainer = Color(0xFFFFD9E2),
    inversePrimary = Color(0xFFB83A63),
    secondary = Color(0xFFE3BDC6), onSecondary = Color(0xFF422930),
    secondaryContainer = Color(0xFF5B3F46), onSecondaryContainer = Color(0xFFFFD9E1),
    tertiary = Color(0xFFD1BCFF), onTertiary = Color(0xFF3C2A6B),
    tertiaryContainer = Color(0xFF544283), onTertiaryContainer = Color(0xFFE9DDFF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF181213), onBackground = Color(0xFFEFDFE2),
    surface = Color(0xFF241B1D), onSurface = Color(0xFFEFDFE2),
    surfaceVariant = Color(0xFF514347), onSurfaceVariant = Color(0xFFD6C1C6),
    surfaceTint = Color(0xFFFFB1C6),
    inverseSurface = Color(0xFFEFDFE2), inverseOnSurface = Color(0xFF372E30),
    surfaceDim = Color(0xFF181213), surfaceBright = Color(0xFF403537),
    surfaceContainerLowest = Color(0xFF120D0E), surfaceContainerLow = Color(0xFF1F1719),
    surfaceContainer = Color(0xFF241B1D), surfaceContainerHigh = Color(0xFF2F2528),
    surfaceContainerHighest = Color(0xFF3A2F32),
    outline = Color(0xFF9F8C90), outlineVariant = Color(0xFF4A3D41), scrim = Color(0xFF000000)
)

/**
 * Dynamic schemes use the same tone for background and surface. The product keeps
 * cards on `surface` above a `background` page, so separate the two roles here.
 */
private fun ColorScheme.withCardSeparation(darkTheme: Boolean): ColorScheme = if (darkTheme) {
    copy(background = surfaceContainerLowest, surface = surfaceContainer)
} else {
    copy(background = surfaceContainerLow, surface = surfaceContainerLowest)
}

@Composable
private fun paletteColorScheme(palette: AppPalette, darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    return when (palette) {
        AppPalette.MONOCHROME -> if (darkTheme) MonochromeDarkColors else MonochromeLightColors
        AppPalette.SAKURA -> if (darkTheme) SakuraDarkColors else SakuraLightColors
        AppPalette.DYNAMIC -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            remember(context, darkTheme) {
                val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                scheme.withCardSeparation(darkTheme)
            }
        } else {
            if (darkTheme) InkDarkColors else InkLightColors
        }
        AppPalette.INK -> if (darkTheme) InkDarkColors else InkLightColors
    }
}

/** Swatch colors for palette pickers, independent from the active theme. */
fun paletteSwatch(palette: AppPalette, darkTheme: Boolean): Pair<Color, Color> = when (palette) {
    AppPalette.MONOCHROME -> if (darkTheme) MonochromeDarkColors.primary to MonochromeDarkColors.background
        else MonochromeLightColors.primary to MonochromeLightColors.background
    AppPalette.SAKURA -> if (darkTheme) SakuraDarkColors.primary to SakuraDarkColors.background
        else SakuraLightColors.primary to SakuraLightColors.background
    AppPalette.INK, AppPalette.DYNAMIC -> if (darkTheme) InkDarkColors.primary to InkDarkColors.background
        else InkLightColors.primary to InkLightColors.background
}

/** Palette-aware visual identity with a system-mode default. */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: AppPalette = AppPalette.INK,
    content: @Composable () -> Unit
) {
    val colorScheme = paletteColorScheme(palette, darkTheme)
    val semanticColors = remember(colorScheme, palette, darkTheme) {
        buildSemanticColors(colorScheme, palette, darkTheme)
    }
    CompositionLocalProvider(LocalAppSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = androidx.compose.material3.Typography(
                displayLarge = AppTypography.displayLarge,
                displayMedium = AppTypography.displayMedium,
                headlineSmall = AppTypography.headlineSmall,
                titleLarge = AppTypography.titleLarge,
                titleMedium = AppTypography.titleMedium,
                titleSmall = AppTypography.titleSmall,
                bodyLarge = AppTypography.bodyLarge,
                bodyMedium = AppTypography.bodyMedium,
                bodySmall = AppTypography.bodySmall,
                labelLarge = AppTypography.labelLarge,
                labelMedium = AppTypography.labelMedium,
                labelSmall = AppTypography.labelSmall
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
}
