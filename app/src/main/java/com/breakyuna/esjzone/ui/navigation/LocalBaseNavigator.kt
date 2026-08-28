package com.breakyuna.esjzone.ui.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.navigator.Navigator

/** Null means the component is being rendered outside the app-level navigator. */
val LocalBaseNavigator: ProvidableCompositionLocal<Navigator?> =
    staticCompositionLocalOf { null }
