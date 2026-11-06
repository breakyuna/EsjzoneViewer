package com.breakyuna.esjzone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.AppTabId
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.TabRootLayout
import com.breakyuna.esjzone.ui.navigation.TabSwitchLayers
import com.breakyuna.esjzone.ui.navigation.containerContentTransform
import com.breakyuna.esjzone.ui.navigation.containerTransformMetadata
import com.breakyuna.esjzone.ui.navigation.popTransition
import com.breakyuna.esjzone.ui.navigation.pushTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TabRootNavigationInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun bottomBarAndRootSizeSurviveSlideEntryAndReturn() = verifyTransition(false, false)

    @Test fun bottomBarAndRootSizeSurviveContainerEntryAndReturn() = verifyTransition(false, true)

    @Test fun rootRailAndRootSizeSurviveSlideEntryAndReturn() = verifyTransition(true, false)

    @Test fun bottomBarStaysOpaqueAndStationaryDuringRapidTabChanges() = verifyTabChanges(false)

    @Test fun rootRailStaysOpaqueAndStationaryDuringRapidTabChanges() = verifyTabChanges(true)

    private fun verifyTabChanges(sideNavigation: Boolean) {
        val selected = mutableStateOf(AppTabId.HOME)
        composeRule.setContent {
            TabSwitchLayers(selected.value, AppTabId.entries, Modifier.fillMaxSize()) { _, active, motion ->
                TabRootLayout(
                    showNavigation = true,
                    sideNavigation = sideNavigation,
                    contentModifier = motion,
                    navigationVisible = active,
                    navigation = {
                        Box(Modifier.then(
                            if (sideNavigation) Modifier.width(80.dp).fillMaxHeight()
                            else Modifier.height(68.dp).fillMaxWidth()
                        ).background(Color.Blue).testTag("tab-navigation")) {
                            // An asymmetric edge exposes horizontal drift and double drawing.
                            Box(Modifier.width(8.dp).fillMaxHeight().background(Color.Red))
                        }
                    }
                ) {
                    Box(Modifier.fillMaxSize().background(Color.White))
                }
            }
        }
        composeRule.waitForIdle()
        val bounds = composeRule.onNodeWithTag("tab-navigation").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false
        try {
            for (tab in listOf(AppTabId.BOOKSHELF, AppTabId.HISTORY, AppTabId.PROFILE, AppTabId.HOME)) {
                composeRule.runOnIdle { selected.value = tab }
                composeRule.mainClock.advanceTimeBy(64)
                assertEquals(bounds, composeRule.onNodeWithTag("tab-navigation").fetchSemanticsNode().boundsInRoot)
                val pixels = composeRule.onRoot().captureToImage().toPixelMap()
                assertEquals(Color.Red, pixels[bounds.left.toInt() + 2, bounds.top.toInt() + 2])
                assertEquals(Color.Blue, pixels[bounds.right.toInt() - 2, bounds.bottom.toInt() - 2])
            }
            composeRule.mainClock.advanceTimeBy(600)
            assertEquals(bounds, composeRule.onNodeWithTag("tab-navigation").fetchSemanticsNode().boundsInRoot)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun verifyTransition(sideNavigation: Boolean, container: Boolean) {
        val stack = mutableStateListOf<NavKey>(AppNavKey.HomeTab)
        val child = AppNavKey.Legacy(LegacyRoute.Static("navigation-test-child"))
        var hostSize = IntSize.Zero
        var rootSize = IntSize.Zero
        composeRule.setContent {
            NavDisplay(
                backStack = stack,
                modifier = Modifier.fillMaxSize().onSizeChanged { hostSize = it },
                onBack = { stack.removeLastOrNull() },
                transitionSpec = { if (container) containerContentTransform() else pushTransition() },
                popTransitionSpec = { if (container) containerContentTransform(pop = true) else popTransition() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider = entryProvider {
                    entry<AppNavKey.HomeTab>(metadata = ::containerTransformMetadata) {
                        TabRootLayout(
                            showNavigation = true,
                            sideNavigation = sideNavigation,
                            navigation = {
                                Box(Modifier.then(
                                    if (sideNavigation) Modifier.width(80.dp).fillMaxHeight()
                                    else Modifier.height(68.dp).fillMaxWidth()
                                ).background(Color.Blue).testTag("root-navigation"))
                            }
                        ) {
                            Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }.background(Color.Red))
                        }
                    }
                    entry<AppNavKey.Legacy>(metadata = ::containerTransformMetadata) {
                        Box(Modifier.fillMaxSize().background(Color.White))
                    }
                }
            )
        }
        composeRule.waitForIdle()
        val initialHost = hostSize
        val initialRoot = rootSize
        assertTrue(initialRoot.width > 0 && initialRoot.height > 0)
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { stack.add(child) }
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.onNodeWithTag("root-navigation").assertExists()
        composeRule.runOnIdle {
            assertEquals(initialHost, hostSize)
            assertEquals(initialRoot, rootSize)
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("root-navigation").assertDoesNotExist()
        composeRule.runOnIdle { stack.removeLastOrNull() }
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.onNodeWithTag("root-navigation").assertExists()
        composeRule.runOnIdle {
            assertEquals(initialHost, hostSize)
            assertEquals(initialRoot, rootSize)
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("root-navigation").assertExists()
        composeRule.mainClock.autoAdvance = true
    }
}
