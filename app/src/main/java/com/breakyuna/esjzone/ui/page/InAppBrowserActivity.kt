package com.breakyuna.esjzone.ui.page

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
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
import androidx.lifecycle.lifecycleScope
import com.breakyuna.esjzone.AppThemeMode
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.StructuredChapterCache
import com.breakyuna.esjzone.network.external.wenkuChapterCacheKey
import com.breakyuna.esjzone.novellibrary.component.resolveImageUrl
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import com.breakyuna.esjzone.ui.designsystem.AppTheme
import com.breakyuna.esjzone.ui.designsystem.LocalGlobalScript
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.util.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/**
 * Opens ordinary web links inside the app without sharing the ESJ network
 * client's cookies, and also renders saved chapter HTML snapshots offline as a
 * browser-grade fallback when native rich parsing cannot reproduce complex markup.
 */
@OptIn(ExperimentalMaterial3Api::class)
class InAppBrowserActivity : ComponentActivity() {
    private val websiteConverter = com.breakyuna.esjzone.ui.reader.WebsiteScriptConverter()
    @Volatile private var currentScriptMode = ReaderScript.ORIGINAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val snapshotToken = intent.getStringExtra(EXTRA_HTML_TOKEN)
        val htmlSnapshot = snapshotToken?.let(::peekHtmlSnapshot)
        val initialUrl = intent.getStringExtra(EXTRA_URL)?.takeIf(::isWebUrl)
        val keepInApp = intent.getBooleanExtra(EXTRA_KEEP_IN_APP, false)
        if (htmlSnapshot == null && initialUrl == null) {
            finish()
            return
        }
        if (snapshotToken != null && htmlSnapshot == null && initialUrl != null) {
            val novelUrl = intent.getStringExtra(EXTRA_NOVEL_URL)
            val fontSize = intent.getFloatExtra(EXTRA_FONT_SIZE, 18f)
            lifecycleScope.launch {
                val restored = withContext(Dispatchers.IO) { restoreChapterHtml(initialUrl, novelUrl, fontSize) }
                showBrowser(initialUrl, keepInApp, restored)
            }
        } else {
            showBrowser(initialUrl, keepInApp, htmlSnapshot)
        }
    }

    private suspend fun restoreChapterHtml(url: String, novelUrl: String?, fontSize: Float): ChapterHtmlSnapshot? {
        val container = EsjzoneApplication.instance.container
        val downloaded = try {
            container.initializeDownloads()
            PresentationAccess.downloads.readChapter(url, novelUrl)?.takeUnless { it.contentHtml.isNullOrBlank() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val detail = downloaded ?: try {
            container.initializeCaches()
            val client = PresentationAccess.client
            val key = when (resolveChapterSource(url, url)) {
                ChapterSource.ESJ_ZONE -> {
                    val host = Uri.parse(url).host.orEmpty()
                    client.novelDetailCacheKey(client.restoreAuthorization(host) ?: Authorization(), url)
                }
                ChapterSource.WENKU8 -> wenkuChapterCacheKey(url)
                ChapterSource.UNSUPPORTED_EXTERNAL -> return null
            }
            StructuredChapterCache.read(key, url, maxAgeMillis = Long.MAX_VALUE)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return null
        val html = detail.contentHtml?.takeIf { it.isNotBlank() } ?: return null
        return ChapterHtmlSnapshot(detail.name, html, detail.sourceUrl ?: url, fontSize, detail.imageLocations)
    }

    private fun showBrowser(initialUrl: String?, keepInApp: Boolean, htmlSnapshot: ChapterHtmlSnapshot?) {
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
                    val isHtmlSnapshotMode = htmlSnapshot != null
                    val externalSourceUrl = htmlSnapshot?.sourceUrl?.takeIf(::isWebUrl) ?: initialUrl
                    val snapshotBaseUrl = externalSourceUrl ?: EsjzoneUrls.Base
                    var currentUrl by remember { mutableStateOf(externalSourceUrl.orEmpty()) }
                    var canGoBack by remember { mutableStateOf(false) }
                    val browser = remember {
                        WebView(this).apply {
                            settings.javaScriptEnabled = !isHtmlSnapshotMode
                            settings.domStorageEnabled = !isHtmlSnapshotMode
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            if (!isHtmlSnapshotMode) {
                                addJavascriptInterface(object {
                                    @JavascriptInterface
                                    fun convert(text: String, mode: String): String {
                                        if (text.length > 32_768) return text
                                        val script = ReaderScript.entries.firstOrNull { it.name == mode }
                                            ?: ReaderScript.ORIGINAL
                                        if (script != currentScriptMode) return text
                                        return websiteConverter.convert(text, script)
                                    }
                                }, "EsjScriptBridge")
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val target = request.url.toString()
                                    if (isHtmlSnapshotMode) {
                                        if (request.isForMainFrame && isWebUrl(target)) {
                                            open(this@InAppBrowserActivity, target, keepInApp = true)
                                        }
                                        return true
                                    }
                                    if (isWebUrl(target)) return false
                                    if (request.isForMainFrame) openSystemBrowser(target)
                                    return true
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): WebResourceResponse? {
                                    val localTarget = htmlSnapshot?.localImages?.get(request.url.toString())
                                        ?: return super.shouldInterceptRequest(view, request)
                                    return openLocalImageResponse(localTarget)
                                        ?: super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                    if (!isHtmlSnapshotMode) currentUrl = url
                                    canGoBack = view.canGoBack()
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    if (!isHtmlSnapshotMode) {
                                        currentUrl = url
                                        applyWebsiteScript(view, currentScriptMode)
                                    }
                                    canGoBack = view.canGoBack()
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError
                                ) {
                                    if (!isHtmlSnapshotMode && !keepInApp && request.isForMainFrame && request.url.scheme == "http") {
                                        openSystemBrowser(request.url.toString())
                                        finish()
                                    }
                                }
                            }
                            setDownloadListener { url, _, _, _, _ -> openSystemBrowser(url) }
                        }
                    }
                    LaunchedEffect(browser, readerSettings.script, darkTheme) {
                        currentScriptMode = readerSettings.script
                        if (htmlSnapshot != null) {
                            val documentHtml = withContext(Dispatchers.Default) {
                                val convertedHtml = convertHtmlTextNodes(htmlSnapshot.html, readerSettings.script)
                                buildSafeChapterHtmlDocument(
                                    title = htmlSnapshot.title,
                                    rawHtml = convertedHtml,
                                    darkTheme = darkTheme,
                                    fontSizeSp = htmlSnapshot.fontSizeSp,
                                    baseUrl = snapshotBaseUrl,
                                    localImages = htmlSnapshot.localImages
                                )
                            }
                            browser.loadDataWithBaseURL(snapshotBaseUrl, documentHtml, "text/html", "utf-8", null)
                        } else {
                            applyWebsiteScript(browser, readerSettings.script)
                        }
                    }
                    DisposableEffect(browser) {
                        if (htmlSnapshot == null && initialUrl != null) {
                            browser.loadUrl(initialUrl)
                        }
                        onDispose {
                            websiteConverter.close()
                            if (!isHtmlSnapshotMode) {
                                browser.removeJavascriptInterface("EsjScriptBridge")
                            }
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
                                        text = htmlSnapshot?.title?.takeIf { it.isNotBlank() }
                                            ?: Uri.parse(currentUrl).host
                                            ?: stringResource(R.string.web_page),
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
                                    if (isHtmlSnapshotMode && externalSourceUrl != null) {
                                        IconButton(onClick = { open(this@InAppBrowserActivity, externalSourceUrl, keepInApp = true) }) {
                                            Icon(Icons.Filled.Language, stringResource(R.string.reader_open_online_page))
                                        }
                                    }
                                    val systemUrl = if (isHtmlSnapshotMode) externalSourceUrl else (browser.url ?: currentUrl)
                                    if (!systemUrl.isNullOrBlank()) {
                                        IconButton(onClick = { openSystemBrowser(systemUrl) }) {
                                            Icon(Icons.Filled.OpenInNew, stringResource(R.string.open_in_system_browser))
                                        }
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

    private fun convertHtmlTextNodes(rawHtml: String, mode: ReaderScript): String {
        if (mode == ReaderScript.ORIGINAL) return rawHtml
        return runCatching {
            val doc = Jsoup.parseBodyFragment(rawHtml)
            fun convertNode(node: org.jsoup.nodes.Node) {
                when (node) {
                    is org.jsoup.nodes.TextNode -> {
                        val original = node.wholeText
                        if (original.isNotBlank()) {
                            node.text(websiteConverter.convert(original, mode))
                        }
                    }
                    is org.jsoup.nodes.Element -> {
                        if (node.normalName() !in setOf("script", "style", "noscript", "code", "pre")) {
                            node.childNodes().forEach(::convertNode)
                        }
                    }
                }
            }
            doc.body().childNodes().forEach(::convertNode)
            doc.body().html()
        }.getOrDefault(rawHtml)
    }

    private fun openLocalImageResponse(localTarget: String): WebResourceResponse? = runCatching {
        val uri = Uri.parse(localTarget)
        val mime = MimeTypeMap.getFileExtensionFromUrl(localTarget)
            ?.lowercase()
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            ?: "image/*"
        val stream = when (uri.scheme?.lowercase()) {
            "content" -> contentResolver.openInputStream(uri)
            "file" -> uri.path?.let { File(it) }?.takeIf { it.isFile }?.let(::FileInputStream)
            null -> File(localTarget).takeIf { it.isFile }?.let(::FileInputStream)
            else -> null
        } ?: return@runCatching null
        WebResourceResponse(mime, null, stream)
    }.getOrNull()

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
        private const val EXTRA_KEEP_IN_APP = "keep_in_app"
        private const val EXTRA_HTML_TOKEN = "chapter_html_token"
        private const val EXTRA_NOVEL_URL = "chapter_novel_url"
        private const val EXTRA_FONT_SIZE = "chapter_font_size"
        private const val MAX_SNAPSHOTS = 4

        internal data class ChapterHtmlSnapshot(
            val title: String,
            val html: String,
            val sourceUrl: String?,
            val fontSizeSp: Float,
            val localImages: Map<String, String>
        )

        private val htmlSnapshots = object : LinkedHashMap<String, ChapterHtmlSnapshot>(MAX_SNAPSHOTS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ChapterHtmlSnapshot>?) =
                size > MAX_SNAPSHOTS
        }

        private fun putHtmlSnapshot(snapshot: ChapterHtmlSnapshot): String {
            val token = UUID.randomUUID().toString()
            synchronized(htmlSnapshots) {
                htmlSnapshots[token] = snapshot
            }
            return token
        }

        private fun peekHtmlSnapshot(token: String): ChapterHtmlSnapshot? =
            synchronized(htmlSnapshots) { htmlSnapshots[token] }

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

        fun openChapterHtml(
            context: Context,
            title: String,
            html: String,
            sourceUrl: String,
            fontSizeSp: Float = 18f,
            localImages: Map<String, String> = emptyMap(),
            novelUrl: String? = null
        ) {
            if (html.isBlank()) {
                open(context, sourceUrl, keepInApp = true)
                return
            }
            val fontSize = fontSizeSp.coerceIn(12f, 36f)
            val token = putHtmlSnapshot(
                ChapterHtmlSnapshot(
                    title = title,
                    html = html,
                    sourceUrl = sourceUrl,
                    fontSizeSp = fontSize,
                    localImages = localImages
                )
            )
            context.startActivity(
                Intent(context, InAppBrowserActivity::class.java)
                    .putExtra(EXTRA_HTML_TOKEN, token)
                    .putExtra(EXTRA_URL, sourceUrl)
                    .putExtra(EXTRA_KEEP_IN_APP, true)
                    .putExtra(EXTRA_NOVEL_URL, novelUrl)
                    .putExtra(EXTRA_FONT_SIZE, fontSize)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

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
