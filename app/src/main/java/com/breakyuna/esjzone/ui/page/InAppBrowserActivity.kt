package com.breakyuna.esjzone.ui.page

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakyuna.esjzone.AppThemeMode
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.component.resolveImageUrl
import com.breakyuna.esjzone.ui.designsystem.AppTheme
import com.breakyuna.esjzone.ui.designsystem.LocalGlobalScript
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.util.LocaleHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.jsoup.Jsoup

/** Opens ordinary web links inside the app without sharing the ESJ network client's cookies. */
@OptIn(ExperimentalMaterial3Api::class)
class InAppBrowserActivity : ComponentActivity() {
    private val websiteConverter = com.breakyuna.esjzone.ui.reader.WebsiteScriptConverter()
    @Volatile private var currentScriptMode = ReaderScript.ORIGINAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = intent.getStringExtra(EXTRA_URL)?.takeIf(::isWebUrl)
        val keepInApp = intent.getBooleanExtra(EXTRA_KEEP_IN_APP, false)
        if (initialUrl == null) {
            finish()
            return
        }
        setContent {
            val appLanguage by PresentationAccess.settings.language
            val themeMode by PresentationAccess.settings.themeMode
            val appPalette by PresentationAccess.settings.palette
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
                AppTheme(darkTheme = darkTheme, palette = appPalette) {
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
                                fun convertBatch(payload: String?, mode: String?): String {
                                    if (payload.isNullOrEmpty() || payload.length > MAX_BRIDGE_PAYLOAD_CHARS) return ""
                                    val script = ReaderScript.entries.firstOrNull { it.name == mode }
                                        ?: ReaderScript.ORIGINAL
                                    if (script == ReaderScript.ORIGINAL || script != currentScriptMode) return ""
                                    val items = runCatching { Json.decodeFromString<List<String>>(payload) }
                                        .getOrNull() ?: return ""
                                    if (items.isEmpty()) return ""
                                    val converted = websiteConverter.convertBatch(items, script)
                                    if (converted.size != items.size) return ""
                                    return Json.encodeToString(converted)
                                }
                            }, "EsjScriptBridge")
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val target = request.url.toString()
                                    if (isWebUrl(target)) return false
                                    if (request.isForMainFrame) openSystemBrowser(target)
                                    return true
                                }

                                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                    websiteConverter.resetPage()
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
                                    if (!keepInApp && request.isForMainFrame && request.url.scheme == "http") {
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
                            websiteConverter.close()
                            browser.removeJavascriptInterface("EsjScriptBridge")
                            browser.stopLoading()
                            browser.destroy()
                        }
                    }
                    BackHandler(canGoBack) { browser.goBack() }
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = Uri.parse(currentUrl).host ?: stringResource(R.string.web_page),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
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
                if (!state && mode === 'ORIGINAL') return;
                var MAX_TEXT_LENGTH = 32768;
                var MAX_BATCH_NODES = 128;
                var MAX_BATCH_CHARS = 65536;
                if (!state) {
                    state = { mode: mode, records: new WeakMap() };
                    state.flush = function(batch) {
                        if (!batch.length) return;
                        var texts = batch.map(function(item) { return item.record.original; });
                        var raw = window.EsjScriptBridge.convertBatch(JSON.stringify(texts), state.mode);
                        if (!raw) return;
                        var results;
                        try {
                            results = JSON.parse(raw);
                        } catch (_) {
                            return;
                        }
                        if (!Array.isArray(results) || results.length !== batch.length) return;
                        for (var i = 0; i < batch.length; i++) {
                            var next = results[i];
                            if (typeof next !== 'string') continue;
                            var item = batch[i];
                            item.record.rendered = next;
                            item.record.mode = state.mode;
                            state.records.set(item.node, item.record);
                            if (item.node.nodeValue === item.expected && item.expected !== next) {
                                item.node.nodeValue = next;
                            }
                        }
                    };
                    state.collectNode = function(node, queue, seen) {
                        if (!node || node.nodeType !== Node.TEXT_NODE || seen.has(node)) return;
                        seen.add(node);
                        var parent = node.parentElement;
                        if (!parent || /^(SCRIPT|STYLE|NOSCRIPT|TEXTAREA|CODE|PRE)$/.test(parent.tagName) ||
                            parent.closest('[contenteditable="true"]')) return;
                        var current = node.nodeValue || '';
                        if (!/\S/.test(current)) return;
                        var record = state.records.get(node);
                        if (record && current === record.rendered && record.mode === state.mode) return;
                        if (!record || current !== record.rendered) {
                            record = { original: current, rendered: current, mode: 'ORIGINAL' };
                        }
                        if (state.mode === 'ORIGINAL') {
                            record.rendered = record.original;
                            record.mode = 'ORIGINAL';
                            state.records.set(node, record);
                            if (current !== record.original) node.nodeValue = record.original;
                            return;
                        }
                        if (record.original.length > MAX_TEXT_LENGTH) return;
                        queue.push({ node: node, record: record, expected: current });
                    };
                    state.collectTree = function(root, queue, seen) {
                        if (!root) return;
                        if (root.nodeType === Node.TEXT_NODE) {
                            state.collectNode(root, queue, seen);
                        } else if (root.nodeType === Node.ELEMENT_NODE) {
                            var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
                            var node;
                            while ((node = walker.nextNode())) state.collectNode(node, queue, seen);
                        }
                    };
                    state.applyQueue = function(queue) {
                        if (!queue.length) return;
                        var batch = [];
                        var batchChars = 0;
                        for (var i = 0; i < queue.length; i++) {
                            var item = queue[i];
                            var len = item.record.original.length;
                            if (batch.length >= MAX_BATCH_NODES ||
                                (batch.length > 0 && batchChars + len > MAX_BATCH_CHARS)) {
                                state.flush(batch);
                                batch = [];
                                batchChars = 0;
                            }
                            batch.push(item);
                            batchChars += len;
                        }
                        state.flush(batch);
                    };
                    new MutationObserver(function(changes) {
                        var queue = [];
                        var seen = new Set();
                        changes.forEach(function(change) {
                            if (change.type === 'characterData') {
                                state.collectNode(change.target, queue, seen);
                            } else {
                                change.addedNodes.forEach(function(added) {
                                    state.collectTree(added, queue, seen);
                                });
                            }
                        });
                        state.applyQueue(queue);
                    }).observe(document.documentElement, { subtree: true, childList: true, characterData: true });
                    window.__esjScriptState = state;
                }
                state.mode = mode;
                var queue = [];
                state.collectTree(document.body, queue, new Set());
                state.applyQueue(queue);
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
        private const val EXTRA_KEEP_IN_APP = "keep_in_app"
        private const val MAX_BRIDGE_PAYLOAD_CHARS = 512 * 1024

        fun open(context: Context, url: String, keepInApp: Boolean = false) {
            val target = if (keepInApp) EsjzoneUrls.normalizeHttpUrl(url) ?: return else url.trim()
            if (!isWebUrl(target)) return
            context.startActivity(
                Intent(context, InAppBrowserActivity::class.java)
                    .putExtra(EXTRA_URL, target)
                    .putExtra(EXTRA_KEEP_IN_APP, keepInApp)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        internal fun buildSafeChapterHtmlDocument(
            title: String,
            rawHtml: String,
            darkTheme: Boolean,
            fontSizeSp: Float,
            baseUrl: String = "",
            localImages: Map<String, String> = emptyMap()
        ): String {
            val fragment = Jsoup.parseBodyFragment(rawHtml, baseUrl)
            fragment.select("script, iframe, object, embed, form, base, meta").remove()
            for (element in fragment.allElements) {
                val dangerousAttrs = element.attributes().asList()
                    .filter { attr ->
                        val key = attr.key.lowercase()
                        val value = attr.value.trim().lowercase()
                        key.startsWith("on") ||
                            ((key == "href" || key == "src" || key == "action") && value.startsWith("javascript:"))
                    }
                dangerousAttrs.forEach { element.removeAttr(it.key) }
                if (element.normalName() == "details") {
                    element.attr("open", "")
                }
                if (element.normalName() == "img") {
                    val source = resolveImageUrl(element)
                    if (source.isNotBlank()) {
                        element.attr("src", source)
                        if (localImages[source]?.let { it != source } == true) {
                            element.removeAttr("srcset").removeAttr("sizes")
                            element.closest("picture")?.select("source")?.remove()
                        }
                    }
                }
            }
            val safeTitle = org.jsoup.nodes.Entities.escape(title)
            val bg = if (darkTheme) "#121212" else "#FAF9F6"
            val fg = if (darkTheme) "#E6E1E5" else "#1C1B1F"
            val muted = if (darkTheme) "#A8A2A8" else "#625B71"
            val border = if (darkTheme) "#38353B" else "#DED8E1"
            val link = if (darkTheme) "#9ECAFF" else "#1A5FB4"
            val sizePx = fontSizeSp.coerceIn(12f, 36f)
            return """
                <!doctype html>
                <html>
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                  html, body { margin: 0; padding: 0; background: $bg; color: $fg; }
                  body { padding: 16px 20px 48px; font-size: ${sizePx}px; line-height: 1.75; word-wrap: break-word; overflow-wrap: break-word; }
                  h1.chapter-title { font-size: 1.25em; margin: 0 0 18px; line-height: 1.4; }
                  a { color: $link; }
                  img { max-width: 100%; height: auto; display: block; margin: 12px auto; }
                  hr { border: none; border-top: 1px solid $border; margin: 18px 0; }
                  details { border-top: 1px solid $border; border-bottom: 1px solid $border; padding: 10px 0; margin: 14px 0; }
                  summary { font-weight: 600; cursor: pointer; margin-bottom: 6px; }
                  blockquote { margin: 12px 0; padding: 4px 12px; border-left: 3px solid $border; color: $muted; }
                  table { border-collapse: collapse; width: 100%; margin: 12px 0; display: block; overflow-x: auto; }
                  th, td { border: 1px solid $border; padding: 6px 8px; text-align: left; }
                  pre { overflow-x: auto; padding: 10px; border: 1px solid $border; border-radius: 6px; }
                </style>
                </head>
                <body>
                  ${if (safeTitle.isNotBlank()) "<h1 class=\"chapter-title\">$safeTitle</h1>" else ""}
                  ${fragment.body().html()}
                </body>
                </html>
            """.trimIndent()
        }

        private fun isWebUrl(url: String): Boolean = Uri.parse(url).scheme?.lowercase() in setOf("http", "https") &&
            !Uri.parse(url).host.isNullOrBlank()
    }
}
