package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageResponsePolicy
import org.junit.Assert.assertFalse
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
        val challenge = "<html><body><h1>Access denied</h1>Cloudflare</body></html>"
        val login = """
            <html><body><form class="login-box">
                <input name="pwd" type="password"><a href="/my/login">Login</a>
            </form></body></html>
        """.trimIndent()

        assertFalse(PageResponsePolicy.validate(200, challenge, url).trusted)
        assertFalse(PageResponsePolicy.validate(200, login, url).trusted)
        assertTrue(PageResponsePolicy.looksLikeBlockedOrLoginPage(challenge))
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
        <html><body><section><a href="/detail/123.html">Novel</a></section>
        </body></html>
    """.trimIndent()
}
