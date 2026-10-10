package com.breakyuna.esjzone.ui.page

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.external.WenkuChapterClient
import com.breakyuna.esjzone.network.external.WenkuCookieJar
import com.breakyuna.esjzone.network.wenku8.Wenku8Urls
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Local documents and a synthetic native challenge; no site or account requests. */
@RunWith(AndroidJUnit4::class)
class WenkuLoginStateInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun nativeChallengeDoesNotUndoBrowserSignIn() = withModel { model, view, nativeStarted, releaseNative ->
        composeRule.runOnIdle { model.openWeb() }
        load(view, SIGNED_IN, HOME)
        assertTrue("Native check did not start", nativeStarted.await(10, TimeUnit.SECONDS))
        composeRule.runOnIdle {
            assertEquals(Wenku8LoginStatus.SUCCESS, model.status)
            assertFalse(model.showWeb)
            assertFalse(model.canSubmit)
        }
        releaseNative.countDown()
        composeRule.waitUntil(10_000) { onMain { !verification(model).isActive } }
        composeRule.runOnIdle {
            assertEquals(Wenku8LoginStatus.SUCCESS, model.status)
            assertFalse(model.showWeb)
        }
    }

    @Test fun intermediateSubmissionPageKeepsNativePanelUntilRedirect() = withModel { model, view, nativeStarted, releaseNative ->
        load(view, FORM, LOGIN)
        composeRule.waitUntil(10_000) { onMain { model.canSubmit } }
        composeRule.runOnIdle {
            model.submit("fixture-user", "fixture-password", PresentationAccess.settings.wenkuLoginDurationFlow.value)
        }
        load(view, "<html><body>正在跳转</body></html>", LOGIN)
        composeRule.waitUntil(10_000) {
            onMain { field(Wenku8LoginModel::class.java, "lastInspection").get(model).toString().startsWith("state=unknown") }
        }
        composeRule.runOnIdle {
            assertEquals(Wenku8LoginStatus.SUBMITTING, model.status)
            assertTrue(model.submitting)
            assertFalse(model.showWeb)
            assertFalse(model.canSubmit)
        }
        load(view, SIGNED_IN, HOME)
        assertTrue("Native check did not start after redirect", nativeStarted.await(10, TimeUnit.SECONDS))
        releaseNative.countDown()
        composeRule.runOnIdle {
            assertEquals(Wenku8LoginStatus.SUCCESS, model.status)
            assertFalse(model.showWeb)
        }
    }

    @Test fun actualLoginFormAfterSignInRestoresNativeLoginInputs() = withModel { model, view, nativeStarted, releaseNative ->
        load(view, SIGNED_IN, HOME)
        assertTrue("Native check did not start", nativeStarted.await(10, TimeUnit.SECONDS))
        releaseNative.countDown()
        load(view, FORM, LOGIN)
        composeRule.waitUntil(10_000) { onMain { model.canSubmit } }
        composeRule.runOnIdle {
            assertEquals(Wenku8LoginStatus.READY, model.status)
            assertFalse(model.showWeb)
        }
    }

    private fun withModel(block: (Wenku8LoginModel, WebView, CountDownLatch, CountDownLatch) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val nativeStarted = CountDownLatch(1)
        val releaseNative = CountDownLatch(1)
        val jar = WenkuCookieJar(context)
        jar.clear()
        val client = WenkuChapterClient(context, "Synthetic test agent")
        field(WenkuChapterClient::class.java, "client").set(client, OkHttpClient.Builder().addInterceptor { chain ->
            nativeStarted.countDown()
            check(releaseNative.await(10, TimeUnit.SECONDS)) { "Synthetic native check was not released" }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(403).message("Synthetic challenge")
                .body("<html><title>Just a moment</title><body><div id='challenge-form'></div></body></html>"
                    .toResponseBody("text/html".toMediaType())).build()
        }.build())
        val clientField = field(EsjzoneClient::class.java, "wenkuClient")
        val previousClient = clientField.get(EsjzoneClient)
        clientField.set(EsjzoneClient, client)
        var model: Wenku8LoginModel? = null
        try {
            lateinit var view: WebView
            composeRule.runOnIdle {
                val created = Wenku8LoginModel(context)
                model = created
                // Use local documents instead of navigating to the website during attach.
                field(Wenku8LoginModel::class.java, "initialized").setBoolean(created, true)
                view = created.attach(composeRule.activity)
            }
            block(requireNotNull(model), view, nativeStarted, releaseNative)
        } finally {
            releaseNative.countDown()
            composeRule.runOnIdle {
                model?.let {
                    it.prepareToLeave()
                    it.detach()
                    (field(Wenku8LoginModel::class.java, "browser").get(it) as? WebView)?.destroy()
                }
            }
            clientField.set(EsjzoneClient, previousClient)
            jar.clear()
        }
    }

    private fun load(view: WebView, html: String, url: String) = composeRule.runOnIdle {
        view.loadDataWithBaseURL(url, html, "text/html", "UTF-8", url)
    }

    private fun verification(model: Wenku8LoginModel): Job =
        field(Wenku8LoginModel::class.java, "verification").get(model) as Job

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return requireNotNull(result)
    }

    private fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }

    private companion object {
        const val HOME = "${Wenku8Urls.BASE}/index.php"
        const val LOGIN = "${Wenku8Urls.BASE}/login.php"
        const val SIGNED_IN = "<html><body><p>欢迎您</p><a href='/logout.php'>退出登录</a></body></html>"
        const val FORM = """
            <html><body><form name="frmlogin" method="post" action="/login.php?do=submit"
                onsubmit="event.preventDefault()">
                <input type="text" name="username" maxlength="30">
                <input type="password" name="password" maxlength="30">
                <select name="usecookie"><option value="0">0</option><option value="86400">1</option>
                    <option value="2592000">2</option><option value="315360000">3</option></select>
                <input type="submit" name="submit" value="fixture-submit">
            </form></body></html>
        """
    }
}
