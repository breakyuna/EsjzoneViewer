@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.page

import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.wenku8.Wenku8Urls
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import java.io.ByteArrayInputStream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** User-operated site browser. No native form submission or inferred signed-in status. */
object Wenku8LoginPage : AppDestination {
    override val key = "Wenku8LoginPage"
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalBaseNavigator.current
        val model = rememberAppViewModel { SessionBrowserModel(context.applicationContext) }
        val browser = model.browser
        DisposableEffect(model, context) {
            model.browserContext.baseContext = context
            onDispose {
                (browser.parent as? ViewGroup)?.removeView(browser)
                model.browserContext.baseContext = context.applicationContext
            }
        }
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.wenku8_session)) }, navigationIcon = {
                IconButton(onClick = { navigator?.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reader_back)) }
            }, actions = {
                TextButton(onClick = {
                    model.bridgeBrowserSession()
                    model.cleared = true
                    model.clearing = true
                    browser.stopLoading()
                    browser.loadUrl("about:blank")
                    PresentationAccess.client.clearWenkuSession {
                        if (model.alive) {
                            model.cleared = false
                            model.clearing = false
                            browser.loadUrl("${Wenku8Urls.BASE}/login.php")
                        }
                    }
                }, enabled = !model.clearing) { Text(stringResource(R.string.wenku8_clear_session)) }
            })
        }) { padding ->
            AndroidView(factory = {
                (browser.parent as? ViewGroup)?.removeView(browser)
                browser
            }, modifier = Modifier.fillMaxSize().padding(padding))
        }
    }
    // Retain the live document across Activity recreation without serializing form data.
    private class SessionBrowserModel(context: Context) : ViewModel() {
        private val applicationContext = context.applicationContext
        val browserContext = MutableContextWrapper(applicationContext)
        var clearing by mutableStateOf(false)
        var cleared = false
        var alive = true
            private set
        val browser = WebView(browserContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = PresentationAccess.client.wenkuUserAgent()
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (alive && !clearing && allowed(url)) bridgeBrowserSession(url)
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && !allowed(request.url.toString())
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString().toHttpUrlOrNull()
                    if (url?.isHttps == true && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                        url.host in setOf("www.wenku8.net", "challenges.cloudflare.com", "www.cloudflare.com")) return null
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
            val loginBrowser = this
            PresentationAccess.client.restoreWenkuBrowserCookies {
                if (alive && !clearing) loginBrowser.loadUrl("${Wenku8Urls.BASE}/login.php")
            }
        }

        fun bridgeBrowserSession(currentUrl: String? = browser.url) {
            val manager = CookieManager.getInstance()
            manager.flush()
            val root = "${Wenku8Urls.BASE}/"
            PresentationAccess.client.importWenkuBrowserCookies(manager.getCookie(root), root)
            currentUrl?.takeIf { it != root && allowed(it) }?.let { url ->
                PresentationAccess.client.importWenkuBrowserCookies(manager.getCookie(url), url)
            }
        }

        override fun onCleared() {
            alive = false
            if (!cleared) bridgeBrowserSession()
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.stopLoading()
            browser.destroy()
            browserContext.baseContext = applicationContext
        }
    }

    private fun allowed(raw: String): Boolean = raw.toHttpUrlOrNull()?.let {
        it.isHttps && it.host == "www.wenku8.net" && it.port == 443 && it.username.isEmpty() && it.password.isEmpty()
    } == true
}
