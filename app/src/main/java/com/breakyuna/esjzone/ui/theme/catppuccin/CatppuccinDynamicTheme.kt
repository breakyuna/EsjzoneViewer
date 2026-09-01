package com.breakyuna.esjzone.ui.theme.catppuccin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.breakyuna.esjzone.GlobalSettings

@Composable
fun CatppuccinDynamicTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val theme by GlobalSettings.theme
    val colorScheme = if (useDarkTheme) theme.darkColorScheme else theme.lightColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
