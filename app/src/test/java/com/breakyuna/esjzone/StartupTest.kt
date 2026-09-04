package com.breakyuna.esjzone

import androidx.activity.compose.setContent
import cafe.adriel.voyager.navigator.Navigator
import com.breakyuna.esjzone.ui.screen.LoadingScreen
import com.breakyuna.esjzone.ui.screen.LoginScreen
import com.breakyuna.esjzone.update.ReleaseVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupTest {
    @Test
    fun testBetaVersionComparison() {
        // Stable release of same version is newer than installed beta
        assertTrue(ReleaseVersion.isNewerStableRelease("0.1.0", "beta-0.1.0"))
        assertTrue(ReleaseVersion.isNewerStableRelease("v0.1.0", "beta-0.1.0"))
        // Higher stable release is newer
        assertTrue(ReleaseVersion.isNewerStableRelease("0.2.0", "beta-0.1.0"))
        // Lower stable release is not newer
        assertFalse(ReleaseVersion.isNewerStableRelease("0.0.9", "beta-0.1.0"))
        // Beta tag is not treated as newer stable release
        assertFalse(ReleaseVersion.isNewerStableRelease("beta-0.1.0", "beta-0.1.0"))
        assertFalse(ReleaseVersion.isNewerStableRelease("0.1.0-beta.1", "beta-0.1.0"))
        // Same stable release is not newer
        assertFalse(ReleaseVersion.isNewerStableRelease("0.1.0", "0.1.0"))
    }

    @Test
    fun testMainActivityLaunch() {
        println("Launching MainActivity via Robolectric...")
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        println("MainActivity launched successfully!")
    }

    @Test
    fun testLoadingScreenRender() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        activity.setContent {
            Navigator(LoadingScreen())
        }
    }

    @Test
    fun testLoginScreenRender() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        activity.setContent {
            Navigator(LoginScreen)
        }
    }
}

