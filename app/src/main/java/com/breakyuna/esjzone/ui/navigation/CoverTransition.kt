package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator

/**
 * Shows a pushed screen over the current screen while keeping the old screen
 * stationary. Popping reverses the direction so the restored screen covers
 * the outgoing one from the opposite side.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CoverTransition(
    navigator: Navigator,
    content: @Composable (Screen) -> Unit
) {
    AnimatedContent(
        targetState = navigator.lastItem,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val isPush = navigator.items.any { it.key == initialState.key }
            val enter = if (isPush) {
                slideInHorizontally(initialOffsetX = { it })
            } else {
                slideInHorizontally(initialOffsetX = { -it })
            }
            enter togetherWith ExitTransition.None
        },
        contentKey = { screen -> screen.key },
        label = "cover transition"
    ) { screen ->
        content(screen)
    }
}
