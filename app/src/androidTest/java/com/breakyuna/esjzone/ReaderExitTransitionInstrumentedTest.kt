package com.breakyuna.esjzone

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.ReaderRoute
import com.breakyuna.esjzone.ui.navigation.AppNavigator
import com.breakyuna.esjzone.ui.navigation.LocalReaderNavigationSettled
import com.breakyuna.esjzone.ui.navigation.ReaderDestinationContent
import com.breakyuna.esjzone.ui.navigation.popTransition
import com.breakyuna.esjzone.ui.navigation.pushTransition
import com.breakyuna.esjzone.ui.reader.ReaderEntryPresentation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderExitTransitionInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun exitKeepsVisibleReaderThroughRecompositionAndReopeningUsesNewReader() {
        val registry = mutableMapOf<String, AppDestination>()
        val exitStates = mutableListOf<Boolean>()
        var replacementCompositions = 0
        val original = FakeReader("Original reading position", true) { settled -> exitStates += settled }
        val replacement = FakeReader("Restored reader", false) { replacementCompositions++ }
        val reopened = FakeReader("Reopened reading position", true) { }
        lateinit var navigator: AppNavigator

        composeRule.setContent {
            MaterialTheme {
                val stack = rememberNavBackStack(AppNavKey.Main)
                navigator = remember(stack) { AppNavigator(stack, registry) }
                NavDisplay(
                    backStack = stack,
                    modifier = Modifier.size(320.dp, 480.dp),
                    onBack = { navigator.pop() },
                    transitionSpec = pushTransition,
                    popTransitionSpec = popTransition,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator()
                    ),
                    entryProvider = entryProvider {
                        entry<AppNavKey.Main> { Box(Modifier.fillMaxSize()) }
                        entry<AppNavKey.Reader> { key ->
                            val transition = LocalNavAnimatedContentScope.current.transition
                            val settled = !transition.isRunning &&
                                transition.currentState == EnterExitState.Visible &&
                                transition.targetState == EnterExitState.Visible
                            CompositionLocalProvider(LocalReaderNavigationSettled provides settled) {
                                ReaderDestinationContent(navigator, key.route)
                            }
                        }
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navigator.push(original) }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.onNodeWithText("Original reading position").assertIsDisplayed()
        composeRule.runOnIdle {
            exitStates.clear()
            assertTrue(navigator.pop())
            assertFalse(registry.containsKey(original.key))
            // Stand in for the reader reconstructed from its route after cleanup.
            registry[original.key] = replacement
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(120)
        composeRule.onNodeWithText("Original reading position").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-entry-loading").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue("The outgoing entry should have recomposed for its transition", exitStates.any { !it })
            assertTrue("A reconstructed reader must not be composed during exit", replacementCompositions == 0)
        }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.runOnIdle { navigator.push(reopened) }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.onNodeWithText("Reopened reading position").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-entry-loading").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = true
    }

    private class FakeReader(
        private val label: String,
        private val ready: Boolean,
        private val onComposition: (Boolean) -> Unit
    ) : AppDestination {
        override val key = "ChapterPage:123:/forum/123/chapter.html"
        override val navKey = AppNavKey.Reader(ReaderRoute("123", "/forum/123/chapter.html"))

        @Composable
        override fun Content(navigator: AppNavigator) {
            val settled = LocalReaderNavigationSettled.current
            SideEffect { onComposition(settled) }
            ReaderEntryPresentation(ready, Color.White, null, 0f, "Loading") {
                Text(label)
            }
        }
    }
}
