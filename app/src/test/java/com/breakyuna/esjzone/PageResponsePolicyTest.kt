package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageResponsePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageResponsePolicyTest {
    private val url = "https://www.esjzone.cc/"

    @Test
    fun rejectsNonSuccessAndEmptyResponses() {
        assertFalse(PageResponsePolicy.validate(403, validHome(), url, kind = PageKind.HOME).trusted)
        assertFalse(PageResponsePolicy.validate(200, "", url, kind = PageKind.HOME).trusted)
    }

    @Test
    fun rejectsWafChallengeAndLoginPagesBeforeCaching() {
        val challenge = """
            <!doctype html><html><head><title>Just a moment...</title>
            <script src="/cdn-cgi/challenge-platform/scripts/chl_page/v1"></script></head>
            <body><div id="challenge-stage"><h1>Verify you are human</h1></div></body></html>
        """.trimIndent()
        val denied = "<html><body><h1>Access denied</h1>Cloudflare</body></html>"
        val login = """
            <html><body><form class="login-box">
                <input name="pwd" type="password"><a href="/my/login">Login</a>
            </form></body></html>
        """.trimIndent()

        assertFalse(PageResponsePolicy.validate(200, challenge, url).trusted)
        assertFalse(PageResponsePolicy.validate(200, denied, url).trusted)
        assertFalse(PageResponsePolicy.validate(200, login, url).trusted)
        assertTrue(PageResponsePolicy.looksLikeBlockedOrLoginPage(challenge))
        assertTrue(PageResponsePolicy.looksLikeBlockedOrLoginPage(denied))
    }

    @Test
    fun acceptsEsjCloudflareAssetsAndUserAuthoredSecurityWords() {
        val home = """
            <!doctype html><html><head>
              <title>ESJZone</title>
              <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.3.3/css/bootstrap.min.css">
            </head><body>
              <nav><a href="/forum/">Forum</a></nav>
              <section><a href="/detail/123.html">Novel</a>
                <p>This discussion explains captcha, forbidden words, and access denied errors.</p>
              </section>
              <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
            </body></html>
        """.trimIndent()

        assertTrue(PageResponsePolicy.validate(200, home, url, kind = PageKind.HOME).trusted)
        assertFalse(PageResponsePolicy.looksLikeBlockedOrLoginPage(home))
    }

    @Test
    fun rejectsStructurallyUnrelatedHtmlButAcceptsLegitimateEmptyForum() {
        val unrelated = "<html><body><main>proxy response</main></body></html>"
        val emptyForum = """
            <html><body><nav><a href="/forum/">Forum</a></nav>
            <section><table><tbody></tbody></table></section></body></html>
        """.trimIndent()

        assertFalse(PageResponsePolicy.validate(200, unrelated, url).trusted)
        assertTrue(
            PageResponsePolicy.validate(
                200,
                emptyForum,
                "https://www.esjzone.cc/forum/",
                kind = PageKind.FORUM
            ).trusted
        )
    }

    @Test
    fun fallsBackToValidatedStaleBodyOnAnUntrustedResponse() {
        val stale = validHome()
        val blocked = "<html><body><h1>Request blocked</h1></body></html>"
        val validation = PageResponsePolicy.validate(200, blocked, url, kind = PageKind.HOME)

        assertTrue(validation.trusted.not())
        assertTrue(
            PageResponsePolicy.selectTrustedBody(validation, blocked, stale) == stale
        )
        assertTrue(
            PageResponsePolicy.selectTrustedBody(
                PageResponsePolicy.validate(200, stale, url, kind = PageKind.HOME),
                stale,
                null
            ) == stale
        )
        assertEquals(
            stale,
            PageResponsePolicy.selectTrustedBody(validation, blocked, stale)
        )
    }

    @Test
    fun rejectsWrongPageFamilyWithoutRequiringNovelRows() {
        val shell = """
            <html><body><nav><a href="/detail/1.html">A</a></nav>
            <section><div>empty</div></section></body></html>
        """.trimIndent()

        assertFalse(
            PageResponsePolicy.validate(
                200,
                shell,
                "https://www.esjzone.cc/forum/123/",
                kind = PageKind.FORUM
            ).trusted
        )
        assertTrue(
            PageResponsePolicy.validate(
                200,
                shell,
                "https://www.esjzone.cc/list-11",
                kind = PageKind.LIST
            ).trusted
        )
    }

    private fun validHome(): String = """
        <html><head><title>ESJZone</title></head><body><section><a href="/detail/123.html">Novel</a></section>
        </body></html>
    """.trimIndent()
}
