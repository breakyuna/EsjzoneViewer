package com.breakyuna.esjzone.util

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Observes browser state only; never enables remote debugging or installs a JS bridge. */
internal class WebViewDiagnostics(private val tag: String) {
    private val id = AppLogger.newTraceId("web")

    fun event(stage: String, detail: String = "") = AppLogger.trace(tag) { "browser=$id, stage=$stage, $detail" }

    fun resource(request: WebResourceRequest, allowed: Boolean) {
        if (!AppLogger.debugEnabled) return
        event("resource", "method=${request.method}, main=${request.isForMainFrame}, redirect=${request.isRedirect}, " +
            "gesture=${request.hasGesture()}, allowed=$allowed, url=${diagnosticUrl(request.url.toString())}")
    }

    fun attach(view: WebView) {
        view.webChromeClient = object : WebChromeClient() {
            private var lastProgress = -1
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (!AppLogger.debugEnabled) return
                if (newProgress / 10 != lastProgress / 10 || newProgress == 100) {
                    event("progress", "percent=$newProgress, url=${diagnosticUrl(view.url)}")
                    lastProgress = newProgress
                }
            }
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (!AppLogger.debugEnabled) return super.onConsoleMessage(message)
                // Arbitrary console messages can contain login inputs or tokens. Only classify them.
                val kind = message.message().substringBefore(':').takeIf {
                    it in setOf("TypeError", "SyntaxError", "ReferenceError", "SecurityError", "NetworkError", "Error")
                } ?: "other"
                event("console", "level=${message.messageLevel()}, kind=$kind, line=${message.lineNumber()}, " +
                    "source=${diagnosticUrl(message.sourceId())}")
                return super.onConsoleMessage(message)
            }
        }
        snapshot(view, "created")
    }

    /** Main-thread only; callback extracts an allowlist, never logs the raw JS return value. */
    fun snapshot(view: WebView, stage: String) {
        if (!AppLogger.debugEnabled) return
        event(stage, "url=${diagnosticUrl(view.url)}, original=${diagnosticUrl(view.originalUrl)}, " +
            "size=${view.width}x${view.height}, attached=${view.isAttachedToWindow}, visibility=${view.visibility}, " +
            "progress=${view.progress}, js=${view.settings.javaScriptEnabled}, domStorage=${view.settings.domStorageEnabled}")
        if (!view.settings.javaScriptEnabled) return
        runCatching {
            view.evaluateJavascript("""
                (function(){
                    const body=document.body, form=document.querySelector('form[name="frmlogin"]');
                    const controls=form?Array.from(form.querySelectorAll('input,select,textarea,button')):[];
                    const expected=controls.filter(e=>['username','password','usecookie','submit'].includes(e.name));
                    const duration=form?.querySelector('select[name="usecookie"]');
                    const count=selector=>form?.querySelectorAll(selector).length||0;
                    let actionSubmit=false;
                    try {const a=new URL(form.action,location.href);
                        actionSubmit=a.origin===location.origin&&a.pathname==='/login.php'&&a.searchParams.get('do')==='submit';
                    } catch (_) {}
                    return {ready:document.readyState, url:location.href,
                        htmlChars:document.documentElement?.outerHTML.length||0,
                        textChars:body?.innerText.length||0, bodyChildren:body?.children.length||0,
                        forms:document.forms.length, loginForms:document.querySelectorAll('form[name="frmlogin"]').length,
                        users:document.querySelectorAll('input[name="username"]').length,
                        passwords:document.querySelectorAll('input[type="password"]').length,
                        durations:document.querySelectorAll('select[name="usecookie"] option').length,
                        submitters:document.querySelectorAll('input[type="submit"],button[type="submit"]').length,
                        challenge:!!document.querySelector('#challenge-stage,#challenge-form,.cf-turnstile'),
                        charset:document.characterSet, action:form?.getAttribute('action')||'',
                        post:form?.method.toLowerCase()==='post', encoding:form?.enctype||'', actionSubmit,
                        userControls:count('input[type="text"][name="username"]'),
                        passwordControls:count('input[type="password"][name="password"]'),
                        durationControls:count('select[name="usecookie"]'),
                        submitControls:count('input[type="submit"][name="submit"]'),
                        disabledControls:expected.filter(e=>e.disabled).length,
                        detachedControls:expected.filter(e=>e.form!==form).length,
                        hiddenControls:count('input[type="hidden"]'),
                        extraControls:controls.filter(e=>['INPUT','SELECT','TEXTAREA'].includes(e.tagName)&&
                            !['hidden','reset','button'].includes(e.type)&&!expected.includes(e)&&!e.disabled).length,
                        validDurations:['0','86400','2592000','315360000'].filter(v=>
                            duration&&Array.from(duration.options).some(o=>o.value===v)).length};
                })()
            """.trimIndent()) { raw ->
                val data = runCatching { JSONObject(raw) }.getOrNull()
                if (data == null) { event("dom", "trigger=$stage, result=invalid"); return@evaluateJavascript }
                val ready = data.optString("ready").takeIf { it in setOf("loading", "interactive", "complete") } ?: "unknown"
                val charset = data.optString("charset").lowercase().takeIf {
                    it in setOf("utf-8", "gbk", "gb2312", "gb18030", "windows-1252", "big5")
                } ?: "other"
                val counts = listOf("htmlChars", "textChars", "bodyChildren", "forms", "loginForms", "users",
                    "passwords", "durations", "submitters", "userControls", "passwordControls", "durationControls",
                    "submitControls", "disabledControls", "detachedControls", "hiddenControls", "extraControls",
                    "validDurations").joinToString { "$it=${data.optInt(it, -1)}" }
                val encoding = data.optString("encoding").lowercase().takeIf {
                    it in setOf("application/x-www-form-urlencoded", "multipart/form-data", "text/plain")
                } ?: "other"
                val documentUrl = data.optString("url")
                val action = documentUrl.toHttpUrlOrNull()
                    ?.resolve(data.optString("action"))?.toString()
                event("dom", "trigger=$stage, ready=$ready, document=${diagnosticUrl(documentUrl)}, charset=$charset, " +
                    "$counts, challenge=${data.optBoolean("challenge")}, post=${data.optBoolean("post")}, encoding=$encoding, " +
                    "actionSubmit=${data.optBoolean("actionSubmit")}, action=${diagnosticUrl(action)}")
            }
        }.onFailure { event("snapshot-error", "type=${it.javaClass.name}") }
    }
}

/** Subclasses retain their existing navigation/resource/error policies and call super for diagnostics. */
internal open class DiagnosticWebViewClient(private val diagnostics: WebViewDiagnostics) : WebViewClient() {
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        diagnostics.event("page-started", "url=${diagnosticUrl(url)}")
    }
    override fun onPageFinished(view: WebView, url: String) {
        diagnostics.snapshot(view, "page-finished")
    }
    override fun onPageCommitVisible(view: WebView, url: String) {
        diagnostics.snapshot(view, "commit-visible")
    }
    override fun onLoadResource(view: WebView, url: String) {
        if (!AppLogger.debugEnabled) return
        diagnostics.event("load-resource", "url=${diagnosticUrl(url)}")
    }
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        diagnostics.event("load-error", "code=${error.errorCode}, main=${request.isForMainFrame}, url=${diagnosticUrl(request.url.toString())}")
    }
    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        diagnostics.event("http-error", "status=${errorResponse.statusCode}, main=${request.isForMainFrame}, url=${diagnosticUrl(request.url.toString())}")
    }
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        diagnostics.event("ssl-error", "code=${error.primaryError}, url=${diagnosticUrl(error.url)}")
        super.onReceivedSslError(view, handler, error)
    }
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        diagnostics.event("renderer-gone", "crashed=${detail.didCrash()}, priority=${detail.rendererPriorityAtExit()}")
        return super.onRenderProcessGone(view, detail)
    }
}
