package com.breakyuna.esjzone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.designsystem.globalStringResource
import com.breakyuna.esjzone.ui.page.ReaderSettingsSheet
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun layoutParameterCanBeSelectedDirectlyAndAdjusted() {
        val initial = ReaderSettings(horizontalPaddingDp = 24f)
        var settings by mutableStateOf(initial)
        var paddingLabel = ""
        var increaseLabel = ""
        var decreaseLabel = ""
        composeRule.setContent {
            paddingLabel = globalStringResource(R.string.reader_horizontal_padding)
            increaseLabel = globalStringResource(R.string.reader_setting_increase)
            decreaseLabel = globalStringResource(R.string.reader_setting_decrease)
            MaterialTheme {
                ReaderSettingsSheet(visible = true, settings = settings,
                    onSettingsChange = { settings = it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithText(paddingLabel).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(increaseLabel).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(initial.copy(horizontalPaddingDp = 28f), settings) }
        composeRule.onNodeWithContentDescription(decreaseLabel).performClick()
        composeRule.runOnIdle { assertEquals(initial, settings) }
    }

    @Test fun moreTabCanEnableAndDisableKeepScreenOn() {
        val initial = ReaderSettings()
        var settings by mutableStateOf(initial)
        var moreLabel = ""
        var keepScreenOnLabel = ""
        composeRule.setContent {
            moreLabel = globalStringResource(R.string.reader_tab_controls)
            keepScreenOnLabel = globalStringResource(R.string.reader_keep_screen_on)
            MaterialTheme {
                ReaderSettingsSheet(visible = true, settings = settings,
                    onSettingsChange = { settings = it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithText(moreLabel).performClick()
        val toggle = composeRule.onNode(isToggleable() and hasAnySibling(hasText(keepScreenOnLabel)))
        toggle.performScrollTo().assertIsOff().performClick()
        composeRule.runOnIdle { assertEquals(initial.copy(keepScreenOn = true), settings) }
        toggle.assertIsOn().performClick()
        composeRule.runOnIdle { assertEquals(initial, settings) }
        toggle.assertIsOff()
    }

    @Test fun moreTabCanEnableAndDisableLongPressUnderlining() {
        val initial = ReaderSettings(tapPagingEnabled = false)
        var settings by mutableStateOf(initial)
        var moreLabel = ""
        var underlineLabel = ""
        composeRule.setContent {
            moreLabel = globalStringResource(R.string.reader_tab_controls)
            underlineLabel = globalStringResource(R.string.reader_long_press_underline)
            MaterialTheme {
                ReaderSettingsSheet(visible = true, settings = settings,
                    onSettingsChange = { settings = it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithText(moreLabel).performClick()
        val toggle = composeRule.onNode(isToggleable() and hasAnySibling(hasText(underlineLabel)))
        toggle.performScrollTo().assertIsOff().performClick()
        composeRule.runOnIdle { assertEquals(initial.copy(longPressUnderline = true), settings) }
        toggle.assertIsOn().performClick()
        composeRule.runOnIdle { assertEquals(initial, settings) }
        toggle.assertIsOff()
    }
}
