package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageResponsePolicy
import com.breakyuna.esjzone.network.external.CloudflareChallenge
import com.breakyuna.esjzone.network.external.ExternalChapterHtml
import com.breakyuna.esjzone.network.external.ExternalChapterParseException
import com.breakyuna.esjzone.network.external.UnsupportedExternalChapterException
import com.breakyuna.esjzone.network.external.wenkuDiagnosticUrl
import com.breakyuna.esjzone.network.wenku8.Wenku8PageKind
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WenkuChapterTest {
    private val url = "https://www.wenku8.net/novel/2/2552/96772.htm"

    @Test fun rejectedPageDiagnosticsKeepContextWithoutUrlSecrets() {
        val actual = "https://synthetic-user:synthetic-pass@www.wenku8.net/index.php?__cf_chl_rt_tk=synthetic-token&page=2#synthetic-fragment"
        val message = UnsupportedExternalChapterException(
            "browser-final-url", Wenku8PageKind.HOME, "https://www.wenku8.net/index.php", actual
        ).message.orEmpty()
        assertTrue(message.contains("stage=browser-final-url, kind=HOME"))
        assertTrue(message.contains("https://www.wenku8.net/index.php"))
        assertTrue(message.contains("queryNames=[__cf_chl_rt_tk, page]"))
        listOf("synthetic-user", "synthetic-pass", "synthetic-token", "synthetic-fragment").forEach {
            assertFalse(message.contains(it))
        }
        assertEquals("invalid-url", wenkuDiagnosticUrl("invalid synthetic-secret"))
        assertEquals("unavailable", wenkuDiagnosticUrl(null))
    }

    @Test fun sourceAndIdentity() {
        assertEquals(ChapterSource.ESJ_ZONE, resolveChapterSource("/forum/1552200797/123.html"))
        assertEquals(ChapterSource.WENKU8, resolveChapterSource(url))
        assertEquals(ChapterSource.WENKU8,
            resolveChapterSource("http://www.wenku8.net/novel/2/2552/96772.htm"))
        assertEquals(ChapterSource.WENKU8, resolveChapterSource("96773.htm", url))
        assertEquals(ChapterSource.UNSUPPORTED_EXTERNAL,
            resolveChapterSource("https://wenku8.net.example.com/novel/2/2552/96772.htm"))
        assertEquals(ChapterSource.UNSUPPORTED_EXTERNAL, resolveChapterSource("javascript:alert(1)"))
        assertEquals(ChapterSource.UNSUPPORTED_EXTERNAL, resolveChapterSource("https://example.org/chapter"))
        assertTrue(EsjzoneUrls.canonicalPageKey(url).startsWith("https://www.wenku8.net/"))
        assertFalse(EsjzoneUrls.canonicalPageKey(url).contains("__cf_chl_"))
    }

    @Test fun continueReadingKeepsFirstSupportedChapterAndHistory() {
        val first = Chapter("first", url, false)
        val later = Chapter("later", "/forum/1552200797/123.html", false)
        val unsupported = Chapter("other", "https://example.org/chapter", false)
        assertEquals(first, NovelChapterList(listOf(ChapterItem(first), ChapterItem(later))).toRead)
        assertEquals(later.copy(isHistory = true), NovelChapterList(listOf(
            ChapterItem(first), ChapterItem(later.copy(isHistory = true)))).toRead)
        assertEquals(first, NovelChapterList(listOf(ChapterItem(unsupported), ChapterItem(first))).toRead)
        assertEquals(listOf(first, later), NovelChapterList(listOf(
            ChapterItem(first), ChapterItem(later))).orderedChapters)
    }

    @Test fun gbkAndDeclaredCharsets() {
        val chinese = "<html><head><meta charset=GBK></head><body><div id=content>中文正文测试</div></body></html>"
        assertEquals(chinese, ExternalChapterHtml.decode(chinese.toByteArray(charset("GBK")), null))
        val utf8 = "<html><head><meta charset=GBK></head><body>正文</body></html>"
        assertEquals(utf8, ExternalChapterHtml.decode(utf8.toByteArray(), "text/html; charset=UTF-8"))
        assertEquals(chinese, ExternalChapterHtml.decode(chinese.toByteArray(charset("GBK")),
            "text/html; charset=unknown-charset"))
        val equiv = chinese.replace("<meta charset=GBK>",
            "<meta http-equiv='Content-Type' content='text/html; charset=GBK'>")
        assertEquals(equiv, ExternalChapterHtml.decode(equiv.toByteArray(charset("GBK")), null))
    }

    @Test fun gb18030ExtendsDeclaredGbkWithoutChangingUtf8() {
        val text = "<meta charset=GBK><div id=content>扩展字符𠀀与中文正文</div>"
        assertEquals(text, ExternalChapterHtml.decode(text.toByteArray(charset("GB18030")), null))
        assertEquals(text, ExternalChapterHtml.decode(text.toByteArray(charset("GB18030")), "text/html; charset=GB2312"))
        val undeclared = "中文𠀀正文"
        assertEquals(undeclared, ExternalChapterHtml.decode(undeclared.toByteArray(charset("GB18030")), null))
        assertEquals(undeclared, ExternalChapterHtml.decode(undeclared.toByteArray(), "text/html; charset=UTF-8"))
    }

    @Test fun parserCleansBodyAndResolvesLinks() {
        val body = "<html><body><div id=title>第一章</div><div id=content>" +
            "<p>这是第一段正文，长度足以成为有效章节。</p><script>tracking()</script>" +
            "<a href=96773.htm>下一页</a><img src=96772.jpg></div></body></html>"
        val detail = ExternalChapterHtml.parse(body, Chapter("fallback", url, false), url)
        assertEquals("第一章", detail.name)
        assertTrue(detail.contentHtml!!.contains("正文"))
        assertFalse(detail.contentHtml!!.contains("tracking"))
        assertFalse(detail.contentHtml!!.contains("下一页"))
        assertTrue(detail.contentHtml!!.contains("https://www.wenku8.net/novel/2/2552/96772.jpg"))
        assertEquals("https://www.wenku8.net/novel/2/2552/96773.htm", detail.next?.url)
        assertThrows(ExternalChapterParseException::class.java) {
            ExternalChapterHtml.parse("<html><body>missing</body></html>", Chapter("x", url, false), url)
        }
    }

    @Test fun imageOnlyChapterPassesCachePolicyAndParser() {
        val html = "<html><body><div id=title>彩页</div><div id=content>" +
            "<img src='96772.jpg'></div></body></html>"
        assertTrue(PageResponsePolicy.validate(200, html, url, kind = PageKind.EXTERNAL_CHAPTER).trusted)
        val detail = ExternalChapterHtml.parse(html, Chapter("彩页", url, false), url)
        assertEquals("彩页", detail.name)
        assertTrue(detail.contentHtml!!.contains("https://www.wenku8.net/novel/2/2552/96772.jpg"))
    }

    @Test fun footerCannotJumpIntoAnotherBook() {
        val html = "<div id=content>这是足够长的正文内容，用于验证章节页不会跨作品跳转。</div>" +
            "<a href='/novel/2/2553/1.htm'>下一页</a>"
        assertEquals(null, ExternalChapterHtml.parse(html, Chapter("章", url, false), url).next)
    }

    @Test fun footerNavigationWinsOverHeaderLinks() {
        val html = "<html><body><a href='96773.htm'>下一页</a>" +
            "<div id=content>正文内容足够长，用于验证目录缺失时页尾链接作为降级导航。</div>" +
            "<a href='100485.htm'>下一页</a></body></html>"
        val detail = ExternalChapterHtml.parse(html, Chapter("章节", url, false), url)
        assertEquals("https://www.wenku8.net/novel/2/2552/100485.htm", detail.next?.url)
    }

    @Test fun challengeIsNotOrdinaryHttpFailureAndCannotCache() {
        val challenge = "<html><body><title>Just a moment...</title>" +
            "<script src='/cdn-cgi/challenge-platform/start.js'></script></body></html>"
        assertTrue(CloudflareChallenge.isChallenge(403, "challenge", "cloudflare", challenge))
        assertTrue(CloudflareChallenge.isChallenge(403, null, "cloudflare", challenge))
        assertTrue(CloudflareChallenge.isChallenge(200, null, "cloudflare", challenge))
        assertFalse(CloudflareChallenge.isChallenge(403, null, null, "Forbidden"))
        assertFalse(CloudflareChallenge.isChallenge(429, null, "cloudflare", "rate limited"))
        assertFalse(CloudflareChallenge.isChallenge(500, null, "cloudflare", "server failure"))
        assertFalse(PageResponsePolicy.validate(200, challenge, url, kind = PageKind.EXTERNAL_CHAPTER).trusted)
        assertTrue(CloudflareChallenge.hasChallengeDocumentMarkers(challenge))
    }

    @Test fun cloudflareScriptDoesNotHideReadableChapter() {
        val html = "<html><head><title>序章</title></head><body>" +
            "<div id=content>这是可以正常阅读的章节正文，长度足以通过正文校验。</div>" +
            "<script src='/cdn-cgi/challenge-platform/scripts/jsd/main.js'></script>" +
            "</body></html>"
        assertFalse(CloudflareChallenge.hasChallengeDocumentMarkers(html))
        assertFalse(CloudflareChallenge.isChallenge(200, null, "cloudflare", html))
        assertTrue(PageResponsePolicy.validate(200, html, url, kind = PageKind.EXTERNAL_CHAPTER).trusted)
        assertTrue(CloudflareChallenge.isChallenge(200, "challenge", "cloudflare", html))
        val chapterWithCaptchaHeading = html.replace(
            "<div id=content>", "<div id=content><h1>验证码的历史与原理</h1>"
        )
        assertFalse(CloudflareChallenge.hasChallengeDocumentMarkers(chapterWithCaptchaHeading))
        val activeChallenge = html.replace("<div id=content>", "<div id=challenge-stage></div><div id=content>")
        assertTrue(CloudflareChallenge.hasChallengeDocumentMarkers(activeChallenge))
        assertFalse(PageResponsePolicy.validate(200, activeChallenge, url,
            kind = PageKind.EXTERNAL_CHAPTER).trusted)
        val challengeScriptWithContent = html.replace(
            "/scripts/jsd/main.js", "/scripts/chl_page/v1"
        )
        assertTrue(CloudflareChallenge.hasChallengeDocumentMarkers(challengeScriptWithContent))
        val challengeWithContent = html.replace("<div id=content>",
            "<h1>Verify you are human</h1><div id=content>")
        assertTrue(CloudflareChallenge.hasChallengeDocumentMarkers(challengeWithContent))
        assertFalse(PageResponsePolicy.validate(200, challengeWithContent, url,
            kind = PageKind.EXTERNAL_CHAPTER).trusted)
    }

    @Test fun browserChapterCanBeCachedForNativeReader() {
        val browserHtml = "<html><head><title>序章</title></head><body>" +
            "<div id=title>序章</div><div id=content>" +
            "<p>这是浏览器已经加载完成的章节正文，可供原生阅读器使用。</p>" +
            "<img src='96772.jpg'><script>tracking()</script></div>" +
            "<script src='/cdn-cgi/challenge-platform/scripts/jsd/main.js'></script>" +
            "</body></html>"
        assertTrue(PageResponsePolicy.validate(200, browserHtml, url,
            kind = PageKind.EXTERNAL_CHAPTER).trusted)
        val detail = ExternalChapterHtml.parse(browserHtml, Chapter("fallback", url, false), url)
        val cached = ExternalChapterHtml.cacheDocument(detail, url)
        assertFalse(cached.contains("<script"))
        assertTrue(PageResponsePolicy.validate(200, cached, url,
            kind = PageKind.EXTERNAL_CHAPTER).trusted)
        val restored = ExternalChapterHtml.parse(cached, Chapter("fallback", url, false), url)
        assertEquals(detail.name, restored.name)
        assertEquals(detail.contentHtml, restored.contentHtml)
    }

    @Test fun copyrightNoticeCannotBeParsedAsChapterBody() {
        val url = "https://www.wenku8.net/novel/2/2552/1.htm"
        assertThrows(com.breakyuna.esjzone.network.wenku8.Wenku8RestrictedException::class.java) {
            ExternalChapterHtml.parse("<div id=title>Notice</div><div id=content>因版权问题，本作品已经停止提供在线阅读，请不要将此通知保存为章节正文。</div>",
                Chapter("Notice", url, false), url)
        }
    }
}
