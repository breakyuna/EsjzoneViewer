package com.breakyuna.esjzone.ui.app

import androidx.compose.runtime.Composable
import com.breakyuna.esjzone.ui.designsystem.glass.AppGlassHost
import com.breakyuna.esjzone.ui.navigation.AppNavigation

@Composable
fun App() {
    AppGlassHost {
        AppNavigation()
    }
}
