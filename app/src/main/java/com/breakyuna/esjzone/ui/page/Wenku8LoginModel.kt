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
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.wenku8.Wenku8LoginForm
import com.breakyuna.esjzone.network.wenku8.Wenku8PageKind
import com.breakyuna.esjzone.network.wenku8.Wenku8Urls
import com.breakyuna.esjzone.network.wenku8.wenku8SignedInDocument
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
    INVALID(R.string.wenku8_login_invalid)
}

/** Navigation-entry lifetime only. No password state and no process-owned Activity. */
internal class Wenku8LoginModel(context: Context) : ViewModel() {
    private val applicationContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
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
    private var pendingHomeRedirect = false
    private var documentLoading = false
    private var httpError = false
    private var deadline = 0L
    private var pendingDuration: String? = null
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
        attached = true
        val binding = ++bindingGeneration
        browserContext?.baseContext = activity
        val view = browser ?: createBrowser(activity).also { browser = it }
        if (!initialized && !clearing && !leaving) {
            startDeadline()
            PresentationAccess.client.restoreWenkuBrowserCookies {
                if (active() && binding == bindingGeneration && !initialized) {
                    initialized = true
                    // Existing sessions go through the real protected homepage first.
                    view.loadUrl("${Wenku8Urls.BASE}/index.php")
                }
            }
        } else if (active() && pendingHomeRedirect) {
            pendingHomeRedirect = false
            view.loadUrl("${Wenku8Urls.BASE}/index.php")
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
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (!alive || leaving || clearing) return
                documentGeneration++
                documentLoading = true
                httpError = false
                if (!active()) return
                cancelChecks()
                formReady = false
                status = if (submitting) Wenku8LoginStatus.SUBMITTING else Wenku8LoginStatus.PREPARING
                startDeadline()
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (!alive || leaving || clearing) return
                if (view.url != url) return
                documentLoading = false
                if (allowed(url) && status !in setOf(Wenku8LoginStatus.CHECKING, Wenku8LoginStatus.SUCCESS) &&
                    verification?.isActive != true) bridgeBrowserSession()
                if (active() && allowed(url)) inspect()
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && active()) {
                    httpError = true
                    cancelChecks(); submitting = false; formReady = false; status = Wenku8LoginStatus.NETWORK
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && alive && !leaving && !clearing) httpError = true
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                // Rotation detaches the UI briefly; allow safe server redirects to finish.
                if (!alive || leaving || clearing) return true
                val raw = request.url.toString()
                val url = raw.toHttpUrlOrNull()
                // The verified form's original jumpurl uses this exact legacy HTTP home.
                if (url?.scheme == "http" && url.host == "www.wenku8.net" && url.port == 80 &&
                    url.username.isEmpty() && url.password.isEmpty() && url.encodedPath == "/index.php" && url.query == null) {
                    if (attached) view.loadUrl("${Wenku8Urls.BASE}/index.php") else pendingHomeRedirect = true
                    return true
                }
                if (allowed(raw)) return false
                if (attached) {
                    submitting = false; formReady = false; status = Wenku8LoginStatus.INCOMPATIBLE
                }
                return true
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString().toHttpUrlOrNull()
                if (url?.isHttps == true && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                    url.host in setOf("www.wenku8.net", "challenges.cloudflare.com", "www.cloudflare.com")) return null
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
        }
    }

    fun submit(username: String, password: String, duration: String) {
        assertMain()
        val view = browser ?: return
        if (!canSubmit || !allowed(view.url.orEmpty())) return
        cancelChecks()
        submitting = true; formReady = false; status = Wenku8LoginStatus.SUBMITTING
        pendingDuration = duration
        startDeadline()
        val binding = bindingGeneration
        val document = documentGeneration
        submittedDocument = document
        try {
            view.evaluateJavascript(Wenku8LoginForm.submit(attemptId, username, password, duration)) { raw ->
                if (!active() || binding != bindingGeneration || document != documentGeneration) return@evaluateJavascript
                when (runCatching { JSONObject(raw).optString("state") }.getOrNull()) {
                    "submitted" -> scheduleInspection()
                    "invalid", "length" -> {
                        cancelChecks(); submitting = false; pendingDuration = null
                        formReady = true; status = Wenku8LoginStatus.INVALID
                    }
                    else -> {
                        cancelChecks(); submitting = false; pendingDuration = null
                        status = Wenku8LoginStatus.INCOMPATIBLE; showWeb = true
                    }
                }
            }
        } catch (_: Exception) {
            // Never attach raw JS, inputs or a browser exception to diagnostics.
            cancelChecks(); submitting = false; pendingDuration = null
            status = Wenku8LoginStatus.UNKNOWN; showWeb = true
        }
    }

    private fun inspect() {
        assertMain()
        val view = browser ?: return
        if (!active() || documentLoading || !allowed(view.url.orEmpty())) return
        val binding = bindingGeneration
        val document = documentGeneration
        val inspection = ++inspectionGeneration
        view.evaluateJavascript(Wenku8LoginForm.inspect(attemptId)) { raw ->
            if (!active() || binding != bindingGeneration || document != documentGeneration || inspection != inspectionGeneration)
                return@evaluateJavascript
            val result = runCatching { JSONObject(raw) }.getOrNull()
            val duration = result?.optString("duration")?.takeIf { it in SettingsDefaults.WENKU_LOGIN_DURATIONS }
            when (result?.optString("state")) {
                "preparing" -> scheduleInspection()
                "manual" -> {
                    submitting = false; formReady = false; status = Wenku8LoginStatus.MANUAL; showWeb = true
                    scheduleInspection()
                }
                "signedIn" -> {
                    submitting = false; formReady = false
                    verifySession(duration ?: pendingDuration)
                }
                "form" -> {
                    if (submitting && submittedDocument == documentGeneration) {
                        // The old form can remain visible while its POST is starting.
                        scheduleInspection()
                        return@evaluateJavascript
                    }
                    cancelChecks(); formReady = true; submitting = false
                    status = if (duration != null || pendingDuration != null) Wenku8LoginStatus.UNKNOWN else Wenku8LoginStatus.READY
                    if (status == Wenku8LoginStatus.UNKNOWN) showWeb = true
                }
                "rejected" -> {
                    cancelChecks(); submitting = false; formReady = false
                    status = Wenku8LoginStatus.REJECTED; showWeb = true
                }
                else -> {
                    cancelChecks(); submitting = false; formReady = false; showWeb = true
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
                    status = Wenku8LoginStatus.UNKNOWN; showWeb = true; return@launch
                }
                recordedDuration = actualDuration
                PresentationAccess.settings.setWenkuLoginDuration(actualDuration)
            }
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
            } catch (_: Exception) {
                false
            }
            if (active() && binding == bindingGeneration && document == documentGeneration) {
                status = if (verified) Wenku8LoginStatus.SUCCESS else Wenku8LoginStatus.UNKNOWN
                if (verified) {
                    pendingDuration = null
                    browser?.evaluateJavascript(Wenku8LoginForm.clearPassword, null)
                    browser?.clearHistory()
                    showWeb = false
                }
            }
        }
    }

    fun openWeb() { if (active()) { showWeb = true; if (!submitting) inspect() } }
    fun hideWeb() {
        if (active()) {
            showWeb = false
            poll?.let(main::removeCallbacks); poll = null
        }
    }
    fun loadLoginPage() {
        if (!active() || submitting || status == Wenku8LoginStatus.CHECKING) return
        cancelChecks()
        pendingDuration = null
        // Old attempt metadata can never turn the fresh login form into a stale result.
        attemptId = UUID.randomUUID().toString()
        browser?.evaluateJavascript(Wenku8LoginForm.clearPassword, null)
        status = Wenku8LoginStatus.PREPARING; formReady = false; showWeb = false
        startDeadline()
        browser?.loadUrl("${Wenku8Urls.BASE}/login.php")
    }
    fun retryCheck() {
        if (!active() || status == Wenku8LoginStatus.CHECKING) return
        startDeadline()
        if (!documentLoading && allowed(browser?.url.orEmpty())) inspect()
        else if (!submitting) {
            status = Wenku8LoginStatus.PREPARING
            browser?.loadUrl("${Wenku8Urls.BASE}/index.php")
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
                submitting = false; formReady = false; status = Wenku8LoginStatus.UNKNOWN; showWeb = true
            }
        }.also { main.postDelayed(it, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)) }
    }
    private fun scheduleInspection() {
        poll?.let(main::removeCallbacks)
        if (active() && SystemClock.elapsedRealtime() < deadline) {
            poll = Runnable { if (active()) inspect() }.also { main.postDelayed(it, 750) }
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
        val root = "${Wenku8Urls.BASE}/"
        if (!PresentationAccess.client.importWenkuBrowserCookies(manager.getCookie(root), root)) return false
        val url = browser?.url?.takeIf { it != root && allowed(it) } ?: return true
        return PresentationAccess.client.importWenkuBrowserCookies(manager.getCookie(url), url)
    }

    fun clearSession() {
        if (!active()) return
        cancelChecks(); bindingGeneration++
        clearing = true; submitting = false; formReady = false
        pendingDuration = null; recordedDuration = null; status = Wenku8LoginStatus.PREPARING
        pendingHomeRedirect = false; attemptId = UUID.randomUUID().toString()
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
