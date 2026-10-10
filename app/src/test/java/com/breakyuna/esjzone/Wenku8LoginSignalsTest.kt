package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.wenku8.wenku8SignedInDocument
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic result signals only; these fixtures do not prove real account sign-in. */
class Wenku8LoginSignalsTest {
    private val home = "https://www.wenku8.net/index.php"

    @Test fun requiresBothWelcomeAndAnActualSameOriginSignOutLink() {
        assertTrue(wenku8SignedInDocument("<p>欢迎您</p><a href='/logout.php'>退出登录</a>", home))
        assertFalse(wenku8SignedInDocument("<p>欢迎访问</p><a href='/login.php'>登录</a>", home))
        assertFalse(wenku8SignedInDocument("<a href='/logout.php'>退出登录</a>", home))
        assertFalse(wenku8SignedInDocument("<p>欢迎您，退出登录</p>", home))
    }

    @Test fun rejectsOffOriginAndCredentialBearingSignOutLinks() {
        for (href in listOf("https://example.org/logout.php", "http://www.wenku8.net/logout.php",
            "https://www.wenku8.net:444/logout.php", "https://user@www.wenku8.net/logout.php",
            "//fixture-user@www.wenku8.net/logout.php")) {
            assertFalse(href, wenku8SignedInDocument("<p>欢迎您</p><a href='$href'>退出登录</a>", home))
        }
    }
}
