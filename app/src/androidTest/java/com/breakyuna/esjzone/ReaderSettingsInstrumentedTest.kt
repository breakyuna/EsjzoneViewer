package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.designsystem.globalStringResource
import com.breakyuna.esjzone.ui.page.ReaderMoreSettingsContent
import com.breakyuna.esjzone.ui.page.ReaderSettingsSheet
import com.breakyuna.esjzone.ui.reader.ReaderBackground
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun firstLineIndentIsSharedBetweenReaderAndProfileSettings() {
        var settings by mutableStateOf(ReaderSettings())
        var showReaderSettings by mutableStateOf(true)
        var label = ""
        var moreLabel = ""
        var options = emptyList<String>()
        composeRule.setContent {
            label = globalStringResource(R.string.reader_first_line_indent)
            moreLabel = globalStringResource(R.string.reader_tab_controls)
            options = (0..2).map { globalStringResource(R.string.reader_indent_chars, it) }
            MaterialTheme {
                if (showReaderSettings) {
                    ReaderSettingsSheet(visible = true, settings = settings,
                        onSettingsChange = { settings = it }, onDismiss = {})
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ReaderMoreSettingsContent(settings, onSettingsChange = { settings = it })
                    }
                }
            }
        }
        composeRule.onNodeWithText(moreLabel).performClick()
        composeRule.onNodeWithText(label).performScrollTo().performClick()
        composeRule.onNodeWithText(options[1]).performClick()
        composeRule.runOnIdle { assertEquals(1, settings.firstLineIndentChars) }

        composeRule.runOnIdle { showReaderSettings = false }
        composeRule.onNodeWithText(label).performScrollTo().performClick()
        composeRule.onAllNodesWithText(options[1]).filter(isSelected()).assertCountEquals(1)
        composeRule.onNodeWithText(options[2]).performClick()
        composeRule.runOnIdle { assertEquals(2, settings.firstLineIndentChars) }

        composeRule.runOnIdle { showReaderSettings = true }
        composeRule.onNodeWithText(moreLabel).performClick()
        composeRule.onNodeWithText(label).performScrollTo().performClick()
        composeRule.onNodeWithText(options[0]).performClick()
        composeRule.runOnIdle { assertEquals(0, settings.firstLineIndentChars) }
    }

    @Test fun layoutParameterCanBeSelectedDirectlyAndAdjusted() {
        val initial = ReaderSettings(horizontalPaddingDp = 24f)
        var settings by mutableStateOf(initial)
        var fontSizeLabel = ""
        var paddingLabel = ""
        var increaseLabel = ""
        var decreaseLabel = ""
        composeRule.setContent {
            fontSizeLabel = globalStringResource(R.string.reader_font_size)
            paddingLabel = globalStringResource(R.string.reader_horizontal_padding)
            increaseLabel = globalStringResource(R.string.reader_setting_increase)
            decreaseLabel = globalStringResource(R.string.reader_setting_decrease)
            MaterialTheme {
                Box(Modifier.width(280.dp)) {
                    ReaderSettingsSheet(visible = true, settings = settings,
                        onSettingsChange = { settings = it }, onDismiss = {})
                }
            }
        }
        composeRule.onNodeWithText(fontSizeLabel).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(paddingLabel).assertIsNotDisplayed()
        composeRule.onNodeWithText(paddingLabel).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(increaseLabel).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(initial.copy(horizontalPaddingDp = 28f), settings) }
        composeRule.onNodeWithContentDescription(decreaseLabel).performClick()
        composeRule.runOnIdle { assertEquals(initial, settings) }
    }

    @Test fun selectingBackgroundSwatchesUpdatesTheReaderBackground() {
        var settings by mutableStateOf(ReaderSettings())
        var followsSystem by mutableStateOf(false)
        var systemLabel = ""
        var paperLabel = ""
        composeRule.setContent {
            systemLabel = globalStringResource(R.string.reader_background_system)
            paperLabel = globalStringResource(R.string.reader_background_paper)
            MaterialTheme {
                ReaderSettingsSheet(visible = true, settings = settings,
                    onSettingsChange = { settings = it }, onDismiss = {},
                    systemThemeSelected = followsSystem, onFollowSystemTheme = { followsSystem = true })
            }
        }
        val system = composeRule.onNodeWithText(systemLabel)
        val paper = composeRule.onNodeWithContentDescription(paperLabel)
        system.assertIsNotSelected()
        paper.performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(ReaderBackground.PAPER, settings.background) }
        system.performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(true, followsSystem)
            assertEquals(ReaderBackground.SYSTEM, settings.background)
        }
    }

    @Test fun customImageOpensConfigurationUntilAnImageIsAvailable() {
        var settings by mutableStateOf(ReaderSettings())
        var available by mutableStateOf<Boolean?>(null)
        var opened = 0
        var label = ""
        composeRule.setContent {
            label = globalStringResource(R.string.reader_background_image)
            MaterialTheme {
                ReaderSettingsSheet(visible = true, settings = settings,
                    onSettingsChange = { settings = it }, onDismiss = {},
                    customImageAvailable = available, onConfigureImage = { opened++ })
            }
        }
        val option = composeRule.onNodeWithContentDescription(label)
        option.performScrollTo().assertIsNotEnabled()
        composeRule.runOnIdle { available = false }
        option.performClick()
        composeRule.runOnIdle {
            assertEquals(1, opened)
            assertEquals(ReaderBackground.SYSTEM, settings.background)
            available = true
        }
        option.performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(1, opened)
            assertEquals(ReaderBackground.CUSTOM_IMAGE, settings.background)
        }
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
