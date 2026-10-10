package com.breakyuna.esjzone

import android.content.MutableContextWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.page.Wenku8LoginPage
import com.breakyuna.esjzone.util.LocaleHelper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the actual page with the same localized Context and Activity split as MainActivity.
 * No credentials are entered; assertions do not depend on a successful site response.
 */
@RunWith(AndroidJUnit4::class)
class WenkuLoginPageInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun systemLanguageCanOpenLoginWindow() = checkLoginPage(AppLanguage.SYSTEM)

    @Test fun chineseCanOpenLoginWindow() = checkLoginPage(AppLanguage.SIMPLIFIED_CHINESE)

    @Test fun englishCanOpenLoginWindow() = checkLoginPage(AppLanguage.ENGLISH)

    private fun checkLoginPage(language: AppLanguage) {
        val activity = composeRule.activity
        val context = LocaleHelper.createLocalizedContext(activity, language)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalActivity provides activity,
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration
            ) {
                MaterialTheme { Wenku8LoginPage.Content() }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.wenku8_login_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.wenku8_login_window_unavailable)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.wenku_webview_unavailable)).assertDoesNotExist()
        composeRule.runOnIdle {
            val browser = findBrowser(activity.findViewById(android.R.id.content))
            assertNotNull("The login WebView must be attached", browser)
            assertSame(activity, (requireNotNull(browser).context as MutableContextWrapper).baseContext)
        }
    }

    private fun findBrowser(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findBrowser(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
