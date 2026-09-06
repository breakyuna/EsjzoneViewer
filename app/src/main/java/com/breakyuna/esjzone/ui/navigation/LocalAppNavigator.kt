package com.breakyuna.esjzone.ui.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
/** Root Navigation 3 controller. */
val LocalAppNavigator: ProvidableCompositionLocal<AppNavigator?> =
    staticCompositionLocalOf { null }
