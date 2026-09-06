package com.breakyuna.esjzone.ui.theme.catppuccin
import com.breakyuna.esjzone.app.PresentationAccess

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun CatppuccinDynamicTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val theme by PresentationAccess.settings.themeFlow.collectAsState(initial = CatppuccinThemeType.LATTE_YELLOW)
    val colorScheme = if (useDarkTheme) theme.darkColorScheme else theme.lightColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
