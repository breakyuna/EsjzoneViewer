package com.breakyuna.esjzone

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.page.scrollToPreviousBoundaryPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPreviousBoundaryInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    private fun verifyPreviousPage(blockCount: Int, blockHeight: Int) {
        lateinit var state: LazyListState
        lateinit var scope: CoroutineScope
        val finished = AtomicBoolean(false)
        val previous = (0 until blockCount).map { "previous:$it" }
        composeRule.setContent {
            state = rememberLazyListState()
            scope = rememberCoroutineScope()
            LazyColumn(state = state, modifier = Modifier.size(300.dp, 200.dp)) {
                item(key = "previous-heading") { Text("Previous", Modifier.height(40.dp)) }
                items(previous, key = { it }) { Text(it, Modifier.height(blockHeight.dp)) }
                item(key = "current-heading") { Text("Current", Modifier.height(40.dp)) }
                item(key = "current-body") { Text("Body", Modifier.height(400.dp)) }
            }
        }
        // The list is at the newly prepended chapter heading, with the old
        // chapter anchor far outside the visible range.
        composeRule.runOnIdle {
            scope.launch {
                scrollToPreviousBoundaryPage(state, blockCount + 1)
                finished.set(true)
            }
        }
        composeRule.waitUntil(5_000) { finished.get() }
        composeRule.runOnIdle {
            assertEquals("previous:${blockCount - 1}", state.layoutInfo.visibleItemsInfo.first().key)
            if (blockHeight > 200) assertTrue(state.firstVisibleItemScrollOffset > 0)
        }
    }

    @Test fun manyBlocksLandNearChapterEnd() = verifyPreviousPage(50, 200)

    @Test fun longFinalBlockLandsInsideBlock() = verifyPreviousPage(1, 1200)
}
