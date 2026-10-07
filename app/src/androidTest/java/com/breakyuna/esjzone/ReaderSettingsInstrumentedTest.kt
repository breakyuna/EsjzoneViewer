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
