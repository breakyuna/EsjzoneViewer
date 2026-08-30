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
        // Custom transitions do not go through Voyager's CurrentScreen(),
        // which is also responsible for putting each screen inside its
        // SaveableStateProvider.  Without this wrapper, rememberSaveable
        // values such as reader scroll position and selected tabs are lost
        // whenever a page is pushed and later popped.
        navigator.saveableState("currentScreen", screen) {
            content(screen)
        }
    }
}
