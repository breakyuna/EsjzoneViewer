package com.breakyuna.esjzone.ui.page

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.AppThemeMode
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.ui.designsystem.AppTheme
import com.breakyuna.esjzone.ui.designsystem.LocalGlobalScript
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderScriptConverter
import com.breakyuna.esjzone.util.LocaleHelper
import org.json.JSONObject

/** Opens ordinary web links inside the app without sharing the ESJ network client's cookies. */
@OptIn(ExperimentalMaterial3Api::class)
class InAppBrowserActivity : ComponentActivity() {
    @Volatile private var currentScriptMode = ReaderScript.ORIGINAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = intent.getStringExtra(EXTRA_URL)?.takeIf(::isWebUrl)
        if (initialUrl == null) {
            finish()
            return
        }
        setContent {
            val appLanguage by PresentationAccess.settings.language
            val themeMode by PresentationAccess.settings.themeMode
            val readerSettings by PresentationAccess.readerSettings.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            LaunchedEffect(darkTheme) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            val localizedContext = remember(appLanguage) {
                LocaleHelper.createLocalizedContext(this, appLanguage)
            }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides Configuration(localizedContext.resources.configuration),
                LocalGlobalScript provides readerSettings.script
            ) {
                AppTheme(darkTheme = darkTheme) {
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
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun convert(text: String, mode: String): String {
                                    if (text.length > 131_072) return text
                                    val script = ReaderScript.entries.firstOrNull { it.name == mode }
                                        ?: ReaderScript.ORIGINAL
                                    return ReaderScriptConverter.convert(text, script)
                                }
                            }, "EsjScriptBridge")
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
                                applyWebsiteScript(view, currentScriptMode)
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
                    LaunchedEffect(browser, readerSettings.script) {
                        currentScriptMode = readerSettings.script
                        applyWebsiteScript(browser, readerSettings.script)
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

    private fun applyWebsiteScript(browser: WebView, mode: ReaderScript) {
        browser.evaluateJavascript("""
            (function(mode) {
                if (!document.body) return;
                var state = window.__esjScriptState;
                if (!state) {
                    state = { mode: mode, records: new WeakMap() };
                    state.applyNode = function(node) {
                        var parent = node.parentElement;
                        if (!parent || /^(SCRIPT|STYLE|NOSCRIPT|TEXTAREA|CODE|PRE)$/.test(parent.tagName) ||
                            parent.closest('[contenteditable="true"]')) return;
                        var current = node.nodeValue || '';
                        var record = state.records.get(node);
                        if (!record || current !== record.rendered) record = { original: current, rendered: current };
                        var next = state.mode === 'ORIGINAL' ? record.original :
                            window.EsjScriptBridge.convert(record.original, state.mode);
                        record.rendered = next;
                        state.records.set(node, record);
                        if (current !== next) node.nodeValue = next;
                    };
                    state.scan = function(root) {
                        if (!root) return;
                        if (root.nodeType === Node.TEXT_NODE) {
                            state.applyNode(root);
                        } else if (root.nodeType === Node.ELEMENT_NODE) {
                            var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
                            var node;
                            while ((node = walker.nextNode())) state.applyNode(node);
                        }
                    };
                    new MutationObserver(function(changes) {
                        changes.forEach(function(change) {
                            if (change.type === 'characterData') state.applyNode(change.target);
                            else change.addedNodes.forEach(state.scan);
                        });
                    }).observe(document.documentElement, { subtree: true, childList: true, characterData: true });
                    window.__esjScriptState = state;
                }
                state.mode = mode;
                state.scan(document.body);
            })(${JSONObject.quote(mode.name)});
        """.trimIndent(), null)
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
