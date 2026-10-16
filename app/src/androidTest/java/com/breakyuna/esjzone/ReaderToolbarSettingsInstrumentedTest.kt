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

    @Test fun togglingToolsKeepsRowsInPlaceAndSelectedOrderMatchesRows() {
        var tools by mutableStateOf(listOf(ReaderTool.CONTENTS))
        val order = ReaderTool.entries.toList()
        var darkLabel = ""
        composeRule.setContent {
            darkLabel = globalStringResource(ReaderTool.DARK_MODE.label())
            MaterialTheme {
                Column { ReaderToolbarSettings(tools, order, {}, { tools = it }) }
            }
        }
        val originalTop = composeRule.onNodeWithText(darkLabel).fetchSemanticsNode().boundsInRoot.top
        composeRule.onAllNodes(isToggleable())[order.indexOf(ReaderTool.DARK_MODE)].performClick()
        composeRule.runOnIdle { assertEquals(listOf(ReaderTool.CONTENTS, ReaderTool.DARK_MODE), tools) }
        assertEquals(originalTop, composeRule.onNodeWithText(darkLabel).fetchSemanticsNode().boundsInRoot.top)
        composeRule.onAllNodes(isToggleable())[order.indexOf(ReaderTool.CONTENTS)].performClick()
        composeRule.runOnIdle { assertEquals(listOf(ReaderTool.DARK_MODE), tools) }
        assertEquals(originalTop, composeRule.onNodeWithText(darkLabel).fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun longPressingHandleChangesStoredOrderAndVisibleOrder() {
        var tools by mutableStateOf(listOf(ReaderTool.DARK_MODE, ReaderTool.CONTENTS))
        var order by mutableStateOf(ReaderTool.decodeOrder(null, tools))
        var darkLabel = ""
        var contentsLabel = ""
        var dragLabel = ""
        composeRule.setContent {
            darkLabel = globalStringResource(ReaderTool.DARK_MODE.label())
            contentsLabel = globalStringResource(ReaderTool.CONTENTS.label())
            dragLabel = globalStringResource(R.string.reader_tool_drag)
            MaterialTheme {
                Column {
                    ReaderToolbarSettings(tools, order, { moved ->
                        order = moved
                        tools = moved.filter { it in tools }
                    }, { tools = it })
                }
            }
        }
        val rowHeight = composeRule.onAllNodesWithContentDescription(dragLabel)[0]
            .fetchSemanticsNode().boundsInRoot.height
        composeRule.onAllNodesWithContentDescription(dragLabel)[0].performTouchInput {
            down(center)
            advanceEventTime(600)
            moveTo(center.copy(y = center.y + rowHeight), delayMillis = 200)
            up()
        }
        composeRule.runOnIdle { assertEquals(listOf(ReaderTool.CONTENTS, ReaderTool.DARK_MODE), tools) }
        assertTrue(composeRule.onNodeWithText(contentsLabel).fetchSemanticsNode().boundsInRoot.top <
            composeRule.onNodeWithText(darkLabel).fetchSemanticsNode().boundsInRoot.top)
    }
}
