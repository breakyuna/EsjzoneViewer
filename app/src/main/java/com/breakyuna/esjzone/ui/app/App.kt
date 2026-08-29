package com.breakyuna.esjzone.ui.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import com.breakyuna.esjzone.ui.navigation.CoverTransition
import com.breakyuna.esjzone.ui.navigation.LocalAppNavigator
import com.breakyuna.esjzone.ui.screen.LoadingScreen

@Composable
fun App() {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Navigator(
            LoadingScreen()
        ) { navigator ->
            CoverTransition(navigator = navigator) { screen ->
                CompositionLocalProvider(value = LocalAppNavigator provides navigator) {
                    screen.Content()
                }
            }
        }
    }
}
