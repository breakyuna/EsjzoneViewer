package com.breakyuna.esjzone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.page.NovelDetailLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NovelDetailFeedbackInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun cleanupStaysAboveComposerWhenBottomAreaResizes() {
        val snackbar = SnackbarHostState()
        var bottomHeight by mutableStateOf(64.dp)
        var composerClicks = 0
        composeRule.setContent {
            MaterialTheme {
                LaunchedEffect(snackbar) {
                    snackbar.showSnackbar("Clear local content?", actionLabel = "Clear",
                        withDismissAction = true, duration = SnackbarDuration.Indefinite)
                }
                Column(Modifier.size(320.dp, 480.dp)) {
                    NovelDetailLayout(
                        hero = { Text("Book") },
                        modifier = Modifier.weight(1f),
                        snackbarHost = { SnackbarHost(snackbar, Modifier.testTag("cleanup")) }
                    ) {
                        item { Text("Chapters") }
                    }
                    Box(Modifier.fillMaxWidth().height(bottomHeight).testTag("composer")
                        .clickable { composerClicks++ })
                }
            }
        }

        listOf(64.dp, 160.dp).forEach { height ->
            composeRule.runOnIdle { bottomHeight = height }
            composeRule.onNodeWithText("Clear local content?").assertIsDisplayed()
            val feedback = composeRule.onNodeWithTag("cleanup").fetchSemanticsNode().boundsInRoot
            val composer = composeRule.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot
            assertTrue("Cleanup must leave the composer uncovered", feedback.bottom <= composer.top + 1f)
            composeRule.onNodeWithTag("composer").performTouchInput { click(center) }
        }
        composeRule.runOnIdle { assertEquals(2, composerClicks) }
    }
}
