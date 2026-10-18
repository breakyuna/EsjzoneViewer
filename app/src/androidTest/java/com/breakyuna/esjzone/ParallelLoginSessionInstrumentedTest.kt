package com.breakyuna.esjzone

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.PersistentCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses separate preferences and synthetic sessions; never touches the installed account. */
@RunWith(AndroidJUnit4::class)
class ParallelLoginSessionInstrumentedTest {
    private val firstHost = "www.esjzone.cc"
    private val secondHost = "www.esjzone.one"

    @Test
    fun backgroundMirrorKeepsTheFirstMirrorsAccountAndActiveRequests() {
        val jar = isolatedJar()
        jar.clear()
        try {
            assertTrue(jar.loginSessionChanges.value.isEmpty())
            val firstEpoch = requireNotNull(commit(jar, firstHost, jar.sessionEpoch(), continuing = false))
            assertEquals(mapOf(firstHost to 1L), jar.loginSessionChanges.value)
            val identity = jar.activeAccountIdentity()
            assertEquals(2, jar.loadForRequest("https://$firstHost/".toHttpUrl(), firstEpoch).size)

            assertEquals(firstEpoch, commit(jar, secondHost, firstEpoch, continuing = true))
            assertEquals(mapOf(firstHost to 1L, secondHost to 1L), jar.loginSessionChanges.value)
            assertEquals(identity, jar.activeAccountIdentity())
            assertTrue(jar.isActiveSession(firstHost))
            assertTrue(jar.isActiveSession(secondHost))
            assertEquals(2, jar.loadForRequest("https://$firstHost/".toHttpUrl(), firstEpoch).size)
            // Even unchanged credentials must notify a screen that previously failed to load.
            assertEquals(firstEpoch, commit(jar, secondHost, firstEpoch, continuing = true))
            assertEquals(mapOf(firstHost to 1L, secondHost to 2L), jar.loginSessionChanges.value)
            jar.saveFromResponse("https://$secondHost/".toHttpUrl(), listOf(
                Cookie.Builder().hostOnlyDomain(secondHost).path("/").name("ews_token")
                    .value("fixture-rotated-token").secure().build()
            ))
            assertEquals(2L, jar.loginSessionChanges.value[secondHost])
            assertEquals(firstEpoch, jar.sessionEpoch())
        } finally {
            jar.clear()
        }
    }

    @Test
    fun logoutRejectsTheBackgroundMirrorsLateSession() {
        val jar = isolatedJar()
        jar.clear()
        try {
            val firstEpoch = requireNotNull(commit(jar, firstHost, jar.sessionEpoch(), continuing = false))
            jar.clear()

            assertNull(commit(jar, secondHost, firstEpoch, continuing = true))
            assertEquals(mapOf(firstHost to 1L), jar.loginSessionChanges.value)
            assertNull(jar.activeAccountIdentity())
            assertTrue(jar.loadForRequest("https://$secondHost/".toHttpUrl()).isEmpty())
        } finally {
            jar.clear()
        }
    }

    private fun commit(jar: PersistentCookieJar, host: String, epoch: Long, continuing: Boolean): Long? {
        val authorization = Authorization("fixture-key-$host", "fixture-token-$host", host)
        val cookies = listOf(
            Cookie.Builder().hostOnlyDomain(host).path("/").name("ews_key")
                .value(authorization.ewsKey).secure().build(),
            Cookie.Builder().hostOnlyDomain(host).path("/").name("ews_token")
                .value(authorization.ewsToken).secure().build()
        )
        return jar.commitLoginSession(
            "https://$host/".toHttpUrl(), "reader@example.test", cookies, authorization, epoch, continuing
        )
    }

    private fun isolatedJar(): PersistentCookieJar {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("parallel_login_fixture_$name", mode)
            override fun deleteSharedPreferences(name: String): Boolean =
                base.deleteSharedPreferences("parallel_login_fixture_$name")
        }
        return PersistentCookieJar(context)
    }
}
