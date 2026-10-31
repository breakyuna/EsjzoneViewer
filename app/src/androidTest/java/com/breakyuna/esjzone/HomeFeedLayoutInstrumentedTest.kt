package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel
import com.breakyuna.esjzone.ui.designsystem.AppTheme
import com.breakyuna.esjzone.ui.tab.HomeTab
import com.breakyuna.esjzone.ui.tab.HomeTabModel
import com.breakyuna.esjzone.ui.tab.HomeRecommendationPool
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeFeedLayoutInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun pullLoadingScrollsWithTheFooterWhileTheRequestRemainsPending() {
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val authorization = Authorization("", "", "example.invalid")
        val cached = HomeData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            weeklyPopular = listOf(WeeklyPopularNovel(name = "Cached popular fixture",
                url = "https://example.invalid/detail/123.html", coverResolved = true)))
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var model: HomeTabModel
        try {
            composeRule.runOnUiThread {
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        HomeTabModel(authorization) { _, progress ->
                            progress(cached)
                            awaitCancellation()
                        } as T
                }
                model = ViewModelProvider(owner, factory)[HomeTabModel::class.java]
                // Hold the real recommendation job without contacting the website.
                HomeTabModel::class.java.getDeclaredField("randomPool").apply {
                    isAccessible = true
                }.set(model, HomeRecommendationPool {
                    started.countDown()
                    release.await()
                    throw IOException("Fixture request finished")
                })
                model.getHomeData()
            }
            composeRule.setContent {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides owner,
                    LocalAuthorization provides authorization
                ) {
                    AppTheme {
                        Box(Modifier.size(360.dp, 480.dp)) { HomeTab.Content() }
                    }
                }
            }
            val feed = composeRule.onNodeWithTag("home-feed")
            repeat(12) {
                if (started.count != 0L) feed.performTouchInput { swipeUp() }
            }
            composeRule.waitUntil(timeoutMillis = 5_000L) { started.count == 0L }
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithTag("home-random-initial-loading").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("home-random-initial-loading").assertIsDisplayed()
            feed.performScrollToIndex(0)
            composeRule.onNodeWithTag("home-random-initial-loading").assertDoesNotExist()
            composeRule.runOnIdle { assertTrue(model.randomRecommendations.value.isLoading) }
            feed.performScrollToNode(hasTestTag("home-random-initial-loading"))
            composeRule.onNodeWithTag("home-random-initial-loading").assertIsDisplayed()
            composeRule.runOnIdle { assertTrue(model.randomRecommendations.value.isLoading) }
        } finally {
            release.countDown()
            composeRule.runOnUiThread { owner.viewModelStore.clear() }
        }
    }

    @Test fun cachedHomeFeedMeasuresAndScrollsWhilePopularVisibilityChanges() {
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val authorization = Authorization("", "", "example.invalid")
        val cached = HomeData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            weeklyPopular = listOf(WeeklyPopularNovel(name = "Cached popular fixture",
                url = "https://example.invalid/detail/123.html", coverResolved = true)))
        try {
            composeRule.runOnUiThread {
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        HomeTabModel(authorization) { _, progress ->
                            progress(cached)
                            // Keep the cached result visible without network enrichment.
                            awaitCancellation()
                        } as T
                }
                ViewModelProvider(owner, factory)[HomeTabModel::class.java].getHomeData()
            }
            composeRule.setContent {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides owner,
                    LocalAuthorization provides authorization
                ) {
                    AppTheme { HomeTab.Content() }
                }
            }
            composeRule.onNodeWithTag("home-feed").assertIsDisplayed()
            repeat(3) {
                composeRule.onNodeWithTag("home-feed").performScrollToIndex(4)
                composeRule.onNodeWithTag("home-feed").performScrollToIndex(0)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home-feed").assertIsDisplayed()
        } finally {
            composeRule.runOnUiThread { owner.viewModelStore.clear() }
        }
    }
}
