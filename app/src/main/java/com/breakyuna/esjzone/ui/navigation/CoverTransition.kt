package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator

/**
 * Shows a pushed screen over the current screen while keeping the old screen
 * stationary. Popping slides the outgoing screen away to reveal the restored
 * screen underneath it.
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
            when (navigator.lastEvent) {
                StackEvent.Push ->
                    (slideInHorizontally(initialOffsetX = { it }) togetherWith
                        ExitTransition.KeepUntilTransitionsFinished).apply {
                        // The incoming screen must cover the held outgoing screen.
                        targetContentZIndex = 1f
                    }

                StackEvent.Pop ->
                    (EnterTransition.None togetherWith
                        slideOutHorizontally(targetOffsetX = { it })).apply {
                        // Reveal the restored screen as the outgoing screen moves right.
                        targetContentZIndex = -1f
                    }

                StackEvent.Replace,
                StackEvent.Idle ->
                    // Replacement is not a directional navigation operation.
                    EnterTransition.None togetherWith ExitTransition.None
            }
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
