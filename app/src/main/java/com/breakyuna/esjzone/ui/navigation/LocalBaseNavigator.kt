package com.breakyuna.esjzone.ui.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
/** Active tab stack controller. */
val LocalBaseNavigator: ProvidableCompositionLocal<AppNavigator?> =
    staticCompositionLocalOf { null }
