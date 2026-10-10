package com.breakyuna.esjzone

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.network.wenku8.Wenku8LoginForm
import com.breakyuna.esjzone.network.wenku8.Wenku8Urls
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Chromium validation of a synthetic local form; no site requests or account data. */
@RunWith(AndroidJUnit4::class)
class WenkuLoginFormInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun invalidFormClearsPasswordWithoutSubmitting() = withForm("pattern='[0-9]+'") { view ->
        assertEquals("invalid", submit(view).getString("state"))
        val fields = evaluate(view, "({empty:document.frmlogin.password.value==='',submitted:!!window.fixtureSubmitted})")
        assertTrue(fields.getBoolean("empty"))
        assertFalse(fields.getBoolean("submitted"))
    }

    @Test fun lengthFailureNeverFillsPassword() = withForm("maxlength='2'") { view ->
        assertEquals("length", submit(view).getString("state"))
        val fields = evaluate(view, "({empty:document.frmlogin.password.value==='',submitted:!!window.fixtureSubmitted})")
        assertTrue(fields.getBoolean("empty"))
        assertFalse(fields.getBoolean("submitted"))
    }

    @Test fun validFormUsesOriginalSubmitterAndCleanupRemovesTemporaryData() = withForm("") { view ->
        assertEquals("submitted", submit(view).getString("state"))
        val submitted = evaluate(view, """
            ({submitted:!!window.fixtureSubmitted, original:!!window.fixtureOriginal,
              attempt:sessionStorage.getItem('__esjWenkuLoginAttempt')==='fixture-attempt:86400'})
        """.trimIndent())
        assertTrue(submitted.getBoolean("submitted"))
        assertTrue(submitted.getBoolean("original"))
        assertTrue(submitted.getBoolean("attempt"))
        evaluate(view, "(function(){${Wenku8LoginForm.clearPassword};return {};})()")
        val cleared = evaluate(view, """
            ({empty:document.frmlogin.password.value==='',
              removed:sessionStorage.getItem('__esjWenkuLoginAttempt')===null})
        """.trimIndent())
        assertTrue(cleared.getBoolean("empty"))
        assertTrue(cleared.getBoolean("removed"))
    }

    private fun submit(view: WebView): JSONObject = evaluate(view,
        Wenku8LoginForm.submit("fixture-attempt", "fixture-user", "fixture-password", "86400"))

    private fun evaluate(view: WebView, script: String): JSONObject {
        val done = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { result = it; done.countDown() }
        }
        assertTrue("Local script did not finish", done.await(10, TimeUnit.SECONDS))
        return JSONObject(requireNotNull(result))
    }

    private fun withForm(usernameAttributes: String, block: (WebView) -> Unit) {
        val loaded = CountDownLatch(1)
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            view = WebView(instrumentation.context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) { loaded.countDown() }
                }
                loadDataWithBaseURL("${Wenku8Urls.BASE}/login.php", """
                    <html><body><form name="frmlogin" method="post" action="${Wenku8Urls.BASE}/login.php?do=submit"
                        onsubmit="event.preventDefault();window.fixtureSubmitted=true;
                        window.fixtureOriginal=event.submitter.name==='submit' &amp;&amp;
                        event.submitter.value==='fixture-submit' &amp;&amp;
                        this.elements.password.value==='fixture-password' &amp;&amp;
                        this.elements.usecookie.value==='86400';">
                        <input type="text" name="username" $usernameAttributes>
                        <input type="password" name="password" maxlength="30">
                        <select name="usecookie"><option value="0">0</option><option value="86400">1</option>
                        <option value="2592000">2</option><option value="315360000">3</option></select>
                        <input type="submit" name="submit" value="fixture-submit">
                    </form></body></html>
                """.trimIndent(), "text/html", "UTF-8", null)
            }
        }
        try {
            assertTrue("Local document did not load", loaded.await(10, TimeUnit.SECONDS))
            block(view)
        } finally {
            instrumentation.runOnMainSync { view.destroy() }
        }
    }
}
