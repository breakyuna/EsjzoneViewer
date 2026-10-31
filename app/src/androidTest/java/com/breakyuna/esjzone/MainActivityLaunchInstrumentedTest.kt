package com.breakyuna.esjzone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesMainActivityOnDevice() {
        val scenario = composeRule.activityRule.scenario
        // A composed splash alone is not a successful launch: startup must finish.
        awaitStartup()
        awaitDestination()
        scenario.onActivity { activity ->
            assertFalse(activity.isFinishing)
            assertNotNull(activity.window)
            assertNotNull(activity.window.decorView)
            val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            assertTrue((0 until content.childCount).any { content.getChildAt(it) is ComposeView })
        }
        scenario.recreate()
        awaitStartup()
        awaitDestination()
    }

    private fun awaitStartup() = runBlocking {
        val state = withTimeout(30_000L) {
            MainActivity.startup.first { it !is StartupState.Starting }
        }
        assertEquals(StartupState.Ready, state)
    }

    private fun awaitDestination() {
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithTag("app-navigation").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("session-loading").fetchSemanticsNodes().isEmpty()
        }
    }
}
