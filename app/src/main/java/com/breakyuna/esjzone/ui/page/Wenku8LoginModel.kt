package com.breakyuna.esjzone.ui.page

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.external.wenkuDiagnosticUrl
import com.breakyuna.esjzone.network.wenku8.Wenku8LoginForm
import com.breakyuna.esjzone.network.wenku8.Wenku8PageKind
import com.breakyuna.esjzone.network.wenku8.Wenku8Urls
import com.breakyuna.esjzone.network.wenku8.wenku8SignedInDocument
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.DiagnosticWebViewClient
import com.breakyuna.esjzone.util.WebViewDiagnostics
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal enum class Wenku8LoginStatus(val message: Int? = null) {
    PREPARING(R.string.wenku8_form_preparing), READY,
    SUBMITTING(R.string.wenku8_login_submitting), CHECKING(R.string.wenku8_login_checking),
    MANUAL(R.string.wenku8_login_manual), SUCCESS(R.string.wenku8_signed_in),
    REJECTED(R.string.wenku8_login_rejected), NETWORK(R.string.login_network_fail),
    UNKNOWN(R.string.wenku8_login_unknown), INCOMPATIBLE(R.string.wenku8_form_incompatible),
    INVALID(R.string.wenku8_login_invalid);

    fun needsWebReload(formReady: Boolean, submitting: Boolean, attemptStarted: Boolean): Boolean =
        !formReady && !submitting && !attemptStarted &&
            this in setOf(INCOMPATIBLE, NETWORK, UNKNOWN)
}

/** Navigation-entry lifetime only. No password state and no process-owned Activity. */
internal class Wenku8LoginModel(context: Context) : ViewModel() {
    private val applicationContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val diagnostics = WebViewDiagnostics("Wenku8LoginModel")
    private var attemptId = UUID.randomUUID().toString()
    private var browserContext: MutableContextWrapper? = null
    private var browser: WebView? = null
    private var attached = false
    private var alive = true
    private var leaving = false
    private var bindingGeneration = 0L
    private var documentGeneration = 0L
    private var inspectionGeneration = 0L
    private var initialized = false
    private var pendingRedirect: String? = null
    private var requestedUrl = ""
    private var lastInspection = ""
    private var documentLoading = false
    private var httpError = false
    private var deadline = 0L
    private var pendingDuration: String? = null
    private var attemptStarted = false
    private var submittedDocument: Long? = null
    private var recordedDuration: String? = null
    private var verification: Job? = null
    private var poll: Runnable? = null
    private var timeout: Runnable? = null
    var status by mutableStateOf(Wenku8LoginStatus.PREPARING)
        private set
    var formReady by mutableStateOf(false)
        private set
    var submitting by mutableStateOf(false)
        private set
    var clearing by mutableStateOf(false)
        private set
    var showWeb by mutableStateOf(false)
        private set
    val busy: Boolean get() = submitting || status in setOf(Wenku8LoginStatus.PREPARING, Wenku8LoginStatus.CHECKING)
    val canSubmit: Boolean get() = active() && formReady && !busy && status != Wenku8LoginStatus.SUCCESS
    private fun active(): Boolean = alive && attached && !leaving && !clearing
    private fun assertMain() = check(Looper.myLooper() == Looper.getMainLooper())

    fun attach(activity: Activity): WebView {
        assertMain()
        diagnostics.event("attach", "retained=${browser != null}, initialized=$initialized, status=$status")
        attached = true
        val binding = ++bindingGeneration
        browserContext?.baseContext = activity
        val view = browser ?: createBrowser(activity).also { browser = it }
        if (!initialized && !clearing && !leaving) {
            startDeadline()
            diagnostics.event("cookie-restore-start")
            PresentationAccess.client.restoreWenkuBrowserCookies {
                diagnostics.event("cookie-restore-end", "active=${active()}, binding=$binding, currentBinding=$bindingGeneration")
                if (active() && binding == bindingGeneration && !initialized) {
                    initialized = true
                    // Existing sessions go through the real protected homepage first.
                    loadPage("${Wenku8Urls.BASE}/index.php")
                }
            }
        } else if (active() && pendingRedirect != null) {
            val target = pendingRedirect!!
            pendingRedirect = null
            loadPage(target)
        } else if (active()) {
            // Inspect the retained document; never replay submission on recreation.
            if (busy) armTimeout()
            if (!documentLoading) inspect()
        }
        return view
    }

    fun detach(activity: Activity? = null) {
        assertMain()
        if (activity != null && browserContext?.baseContext !== activity) return
        diagnostics.event("detach", "status=$status, loading=$documentLoading")
        attached = false
        bindingGeneration++
        cancelChecks()
        browser?.let { (it.parent as? ViewGroup)?.removeView(it) }
        browserContext?.baseContext = applicationContext
    }

    private fun createBrowser(activity: Activity): WebView = WebView(
        MutableContextWrapper(activity).also { browserContext = it }
    ).apply {
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = PresentationAccess.client.wenkuUserAgent()
            allowFileAccess = false
            allowContentAccess = false
            saveFormData = false
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        diagnostics.attach(this)
        webViewClient = object : DiagnosticWebViewClient(diagnostics) {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (!alive || leaving || clearing) return
                super.onPageStarted(view, url, favicon)
                documentGeneration++
                lastInspection = ""
                logBrowser("started", url)
                documentLoading = true
                httpError = false
                if (!active()) return
                cancelChecks()
                formReady = false
                if (status == Wenku8LoginStatus.SUCCESS) return
                status = if (submitting) Wenku8LoginStatus.SUBMITTING else Wenku8LoginStatus.PREPARING
                startDeadline()
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (!alive || leaving || clearing) return
                if (view.url != url) return
                super.onPageFinished(view, url)
                logBrowser("finished", url)
                documentLoading = false
                if (allowed(url) && status !in setOf(Wenku8LoginStatus.CHECKING, Wenku8LoginStatus.SUCCESS) &&
                    verification?.isActive != true) bridgeBrowserSession()
                if (active() && allowed(url)) inspect()
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame && active()) {
                    logBrowser("load-error", request.url.toString(), "code=${error.errorCode}")
                    httpError = true
                    cancelChecks(); submitting = false; formReady = false
                    if (status != Wenku8LoginStatus.SUCCESS) status = Wenku8LoginStatus.NETWORK
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                super.onReceivedHttpError(view, request, response)
                if (request.isForMainFrame && alive && !leaving && !clearing) {
                    httpError = true
                    logBrowser("http-error", request.url.toString(), "status=${response.statusCode}")
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                // Rotation detaches the UI briefly; allow safe server redirects to finish.
                if (!alive || leaving || clearing) return true
                val raw = request.url.toString()
                diagnostics.event("navigation", "redirect=${request.isRedirect}, allowed=${allowed(raw)}, url=${com.breakyuna.esjzone.util.diagnosticUrl(raw)}")
                val url = raw.toHttpUrlOrNull()
                val loginRedirect = Wenku8Urls.loginRedirectUrl(raw)
                if (url?.scheme == "http" && loginRedirect != null) {
                    if (attached) loadPage(loginRedirect) else pendingRedirect = loginRedirect
                    return true
                }
                // The verified form's original jumpurl uses this exact legacy HTTP home.
                if (url?.scheme == "http" && url.host == "www.wenku8.net" && url.port == 80 &&
                    url.username.isEmpty() && url.password.isEmpty() && url.encodedPath == "/index.php" && url.query == null) {
                    if (attached) loadPage("${Wenku8Urls.BASE}/index.php") else pendingRedirect = "${Wenku8Urls.BASE}/index.php"
                    return true
                }
                if (allowed(raw)) return false
                logBrowser("blocked-navigation", raw)
                if (attached) {
                    submitting = false; formReady = false
                    if (status != Wenku8LoginStatus.SUCCESS) status = Wenku8LoginStatus.INCOMPATIBLE
                }
                return true
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString().toHttpUrlOrNull()
                val permitted = url?.isHttps == true && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                    url.host in setOf("www.wenku8.net", "challenges.cloudflare.com", "www.cloudflare.com")
                diagnostics.resource(request, permitted)
                if (permitted) return null
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
        }
    }

    fun submit(username: String, password: String, duration: String) {
        assertMain()
        val view = browser ?: return
        if (!canSubmit || !allowed(view.url.orEmpty())) return
        diagnostics.event("submit-start", "status=$status, binding=$bindingGeneration, document=$documentGeneration")
        cancelChecks()
        submitting = true; formReady = false; status = Wenku8LoginStatus.SUBMITTING
        attemptStarted = true
        pendingDuration = duration
        startDeadline()
        val binding = bindingGeneration
        val document = documentGeneration
        submittedDocument = document
        try {
            view.evaluateJavascript(Wenku8LoginForm.submit(attemptId, username, password, duration)) { raw ->
                if (!active() || binding != bindingGeneration || document != documentGeneration) {
                    diagnostics.event("submit-callback-ignored", "active=${active()}, binding=$binding/$bindingGeneration, document=$document/$documentGeneration")
                    return@evaluateJavascript
                }
                when (val submissionState = runCatching { JSONObject(raw).optString("state") }.getOrNull()) {
                    "submitted" -> { diagnostics.event("submit-dispatched"); scheduleInspection() }
                    "invalid", "length" -> {
                        cancelChecks(); submitting = false; pendingDuration = null
                        attemptStarted = false
                        diagnostics.event("submit-validation-failed", "state=$submissionState")
                        formReady = true; status = Wenku8LoginStatus.INVALID
                    }
                    else -> {
                        cancelChecks(); submitting = false; pendingDuration = null
                        attemptStarted = submissionState != "incompatible"
                        diagnostics.event("submit-unconfirmed", "scriptRecognized=${submissionState == "incompatible"}")
                        status = Wenku8LoginStatus.INCOMPATIBLE
                    }
                }
            }
        } catch (error: Exception) {
            // Never attach raw JS, inputs or a browser exception to diagnostics.
            diagnostics.event("submit-script-error", "type=${error.javaClass.name}")
            cancelChecks(); submitting = false; pendingDuration = null
            status = Wenku8LoginStatus.UNKNOWN
        }
    }

    private fun inspect(revealFallback: Boolean = true) {
        assertMain()
        val view = browser ?: return
        if (!active() || documentLoading || !allowed(view.url.orEmpty())) return
        val binding = bindingGeneration
        val document = documentGeneration
        val inspection = ++inspectionGeneration
        view.evaluateJavascript(Wenku8LoginForm.inspect(attemptId)) { raw ->
            if (!active() || binding != bindingGeneration || document != documentGeneration || inspection != inspectionGeneration) {
                diagnostics.event("inspect-callback-ignored", "active=${active()}, binding=$binding/$bindingGeneration, document=$document/$documentGeneration, inspection=$inspection/$inspectionGeneration")
                return@evaluateJavascript
            }
            val result = runCatching { JSONObject(raw) }.getOrNull()
            // Only fixed status codes enter diagnostics, never raw JS results or document text.
            val state = result?.optString("state")?.takeIf {
                it in setOf("preparing", "manual", "signedIn", "form", "rejected", "unknown", "incompatible")
            } ?: "script-result"
            val reason = result?.optString("reason")?.takeIf {
                it in setOf("origin", "empty-document", "form-count", "action", "encoding", "fields",
                    "controls", "durations", "extra-input")
            }.orEmpty()
            val inspectionState = "state=$state, reason=$reason"
            diagnostics.event("inspect-result", "$inspectionState, status=$status, formReady=$formReady, submitting=$submitting, loading=$documentLoading")
            if (inspectionState != lastInspection) {
                lastInspection = inspectionState
                logBrowser("inspect", detail = inspectionState)
                diagnostics.snapshot(view, "inspection-changed")
            }
            val duration = result?.optString("duration")?.takeIf { it in SettingsDefaults.WENKU_LOGIN_DURATIONS }
            if (duration != null) attemptStarted = true
            // Navigation and transport errors do not revoke a confirmed browser identity.
            // A real login form can still show that the browser session has ended.
            if (status == Wenku8LoginStatus.SUCCESS && state != "form") {
                return@evaluateJavascript
            }
            when (result?.optString("state")) {
                "preparing" -> {
                    status = if (submitting) Wenku8LoginStatus.SUBMITTING else Wenku8LoginStatus.PREPARING
                    armTimeout()
                    scheduleInspection(revealFallback)
                }
                "manual" -> {
                    submitting = false; formReady = false; status = Wenku8LoginStatus.MANUAL
                    if (revealFallback) showWeb = true
                    scheduleInspection(revealFallback)
                }
                "signedIn" -> {
                    submitting = false; formReady = false
                    verifySession(duration ?: pendingDuration)
                }
                "form" -> {
                    if (submitting && submittedDocument == documentGeneration) {
                        // The old form can remain visible while its POST is starting.
                        scheduleInspection(revealFallback)
                        return@evaluateJavascript
                    }
                    cancelChecks(); formReady = true; submitting = false
                    status = if (duration != null || pendingDuration != null) Wenku8LoginStatus.UNKNOWN else Wenku8LoginStatus.READY
                }
                "rejected" -> {
                    cancelChecks(); submitting = false; formReady = false
                    status = Wenku8LoginStatus.REJECTED
                }
                else -> {
                    if (!httpError && (submitting || duration != null || pendingDuration != null) &&
                        SystemClock.elapsedRealtime() < deadline) {
                        // A successful POST may briefly show a notice before its home redirect.
                        submitting = true; formReady = false; status = Wenku8LoginStatus.SUBMITTING
                        armTimeout()
                        scheduleInspection(revealFallback)
                        return@evaluateJavascript
                    }
                    cancelChecks(); submitting = false; formReady = false
                    status = if (httpError) Wenku8LoginStatus.NETWORK else if (duration != null || pendingDuration != null)
                        Wenku8LoginStatus.UNKNOWN else Wenku8LoginStatus.INCOMPATIBLE
                }
            }
        }
    }

    private fun verifySession(actualDuration: String?) {
        if (!active() || status == Wenku8LoginStatus.SUCCESS || verification?.isActive == true) return
        cancelChecks()
        if (!bridgeBrowserSession()) { status = Wenku8LoginStatus.UNKNOWN; return }
        status = Wenku8LoginStatus.CHECKING
        val binding = bindingGeneration
        val document = documentGeneration
        verification = viewModelScope.launch {
            if (actualDuration != null && actualDuration != recordedDuration) {
                val recorded = PresentationAccess.client.recordWenkuBrowserLogin(actualDuration)
                if (!active() || binding != bindingGeneration || document != documentGeneration) return@launch
                if (!recorded) {
                    status = Wenku8LoginStatus.UNKNOWN; return@launch
                }
                recordedDuration = actualDuration
                PresentationAccess.settings.setWenkuLoginDuration(actualDuration)
            }
            if (!active() || binding != bindingGeneration || document != documentGeneration) return@launch
            status = Wenku8LoginStatus.SUCCESS
            pendingDuration = null
            browser?.evaluateJavascript(Wenku8LoginForm.clearPassword, null)
            browser?.clearHistory()
            showWeb = false
            diagnostics.event("browser-sign-in-confirmed", "durationObserved=${actualDuration != null}")
            // This request checks native transport separately; its failure is not a logout.
            diagnostics.event("session-check-start")
            val verified = try {
                withTimeout(35_000) {
                    cancellablePageRequest {
                        val response = PresentationAccess.client.getWenkuPage("${Wenku8Urls.BASE}/index.php",
                            Wenku8PageKind.HOME, allowAutoSolve = false, forceRefresh = true)
                        wenku8SignedInDocument(response.html, response.url)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics.event("session-check-error", "type=${error.javaClass.name}")
                false
            }
            diagnostics.event("session-check-end", "verified=$verified, active=${active()}, binding=$binding/$bindingGeneration")
        }
    }

    fun openWeb() {
        if (!active()) return
        browser?.let { diagnostics.snapshot(it, "open-web") }
        diagnostics.event("open-web-policy", "status=$status, formReady=$formReady, submitting=$submitting, attemptStarted=$attemptStarted")
        if (status.needsWebReload(formReady, submitting, attemptStarted)) {
            // An explicit user action retries failed preparation in the same WebView.
            reloadLoginPage(showWebAfterLoad = true)
        } else {
            showWeb = true
            if (!submitting) inspect()
        }
    }
    fun hideWeb() {
        if (active()) {
            diagnostics.event("show-native", "status=$status")
            showWeb = false
            poll?.let(main::removeCallbacks); poll = null
            if (!documentLoading) inspect(revealFallback = false)
        }
    }
    fun loadLoginPage() = reloadLoginPage(showWebAfterLoad = false)

    private fun reloadLoginPage(showWebAfterLoad: Boolean) {
        if (!active() || submitting || status == Wenku8LoginStatus.CHECKING) return
        diagnostics.event("reload-login", "showWeb=$showWebAfterLoad, status=$status")
        cancelChecks()
        pendingDuration = null; attemptStarted = false
        // Old attempt metadata can never turn the fresh login form into a stale result.
        attemptId = UUID.randomUUID().toString()
        browser?.evaluateJavascript(Wenku8LoginForm.clearPassword, null)
        status = Wenku8LoginStatus.PREPARING; formReady = false; showWeb = showWebAfterLoad
        startDeadline()
        loadPage("${Wenku8Urls.BASE}/login.php")
    }
    fun retryCheck() {
        if (!active() || status == Wenku8LoginStatus.CHECKING) return
        startDeadline()
        if (!documentLoading && allowed(browser?.url.orEmpty())) inspect()
        else if (!submitting) {
            status = Wenku8LoginStatus.PREPARING
            loadPage("${Wenku8Urls.BASE}/index.php")
        }
    }

    private fun startDeadline() {
        deadline = SystemClock.elapsedRealtime() + 40_000
        armTimeout()
    }
    private fun armTimeout() {
        timeout?.let(main::removeCallbacks)
        timeout = Runnable {
            if (active() && (submitting || status == Wenku8LoginStatus.PREPARING)) {
                logBrowser("timeout", detail = "documentLoading=$documentLoading")
                browser?.let { diagnostics.snapshot(it, "timeout") }
                cancelChecks(); submitting = false; formReady = false; status = Wenku8LoginStatus.UNKNOWN
            }
        }.also { main.postDelayed(it, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)) }
    }
    private fun scheduleInspection(revealFallback: Boolean = true) {
        poll?.let(main::removeCallbacks)
        if (active() && SystemClock.elapsedRealtime() < deadline) {
            poll = Runnable { if (active()) inspect(revealFallback) }.also { main.postDelayed(it, 750) }
        }
    }
    private fun cancelChecks() {
        poll?.let(main::removeCallbacks); poll = null
        timeout?.let(main::removeCallbacks); timeout = null
        verification?.cancel(); verification = null
        inspectionGeneration++
    }

    private fun bridgeBrowserSession(): Boolean {
        assertMain()
        val manager = CookieManager.getInstance()
        manager.flush()
        diagnostics.event("cookie-bridge", "status=$status, binding=$bindingGeneration, document=$documentGeneration")
        val root = "${Wenku8Urls.BASE}/"
        if (!PresentationAccess.client.importWenkuBrowserCookies(manager.getCookie(root), root)) return false
        val url = browser?.url?.takeIf { it != root && allowed(it) } ?: return true
        return PresentationAccess.client.importWenkuBrowserCookies(manager.getCookie(url), url)
    }

    private fun loadPage(url: String) {
        requestedUrl = url
        logBrowser("load", url)
        browser?.loadUrl(url)
    }

    private fun logBrowser(stage: String, actualUrl: String = browser?.url.orEmpty(), detail: String = "") {
        AppLogger.i("Wenku8LoginModel", "Login browser: stage=$stage, " +
            "requested=${wenkuDiagnosticUrl(requestedUrl)}, actual=${wenkuDiagnosticUrl(actualUrl)}, $detail")
    }

    fun clearSession() {
        if (!active()) return
        diagnostics.event("clear-session")
        cancelChecks(); bindingGeneration++
        clearing = true; submitting = false; formReady = false
        pendingDuration = null; attemptStarted = false; recordedDuration = null; status = Wenku8LoginStatus.PREPARING
        pendingRedirect = null; attemptId = UUID.randomUUID().toString()
        browser?.stopLoading()
        browser?.evaluateJavascript(Wenku8LoginForm.clearPassword, null)
        browser?.loadUrl("about:blank")
        PresentationAccess.client.clearWenkuSession {
            if (alive && !leaving) {
                clearing = false; initialized = false; documentLoading = false; showWeb = false
                if (attached) (browserContext?.baseContext as? Activity)?.let(::attach)
            }
        }
    }

    fun prepareToLeave() {
        if (leaving) return
        diagnostics.event("leave", "status=$status, loading=$documentLoading, attemptStarted=$attemptStarted")
        leaving = true
        cancelChecks(); bindingGeneration++
        browser?.stopLoading()
        browser?.evaluateJavascript(Wenku8LoginForm.clearPassword, null)
        // The successful native check can rotate cookies asynchronously into CookieManager.
        // Do not overwrite its newer encrypted cookies with a second, possibly older snapshot.
        if (browser != null && !clearing && status != Wenku8LoginStatus.SUCCESS) bridgeBrowserSession()
    }
    override fun onCleared() {
        assertMain()
        if (!leaving) prepareToLeave()
        alive = false
        detach()
        browser?.destroy()
        browser = null
    }

    private fun allowed(raw: String): Boolean = raw.toHttpUrlOrNull()?.let {
        it.isHttps && it.host == "www.wenku8.net" && it.port == 443 && it.username.isEmpty() && it.password.isEmpty()
    } == true
}
