package com.breakyuna.esjzone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.network.external.WenkuCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the test APK's private storage and synthetic cookies only. */
@RunWith(AndroidJUnit4::class)
class WenkuSessionPersistenceInstrumentedTest {
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
