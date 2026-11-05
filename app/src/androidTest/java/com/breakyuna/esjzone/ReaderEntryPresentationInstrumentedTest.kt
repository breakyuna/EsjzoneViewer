package com.breakyuna.esjzone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.reader.ReaderEntryPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderEntryPresentationInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun failedRestorationCanRetryWithoutExposingOrActivatingHiddenBody() {
        val failed = mutableStateOf(true)
        var retries = 0
        var bodyTaps = 0
        composeRule.setContent {
            MaterialTheme {
                ReaderEntryPresentation(false, Color.Black, null, 0f, "Loading",
                    onRetryPosition = if (failed.value) ({ retries++; failed.value = false }) else null) {
                    Box(Modifier.fillMaxSize().clickable { bodyTaps++ }) { Text("Unrestored body") }
                }
            }
        }
        composeRule.onNodeWithTag("reader-position-retry").assertIsDisplayed()
            .performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, bodyTaps)
        }
        composeRule.onNodeWithTag("reader-position-retry").assertDoesNotExist()
        composeRule.onNodeWithText("Unrestored body").assertDoesNotExist()
        composeRule.onNodeWithTag("reader-entry-loading").assertIsDisplayed()
    }

    @Test
    fun hiddenBodyMeasuresButCannotFlashOrReceiveInputBeforeReveal() {
        val visible = mutableStateOf(false)
        val background = mutableStateOf(Color.Red)
        val paragraph = mutableStateOf("Chapter opening")
        var measured = IntSize.Zero
        var taps = 0
        composeRule.setContent {
            MaterialTheme {
                ReaderEntryPresentation(visible.value, background.value, null, 0f, "Loading") {
                    Box(Modifier.fillMaxSize().background(Color.Black)
                        .onSizeChanged { measured = it }.clickable { taps++ }) {
                        Text(paragraph.value)
                    }
                }
            }
        }
        composeRule.runOnIdle { assertTrue(measured.width > 0 && measured.height > 0) }
        composeRule.onNodeWithText("Chapter opening").assertDoesNotExist()
        assertEquals(Color.Red, composeRule.onRoot().captureToImage().toPixelMap()[4, 4])
        composeRule.onNodeWithTag("reader-entry-loading").performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(0, taps)
            // Simulate image completion and coarse/exact restoration behind the cover.
            background.value = Color.Blue
            paragraph.value = "Saved reading position"
        }
        composeRule.onNodeWithText("Saved reading position").assertDoesNotExist()
        assertEquals(Color.Red, composeRule.onRoot().captureToImage().toPixelMap()[4, 4])
        composeRule.runOnIdle { visible.value = true }
        composeRule.onNodeWithTag("reader-entry-loading").assertDoesNotExist()
        composeRule.onNodeWithText("Saved reading position").assertIsDisplayed()
        composeRule.onRoot().performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, taps) }
    }
}
