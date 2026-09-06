package com.breakyuna.esjzone

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.breakyuna.esjzone.network.PersistentCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Device-only keystore jar contract; retained for CI/instrumentation execution. */
@RunWith(AndroidJUnit4::class)
class PersistentCookieJarIsolationTest {

    @Test
    fun persistedSessionDoesNotCrossRegistrableDomains() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val jar = PersistentCookieJar(context)
        val host = "fixture-esjzone.cc"
        val url = "https://$host/".toHttpUrl()
        try {
            jar.clear(host)
            jar.saveFromResponse(
                url,
                listOf(
                    Cookie.Builder().domain(host).path("/").name("ews_key")
                        .value("fixture-key").secure().build(),
                    Cookie.Builder().domain(host).path("/").name("ews_token")
                        .value("fixture-token").secure().build()
                )
            )
            assertEquals(2, jar.loadForRequest(url).size)
            assertTrue(
                jar.loadForRequest("https://fixture-esjzone.one/".toHttpUrl()).isEmpty()
            )
        } finally {
            jar.clear(host)
        }
    }
}
