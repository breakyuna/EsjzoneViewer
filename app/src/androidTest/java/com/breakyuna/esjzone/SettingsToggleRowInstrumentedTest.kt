package com.breakyuna.esjzone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.page.SettingsToggleRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsToggleRowInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun labelAndSwitchBothToggleExactlyOnce() {
        var checked by mutableStateOf(false)
        var changes = 0
        composeRule.setContent {
            MaterialTheme {
                SettingsToggleRow(title = "Setting", checked = checked) {
                    checked = it
                    changes++
                }
            }
        }

        val row = composeRule.onNode(isToggleable())
        row.assertIsOff()
        composeRule.onNodeWithText("Setting").performClick()
        row.assertIsOn()
        composeRule.runOnIdle { assertEquals(1, changes) }

        // The switch is on the trailing edge; a physical tap must reach the row
        // without a second callback from the decorative Switch.
        row.performTouchInput { click(Offset(right - 32f, centerY)) }
        row.assertIsOff()
        composeRule.runOnIdle { assertEquals(2, changes) }
    }
}
