package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.designsystem.globalStringResource
import com.breakyuna.esjzone.ui.page.ReaderToolbarSettings
import com.breakyuna.esjzone.ui.page.label
import com.breakyuna.esjzone.ui.reader.ReaderTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderToolbarSettingsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun movingSelectedToolChangesBothStoredOrderAndVisibleOrder() {
        var tools by mutableStateOf(listOf(ReaderTool.DARK_MODE, ReaderTool.CONTENTS))
        var darkLabel = ""
        var contentsLabel = ""
        var moveBeforeLabel = ""
        composeRule.setContent {
            darkLabel = globalStringResource(ReaderTool.DARK_MODE.label())
            contentsLabel = globalStringResource(ReaderTool.CONTENTS.label())
            moveBeforeLabel = globalStringResource(R.string.reader_tool_move_before)
            MaterialTheme {
                Column { ReaderToolbarSettings(tools) { tools = it } }
            }
        }
        assertTrue(composeRule.onNodeWithText(darkLabel).fetchSemanticsNode().boundsInRoot.top <
            composeRule.onNodeWithText(contentsLabel).fetchSemanticsNode().boundsInRoot.top)
        composeRule.onAllNodesWithContentDescription(moveBeforeLabel).filter(isEnabled()).onFirst().performClick()
        composeRule.runOnIdle { assertEquals(listOf(ReaderTool.CONTENTS, ReaderTool.DARK_MODE), tools) }
        assertTrue(composeRule.onNodeWithText(contentsLabel).fetchSemanticsNode().boundsInRoot.top <
            composeRule.onNodeWithText(darkLabel).fetchSemanticsNode().boundsInRoot.top)
    }
}
