package com.breakyuna.esjzone

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.designsystem.AppViewportSize
import com.breakyuna.esjzone.ui.designsystem.LocalAppViewport
import com.breakyuna.esjzone.ui.page.NovelDetailLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WideNovelDetailInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun scrollingWideChaptersLeavesTheOverviewAndReadActionAvailable() {
        var readClicks = 0
        var heroScrolledAway = false
        composeRule.setContent {
            // Fit tablet geometry inside a phone test surface without relying on device rotation.
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
                fontScale = 1f
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalAppViewport provides AppViewportSize(1000f, 600f),
                LocalDensity provides Density(0.25f, 1f)
            ) {
                MaterialTheme {
                    Column(Modifier.requiredSize(1000.dp, 600.dp)) {
                        NovelDetailLayout(
                            hero = { Text("Book overview") },
                            onHeroScrolledAway = { heroScrolledAway = it },
                            modifier = Modifier.weight(1f),
                            overview = {
                                item { Button(onClick = { readClicks++ }) { Text("Read book") } }
                                items(80) { Text("Overview $it") }
                            }
                        ) {
                            items(80) { Text("Chapter $it") }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("detail-content-pane").performScrollToNode(hasText("Chapter 60"))
        composeRule.onNodeWithText("Chapter 60").assertIsDisplayed()
        composeRule.onNodeWithText("Book overview").assertIsDisplayed()
        composeRule.onNodeWithText("Read book").performClick()
        composeRule.runOnIdle {
            assertEquals(1, readClicks)
            assertEquals(false, heroScrolledAway)
        }
        composeRule.onNodeWithTag("detail-overview-pane").performScrollToNode(hasText("Overview 60"))
        composeRule.runOnIdle { assertEquals(true, heroScrolledAway) }
        composeRule.onNodeWithTag("detail-overview-pane").performScrollToNode(hasText("Book overview"))
        composeRule.runOnIdle { assertEquals(false, heroScrolledAway) }
    }

    @Test
    fun compactOverviewAndChaptersRemainInTheSameScrollArea() {
        var heroScrolledAway = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppViewport provides AppViewportSize(390f, 844f),
                LocalDensity provides Density(0.25f, 1f)
            ) {
                MaterialTheme {
                    Column(Modifier.requiredSize(390.dp, 844.dp)) {
                        NovelDetailLayout(
                            hero = { Text("Book overview") },
                            onHeroScrolledAway = { heroScrolledAway = it },
                            modifier = Modifier.weight(1f),
                            overview = { item { Text("Book actions") } }
                        ) {
                            items(80) { Text("Chapter $it") }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("detail-overview-pane").assertDoesNotExist()
        composeRule.onNodeWithTag("detail-content-pane").assertDoesNotExist()
        composeRule.onNodeWithText("Book actions").assertIsDisplayed()
        composeRule.onNodeWithTag("detail-compact-pane").performScrollToNode(hasText("Chapter 60"))
        composeRule.onNodeWithText("Chapter 60").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(true, heroScrolledAway) }
        composeRule.onNodeWithTag("detail-compact-pane").performScrollToNode(hasText("Book overview"))
        composeRule.runOnIdle { assertEquals(false, heroScrolledAway) }
    }
}
