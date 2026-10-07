package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val PaperLightColors = lightColorScheme(
    primary = Color(0xFF246B61), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEDE5), onPrimaryContainer = Color(0xFF174D43),
    secondary = Color(0xFF526B5E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EBDF), onSecondaryContainer = Color(0xFF304C3B),
    tertiary = Color(0xFF825621), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E7CC), onTertiaryContainer = Color(0xFF684315),
    error = Color(0xFFAE4040), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF732727),
    background = Color(0xFFF5F3EE), onBackground = Color(0xFF23332D),
    surface = Color(0xFFFFFEFA), onSurface = Color(0xFF23332D),
    surfaceVariant = Color(0xFFEBF0E9), onSurfaceVariant = Color(0xFF5E6B63),
    surfaceDim = Color(0xFFDEDFD6), surfaceBright = Color(0xFFFFFEFA),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F4ED),
    surfaceContainer = Color(0xFFEBF0E9), surfaceContainerHigh = Color(0xFFE4EAE1),
    surfaceContainerHighest = Color(0xFFDDE5DC),
    outline = Color(0xFF738078), outlineVariant = Color(0xFFD8DFD7),
    inverseSurface = Color(0xFF2C3831), inverseOnSurface = Color(0xFFEDF3ED),
    inversePrimary = Color(0xFF90D5C5), surfaceTint = Color(0xFF246B61),
    scrim = Color(0xFF000000)
)

internal val PaperDarkColors = darkColorScheme(
    primary = Color(0xFF90D5C5), onPrimary = Color(0xFF12382F),
    primaryContainer = Color(0xFF244B40), onPrimaryContainer = Color(0xFFCAEBDD),
    secondary = Color(0xFFAFC7BA), onSecondary = Color(0xFF23392D),
    secondaryContainer = Color(0xFF344A3D), onSecondaryContainer = Color(0xFFD4E8D6),
    tertiary = Color(0xFFE6BE86), onTertiary = Color(0xFF432C0D),
    tertiaryContainer = Color(0xFF423321), onTertiaryContainer = Color(0xFFF6DBB2),
    error = Color(0xFFFFB4AB), onError = Color(0xFF601D20),
    errorContainer = Color(0xFF713333), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121917), onBackground = Color(0xFFE2EAE4),
    surface = Color(0xFF1B2421), onSurface = Color(0xFFE2EAE4),
    surfaceVariant = Color(0xFF35433B), onSurfaceVariant = Color(0xFFA8B7AE),
    surfaceDim = Color(0xFF101714), surfaceBright = Color(0xFF37443D),
    surfaceContainerLowest = Color(0xFF0D1411), surfaceContainerLow = Color(0xFF17201C),
    surfaceContainer = Color(0xFF1F2A24), surfaceContainerHigh = Color(0xFF26332D),
    surfaceContainerHighest = Color(0xFF304037),
    outline = Color(0xFF82948A), outlineVariant = Color(0xFF35433B),
    inverseSurface = Color(0xFFE2EAE4), inverseOnSurface = Color(0xFF23332D),
    inversePrimary = Color(0xFF246B61), surfaceTint = Color(0xFF90D5C5),
    scrim = Color(0xFF000000)
)

/** Warm paper surfaces and an ink-green identity, with paired night colors. */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PaperDarkColors else PaperLightColors,
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
