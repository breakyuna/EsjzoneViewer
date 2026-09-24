package com.breakyuna.esjzone.ui.page

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.ui.designsystem.AppTheme
import com.breakyuna.esjzone.util.LocaleHelper

/** Opens ordinary web links inside the app without sharing the ESJ network client's cookies. */
@OptIn(ExperimentalMaterial3Api::class)
class InAppBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = intent.getStringExtra(EXTRA_URL)?.takeIf(::isWebUrl)
        if (initialUrl == null) {
            finish()
            return
        }
        setContent {
            val appLanguage by PresentationAccess.settings.language
            val localizedContext = remember(appLanguage) {
                LocaleHelper.createLocalizedContext(this, appLanguage)
            }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides Configuration(localizedContext.resources.configuration)
            ) {
                AppTheme {
                    var currentUrl by remember { mutableStateOf(initialUrl) }
                    var canGoBack by remember { mutableStateOf(false) }
                    val browser = remember {
                        WebView(this).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    if (isWebUrl(request.url.toString())) return false
                                    if (request.isForMainFrame) openSystemBrowser(request.url.toString())
                                    return true
                                }

                                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                    currentUrl = url
                                    canGoBack = view.canGoBack()
                                }

                            override fun onPageFinished(view: WebView, url: String) {
                                currentUrl = url
                                canGoBack = view.canGoBack()
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                if (request.isForMainFrame && request.url.scheme == "http") {
                                    openSystemBrowser(request.url.toString())
                                    finish()
                                }
                            }
                            }
                            setDownloadListener { url, _, _, _, _ -> openSystemBrowser(url) }
                        }
                    }
                    DisposableEffect(browser) {
                        browser.loadUrl(initialUrl)
                        onDispose {
                            browser.stopLoading()
                            browser.destroy()
                        }
                    }
                    BackHandler(canGoBack) { browser.goBack() }
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(Uri.parse(currentUrl).host ?: stringResource(R.string.web_page)) },
                                navigationIcon = {
                                    IconButton(onClick = { if (browser.canGoBack()) browser.goBack() else finish() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.close))
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { openSystemBrowser(browser.url ?: currentUrl) }) {
                                        Icon(Icons.Filled.OpenInNew, stringResource(R.string.open_in_system_browser))
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        AndroidView(factory = { browser }, modifier = Modifier.fillMaxSize().padding(padding))
                    }
                }
            }
        }
    }

    private fun openSystemBrowser(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        }
    }

    companion object {
        private const val EXTRA_URL = "browser_url"

        fun open(context: Context, url: String) {
            val target = url.trim()
            if (!isWebUrl(target)) return
            context.startActivity(
                Intent(context, InAppBrowserActivity::class.java)
                    .putExtra(EXTRA_URL, target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        private fun isWebUrl(url: String): Boolean = Uri.parse(url).scheme?.lowercase() in setOf("http", "https") &&
            !Uri.parse(url).host.isNullOrBlank()
    }
}
