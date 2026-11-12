package com.breakyuna.esjzone

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.tab.HomeReselectionEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeReselectionInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun reselectRefreshesAndReturnsToTopWithoutReplayingOnRemount() {
        var request by mutableIntStateOf(0)
        var mounted by mutableStateOf(true)
        val listState = LazyListState(firstVisibleItemIndex = 20)
        var refreshes = 0
        composeRule.setContent {
            if (mounted) {
                HomeReselectionEffect(request, listState) { refreshes++ }
                LazyColumn(state = listState, modifier = Modifier.height(200.dp)) {
                    items((0..99).toList()) { Text("Item $it", modifier = Modifier.height(40.dp)) }
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals(20, listState.firstVisibleItemIndex)
            assertEquals(0, refreshes)
            request++
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
            assertEquals(1, refreshes)
            mounted = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { mounted = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, refreshes)
            request++
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(2, refreshes) }
    }

    @Test fun reselectRefreshesAfterTheScrollAnimation() {
        var request by mutableIntStateOf(0)
        val listState = LazyListState(firstVisibleItemIndex = 20)
        var refreshes = 0
        composeRule.setContent {
            HomeReselectionEffect(request, listState) { refreshes++ }
            LazyColumn(state = listState, modifier = Modifier.height(200.dp)) {
                items((0..99).toList()) { Text("Item $it", modifier = Modifier.height(40.dp)) }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { request++ }
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.runOnIdle {
                assertEquals(0, refreshes)
                assertTrue(listState.firstVisibleItemIndex > 0)
            }
            composeRule.mainClock.advanceTimeBy(5_000)
            composeRule.runOnIdle {
                assertEquals(0, listState.firstVisibleItemIndex)
                assertEquals(0, listState.firstVisibleItemScrollOffset)
                assertEquals(1, refreshes)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }
}
