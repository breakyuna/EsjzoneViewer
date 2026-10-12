package com.breakyuna.esjzone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.discovery.LibrarySource
import com.breakyuna.esjzone.ui.discovery.SourceSelector
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceSelectorInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionWaitsForMovementBeforeReplacingHeader() {
        val selected = mutableStateOf<LibrarySource?>(LibrarySource.ESJZONE)
        val callbacks = mutableListOf<LibrarySource?>()
        compose.setContent {
            MaterialTheme {
                SourceSelector(selected.value, { callbacks += it; selected.value = it })
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Wenku").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertEquals(emptyList<LibrarySource?>(), callbacks) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(listOf(LibrarySource.WENKU8), callbacks) }
        compose.onNodeWithText("Wenku").assertIsSelected()

        // A selected segment must not toggle the bookshelf back to the other source.
        compose.onNodeWithText("Wenku").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(1, callbacks.size) }
    }

    @Test fun draggingSelectsBothSourcesAndDisabledControlsStayDisabled() {
        val selected = mutableStateOf<LibrarySource?>(null)
        val enabled = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                SourceSelector(selected.value, { selected.value = it },
                    modifier = Modifier.testTag("source"), enabled = enabled.value)
            }
        }
        compose.onNodeWithTag("source").performTouchInput { swipeRight() }
        compose.onNodeWithText("Wenku").assertIsSelected()
        compose.onNodeWithTag("source").performTouchInput { swipeLeft() }
        compose.onNodeWithText("ESJ").assertIsSelected()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithText("Wenku").assertIsNotEnabled()
        compose.onNodeWithTag("source").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(LibrarySource.ESJZONE, selected.value) }
    }
}
