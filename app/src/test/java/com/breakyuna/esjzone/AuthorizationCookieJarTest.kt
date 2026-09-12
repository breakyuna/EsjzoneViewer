package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM contract for the legacy/session fallback.  The persistent implementation
 * is Android-keystore backed; these tests still pin the security boundary that
 * must hold before a persistent jar is available.
 */
class AuthorizationCookieJarTest {

    @Test
    fun cleartextRequestsNeverReceiveSessionCookies() {
        val jar = AuthorizationCookieJar(
            Authorization("key-fixture", "token-fixture", "www.esjzone.cc"),
            persistResponses = false
        )
        assertTrue(jar.loadForRequest("http://www.esjzone.cc/".toHttpUrl()).isEmpty())
    }

    @Test
    fun credentialsAreSentOnlyToTheIssuingDomain() {
        val jar = AuthorizationCookieJar(
            Authorization("key-fixture", "token-fixture", "www.esjzone.cc"),
            persistResponses = false
        )

        val sameHost = jar.loadForRequest("https://www.esjzone.cc/forum/1/1.html".toHttpUrl())
        assertEquals(
            mapOf("ews_key" to "key-fixture", "ews_token" to "token-fixture"),
            sameHost.associate { it.name to it.value }
        )
        assertTrue(
            jar.loadForRequest("https://esjzone.one/forum/1/1.html".toHttpUrl()).isEmpty()
        )
        assertTrue(
            jar.loadForRequest("https://cdn.esjzone.one/assets/cover.jpg".toHttpUrl()).isEmpty()
        )
    }

    @Test
    fun aliasesOfTheIssuingHostRemainEquivalentButBlankCredentialsDoNotCreateCookies() {
        val aliasJar = AuthorizationCookieJar(
            Authorization("key-fixture", "token-fixture", "esjzone.cc"),
            persistResponses = false
        )
        assertEquals(
            2,
            aliasJar.loadForRequest("https://www.esjzone.cc/".toHttpUrl()).size
        )

        listOf(
            Authorization("", "token-fixture", "esjzone.cc"),
            Authorization("key-fixture", "", "esjzone.cc"),
            Authorization("null", "token-fixture", "esjzone.cc")
        ).forEach { authorization ->
            assertTrue(
                AuthorizationCookieJar(authorization, persistResponses = false)
                    .loadForRequest("https://esjzone.cc/".toHttpUrl())
                    .isEmpty()
            )
        }
    }
}
