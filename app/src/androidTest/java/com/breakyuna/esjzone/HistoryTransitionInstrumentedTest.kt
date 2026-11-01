package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.ReaderRoute
import com.breakyuna.esjzone.ui.navigation.popTransition
import com.breakyuna.esjzone.ui.navigation.pushTransition
import com.breakyuna.esjzone.ui.page.LocalHistoryPageModel
import com.breakyuna.esjzone.ui.page.rememberLocalHistoryPresentationState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryTransitionInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun openingOlderRecordKeepsHistoryOrderDuringRootReaderTransition() {
        val first = activity("first", 200L)
        val second = activity("second", 100L)
        val original = LocalHistoryPageModel.State.Result(listOf(first, second))
        val promoted = LocalHistoryPageModel.State.Result(listOf(second.copy(lastReadAt = 300L), first))
        val source = MutableStateFlow<LocalHistoryPageModel.State>(original)
        lateinit var rootStack: MutableList<NavKey>
        var presented: LocalHistoryPageModel.State? = null
        composeRule.setContent {
            rootStack = rememberNavBackStack(AppNavKey.Main)
            NavDisplay(
                backStack = rootStack,
                modifier = Modifier.size(320.dp, 480.dp),
                onBack = { rootStack.removeLastOrNull() },
                transitionSpec = pushTransition,
                popTransitionSpec = popTransition,
                entryProvider = entryProvider {
                    entry<AppNavKey.Main> {
                        // Match the active tab's lifecycle owner and nested NavDisplay.
                        val tabOwner = rememberLifecycleOwner(maxLifecycle = Lifecycle.State.RESUMED)
                        CompositionLocalProvider(LocalLifecycleOwner provides tabOwner) {
                            val tabStack = rememberNavBackStack(AppNavKey.HistoryTab)
                            NavDisplay(
                                backStack = tabStack,
                                onBack = {},
                                entryProvider = entryProvider {
                                    entry<AppNavKey.HistoryTab> {
                                        val state by rememberLocalHistoryPresentationState(source)
                                        SideEffect { presented = state }
                                        Box(Modifier.fillMaxSize())
                                    }
                                }
                            )
                        }
                    }
                    entry<AppNavKey.Reader> {
                        // The reader persists the timestamp as soon as it opens.
                        LaunchedEffect(Unit) { source.value = promoted }
                        Box(Modifier.fillMaxSize())
                    }
                }
            )
        }
        composeRule.waitForIdle()
        assertEquals(original, presented)
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            rootStack.add(AppNavKey.Reader(ReaderRoute("second", "/forum/second/chapter.html")))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(120)
        assertEquals("Saving the reading timestamp must not wait for navigation", promoted, source.value)
        assertEquals("The outgoing history must retain its pre-click order", original, presented)
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.runOnIdle { rootStack.removeLastOrNull() }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals("Returning to history must show the latest reading order", promoted, presented)
    }

    private fun activity(id: String, timestamp: Long) = LocalReadingActivity(
        activityId = id, novelId = id, novelName = id, novelUrl = "/detail/$id.html",
        chapterUrl = "/forum/$id/chapter.html", chapterName = "chapter",
        chapterIndex = 0, totalChapters = 2, chapterProgress = 0.5f,
        startedAt = timestamp, lastReadAt = timestamp, durationMs = 0L
    )
}
