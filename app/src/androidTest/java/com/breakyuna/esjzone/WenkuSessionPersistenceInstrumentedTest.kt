package com.breakyuna.esjzone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.network.external.WenkuCookieJar
import com.breakyuna.esjzone.network.external.WenkuChapterClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the test APK's private storage and synthetic cookies only. */
@RunWith(AndroidJUnit4::class)
class WenkuSessionPersistenceInstrumentedTest {
    @Test fun processOnlyPolicyClearsSnapshotsAtClientStartupWhilePersistentPolicyRestoresThem() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val root = "https://www.wenku8.net/".toHttpUrl()
        for (duration in listOf("0", "86400", "2592000", "315360000")) {
            val jar = WenkuCookieJar(context)
            jar.clear()
            jar.importBrowserCookies("sample=policy-fixture", root.toString())
            val loginClient = WenkuChapterClient(context, "Synthetic test agent")
            runBlocking { withContext(Dispatchers.Main) { loginClient.recordBrowserLogin(duration) } }
            assertEquals(duration == "0", WenkuCookieJar(context).processSessionFromPreviousRun)
            val client = WenkuChapterClient(context, "Synthetic test agent")
            try {
                val restored = CountDownLatch(1)
                instrumentation.runOnMainSync { client.restoreBrowserCookies { restored.countDown() } }
                assertTrue("Cookie restore did not finish", restored.await(10, TimeUnit.SECONDS))
                val restarted = WenkuCookieJar(context)
                assertEquals(duration == "0", restarted.loadForRequest(root).isEmpty())
                assertTrue(!restarted.processSessionFromPreviousRun)
            } finally {
                val cleared = CountDownLatch(1)
                client.clearSession { cleared.countDown() }
                assertTrue("Cookie cleanup did not finish", cleared.await(10, TimeUnit.SECONDS))
            }
        }
    }

    @Test fun browserSessionSurvivesJarRecreationAndClearRemovesIt() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val root = "https://www.wenku8.net/".toHttpUrl()
        val chapter = root.resolve("novel/2/2552/1.htm")!!
        val first = WenkuCookieJar(context)
        first.clear()
        try {
            first.importBrowserCookies("sample=root-fixture", root.toString())
            first.importBrowserCookies("sample=chapter-fixture", chapter.toString())
            val restored = WenkuCookieJar(context)
            assertEquals("chapter-fixture", restored.loadForRequest(chapter).single().value)
            assertEquals("root-fixture", restored.loadForRequest(root).single().value)
            restored.clear()
            assertTrue(WenkuCookieJar(context).loadForRequest(chapter).isEmpty())
        } finally {
            WenkuCookieJar(context).clear()
        }
    }

    @Test fun nativeRotationAndDeletionDoNotRestoreOldBrowserValues() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val root = "https://www.wenku8.net/".toHttpUrl()
        val jar = WenkuCookieJar(context)
        jar.clear()
        try {
            jar.importBrowserCookies("sample=old-fixture", root.toString())
            val rotated = Cookie.Builder().hostOnlyDomain(root.host).path("/").secure()
                .name("sample").value("new-fixture").expiresAt(4_000_000_000_000L).build()
            jar.saveFromResponse(root, listOf(rotated))
            val restored = WenkuCookieJar(context)
            assertEquals("new-fixture", restored.loadForRequest(root).single().value)
            val deleted = Cookie.Builder().hostOnlyDomain(root.host).path("/").secure()
                .name("sample").value("").expiresAt(0).build()
            restored.saveFromResponse(root, listOf(deleted))
            assertTrue(WenkuCookieJar(context).loadForRequest(root).isEmpty())
        } finally {
            WenkuCookieJar(context).clear()
        }
    }
}
