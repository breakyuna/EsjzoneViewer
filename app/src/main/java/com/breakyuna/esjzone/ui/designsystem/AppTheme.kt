package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

/** One neutral visual identity with a system-mode default. */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MonochromeDarkColors else MonochromeLightColors,
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
