package com.breakyuna.esjzone

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
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
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeFeedLayoutInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

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
