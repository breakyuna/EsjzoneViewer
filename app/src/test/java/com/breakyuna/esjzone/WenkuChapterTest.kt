package com.breakyuna.esjzone

import android.content.SharedPreferences
import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageResponsePolicy
import com.breakyuna.esjzone.network.external.CloudflareChallenge
import com.breakyuna.esjzone.network.external.ExternalChapterHtml
import com.breakyuna.esjzone.network.external.ExternalChapterParseException
import com.breakyuna.esjzone.network.external.UnsupportedExternalChapterException
import com.breakyuna.esjzone.network.external.WenkuChapterClient
import com.breakyuna.esjzone.network.external.WenkuCookieJar
import com.breakyuna.esjzone.network.external.isAllowedWenkuImageUrl
import com.breakyuna.esjzone.network.external.isWenkuImageHost
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.ui.page.WenkuVerificationPolicy
import com.breakyuna.esjzone.ui.reader.toReaderDocument
import okhttp3.Cookie
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WenkuChapterTest {
    private val url = "https://www.wenku8.net/novel/2/2552/96772.htm"

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

    @Test fun waitsForChapterWhileClearanceIsAvailable() {
        val policy = WenkuVerificationPolicy(acceptChapterContent = true)

        assertEquals(
            WenkuVerificationPolicy.Decision.WAIT,
            policy.next(hasReadableChapter = false, hasClearance = true, nowElapsedMillis = 1_000)
        )
        assertEquals(
            WenkuVerificationPolicy.Decision.WAIT,
            policy.next(hasReadableChapter = false, hasClearance = true, nowElapsedMillis = 5_000)
        )
        assertEquals(
            WenkuVerificationPolicy.Decision.USE_CHAPTER_CONTENT,
            policy.next(hasReadableChapter = true, hasClearance = true, nowElapsedMillis = 6_000)
        )
    }

    @Test fun clearanceGraceCanExtendCheckAndThenFallsBack() {
        val policy = WenkuVerificationPolicy(acceptChapterContent = true)

        assertEquals(
            WenkuVerificationPolicy.Decision.WAIT,
            policy.next(hasReadableChapter = false, hasClearance = true, nowElapsedMillis = 59_000)
        )
        assertTrue(policy.shouldContinue(nowElapsedMillis = 61_000, checkStartedAtMillis = 1_000))
        assertEquals(
            WenkuVerificationPolicy.Decision.USE_CLEARANCE,
            policy.next(hasReadableChapter = false, hasClearance = true, nowElapsedMillis = 79_000)
        )

        val noClearance = WenkuVerificationPolicy(acceptChapterContent = true)
        assertEquals(
            WenkuVerificationPolicy.Decision.WAIT,
            noClearance.next(hasReadableChapter = false, hasClearance = false, nowElapsedMillis = 61_000)
        )
        assertFalse(noClearance.shouldContinue(nowElapsedMillis = 61_000, checkStartedAtMillis = 1_000))
    }

    @Test fun downloadFlowAcceptsExistingClearanceWithoutChapterCapture() {
        val policy = WenkuVerificationPolicy(acceptChapterContent = false)
        assertEquals(
            WenkuVerificationPolicy.Decision.USE_CLEARANCE,
            policy.next(hasReadableChapter = false, hasClearance = true, nowElapsedMillis = 1_000)
        )
    }

    @Test fun allowsLegitimateWenkuImageHostsAndRejectsUntrustedImages() {
        assertTrue(isWenkuImageHost("www.wenku8.net"))
        assertTrue(isWenkuImageHost("pic.wenku8.com"))
        assertTrue(isWenkuImageHost("pic.wenku8.net"))
        assertTrue(isWenkuImageHost("img.wenku8.com"))
        assertTrue(isWenkuImageHost("wenku8.com"))
        assertTrue(isWenkuImageHost("wenku8.net"))
        assertFalse(isWenkuImageHost("wenku8.net.example.com"))
        assertFalse(isWenkuImageHost("evilwenku8.com"))
        assertFalse(isWenkuImageHost(".wenku8.net"))

        assertTrue(isAllowedWenkuImageUrl("https://pic.wenku8.com/pictures/2/2552/96772/1.jpg"))
        assertTrue(isAllowedWenkuImageUrl("https://pic.wenku8.net/pictures/2/2552/96772/2.jpg"))
        assertFalse(isAllowedWenkuImageUrl("http://pic.wenku8.com/pictures/2/2552/96772/1.jpg"))
        assertFalse(isAllowedWenkuImageUrl("https://user:pass@pic.wenku8.com/1.jpg"))
        assertFalse(isAllowedWenkuImageUrl("https://wenku8.com.example.com/1.jpg"))

        val html = "<html><body><div id=title>插图</div><div id=content>" +
            "<p>这是一段带有多个图床插图的正文内容，用于验证过滤策略。</p>" +
            "<img src='https://pic.wenku8.com/pictures/2/2552/1.jpg'>" +
            "<img src='http://img.wenku8.com/pictures/2/2552/2.jpg'>" +
            "<img src='//pic.wenku8.net/pictures/2/2552/3.jpg'>" +
            "<img src='https://wenku8.com/cover.jpg'>" +
            "<img src='https://wenku8.net/cover2.jpg'>" +
            "<img data-src='https://pic.wenku8.com/lazy.jpg' src='https://wenku8.net/placeholder.jpg'>" +
            "<img data-original='https://example.org/untrusted.jpg' src='/fallback.jpg'>" +
            "<img src='https://wenku8.net.example.com/bad.jpg'>" +
            "<img src='https://evilwenku8.com/bad2.jpg'>" +
            "<img src='https://user:pass@pic.wenku8.com/bad3.jpg'>" +
            "<img src='data:image/png;base64,AAAA'>" +
            "<img src='javascript:alert(1)'>" +
            "</div></body></html>"
        val parsed = ExternalChapterHtml.parse(html, Chapter("插图", url, false), url)
        val resultHtml = parsed.contentHtml.orEmpty()
        assertTrue(resultHtml.contains("https://pic.wenku8.com/pictures/2/2552/1.jpg"))
        assertTrue(resultHtml.contains("https://img.wenku8.com/pictures/2/2552/2.jpg"))
        assertTrue(resultHtml.contains("https://pic.wenku8.net/pictures/2/2552/3.jpg"))
        assertTrue(resultHtml.contains("https://wenku8.com/cover.jpg"))
        assertTrue(resultHtml.contains("https://wenku8.net/cover2.jpg"))
        assertTrue(resultHtml.contains("https://pic.wenku8.com/lazy.jpg"))
        assertTrue(resultHtml.contains("https://www.wenku8.net/fallback.jpg"))
        assertFalse(resultHtml.contains("placeholder.jpg"))
        assertFalse(resultHtml.contains("example.org/untrusted.jpg"))
        assertFalse(resultHtml.contains("data-original"))
        assertFalse(resultHtml.contains("wenku8.net.example.com"))
        assertFalse(resultHtml.contains("evilwenku8.com"))
        assertFalse(resultHtml.contains("user:pass"))
        assertFalse(resultHtml.contains("data:image"))
        assertFalse(resultHtml.contains("javascript:"))
    }

    @Test fun wenkuCookieJarPersistsOnlyCloudflareCookiesAndClearsLegacyEntries() {
        val prefs = InMemorySharedPreferences(
            mutableMapOf(
                "__androidx_security_crypto_encrypted_prefs_key_keyset__" to "keyset",
                "__androidx_security_crypto_encrypted_prefs_value_keyset__" to "valueset",
                "encryptedKey" to "AbCdEfGh==",
                "legacy_int" to 1
            )
        )
        val jar = WenkuCookieJar(prefs)
        val originUrl = "https://www.wenku8.net/novel/2/2552/96772.htm".toHttpUrl()
        assertTrue(prefs.all.isEmpty())
        assertTrue(jar.loadForRequest(originUrl).isEmpty())

        // Even without keyset markers, orphaned ciphertext/non-String entries cannot trigger exceptions or bogus cookies.
        prefs.edit()
            .putString("cipherKey", "AbCdEfGh==")
            .putInt("legacy_int", 42)
            .putString("www.wenku8.net|/|session", "session=fixture; path=/; secure")
            .apply()
        assertTrue(jar.loadForRequest(originUrl).isEmpty())
        assertTrue(prefs.all.isEmpty())

        val future = System.currentTimeMillis() + 60_000L
        val past = System.currentTimeMillis() - 60_000L
        jar.saveFromResponse(
            originUrl,
            listOf(
                Cookie.Builder().hostOnlyDomain("www.wenku8.net").path("/").secure().httpOnly()
                    .name("cf_clearance").value("valid-token").expiresAt(future).build(),
                Cookie.Builder().hostOnlyDomain("www.wenku8.net").path("/").secure().httpOnly()
                    .name("ews_key").value("secret-key").expiresAt(future).build(),
                Cookie.Builder().hostOnlyDomain("www.wenku8.net").path("/").secure().httpOnly()
                    .name("ews_token").value("secret-token").expiresAt(future).build(),
                Cookie.Builder().hostOnlyDomain("www.wenku8.net").path("/").secure().httpOnly()
                    .name("_cfuvid").value("old").expiresAt(past).build(),
                Cookie.Builder().hostOnlyDomain("www.wenku8.net").path("/").secure().httpOnly()
                    .name("session").value("fixture").expiresAt(future).build()
            )
        )
        jar.importBrowserCookies("__cf_bm=browser-fixture; session=browser-session-fixture")

        val sent = jar.loadForRequest(originUrl)
        assertEquals(setOf("cf_clearance", "__cf_bm"), sent.map { it.name }.toSet())
        assertEquals(2, prefs.all.size)
        assertTrue(jar.loadForRequest("http://www.wenku8.net/novel/2/2552/96772.htm".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://pic.wenku8.com/1.jpg".toHttpUrl()).isEmpty())
    }

    @Test fun savedWenkuImagesKeepBlockPositionsAndUseOnlyAllowedRemoteUrlsOrDownloadedFiles() {
        val chapter = Chapter("插图", url, false)
        val remote = "https://pic.wenku8.com/illustration.png"
        val oldRemote = "https://example.org/old.png"
        val unavailable = "https://example.org/unavailable.png"
        val local = "file:/downloads/image.png"
        val body = ChapterBody.fromBlocks(listOf(
            ReaderBlock.Image(remote), ReaderBlock.Image(oldRemote), ReaderBlock.Image(unavailable)
        ))
        val detail = DetailedChapter(chapter.name, emptyList(), null, null, body = body,
            imageLocations = mapOf(oldRemote to local, unavailable to unavailable))

        val document = detail.toReaderDocument(chapter)
        assertEquals(body.fingerprint, document.contentFingerprint)
        assertEquals(listOf(ReaderBlock.Image(remote), ReaderBlock.Image(local), ReaderBlock.Image("")), document.blocks)
    }

    @Test fun wenkuImageClientChecksTheUrlBeforeTransport() {
        val client = WenkuChapterClient.createHttpClient(WenkuCookieJar(InMemorySharedPreferences()))
        val imageClient = WenkuChapterClient.createImageClient(client, "TestUA/1.0") { Long.MAX_VALUE }
        var transportCalls = 0
        val stubClient = imageClient.newBuilder().addInterceptor { chain ->
            transportCalls++
            assertEquals("TestUA/1.0", chain.request().header("User-Agent"))
            assertEquals("https://www.wenku8.net/", chain.request().header("Referer"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("image".toResponseBody()).build()
        }.build()

        assertEquals(Dns.SYSTEM, client.dns)
        assertEquals(Dns.SYSTEM, imageClient.dns)
        assertFalse(client.followRedirects)
        assertFalse(imageClient.followRedirects)
        for (url in listOf("https://example.org/cover.jpg", "http://pic.wenku8.com/cover.jpg")) {
            assertThrows(UnsupportedExternalChapterException::class.java) {
                stubClient.newCall(Request.Builder().url(url).build()).execute().close()
            }
        }
        assertEquals(0, transportCalls)
        stubClient.newCall(Request.Builder().url("https://pic.wenku8.com/cover.jpg").build()).execute().use { response ->
            assertEquals(200, response.code)
        }
        assertEquals(1, transportCalls)
    }

    private class InMemorySharedPreferences(
        initial: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {
        private val data: MutableMap<String, Any?> = initial.toMutableMap()

        override fun getAll(): MutableMap<String, *> = synchronized(data) { data.toMutableMap() }
        override fun getString(key: String?, defValue: String?): String? =
            synchronized(data) { (data[key] as? String) ?: defValue }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            synchronized(data) { (data[key] as? MutableSet<String>) ?: defValues }
        override fun getInt(key: String?, defValue: Int): Int =
            synchronized(data) { (data[key] as? Int) ?: defValue }
        override fun getLong(key: String?, defValue: Long): Long =
            synchronized(data) { (data[key] as? Long) ?: defValue }
        override fun getFloat(key: String?, defValue: Float): Float =
            synchronized(data) { (data[key] as? Float) ?: defValue }
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            synchronized(data) { (data[key] as? Boolean) ?: defValue }
        override fun contains(key: String?): Boolean = synchronized(data) { key != null && data.containsKey(key) }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private var cleared = false
            private val removed = mutableSetOf<String>()
            private val updates = mutableMapOf<String, Any?>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) {
                    if (value == null) remove(key) else { removed.remove(key); updates[key] = value }
                }
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) {
                    if (values == null) remove(key) else { removed.remove(key); updates[key] = values.toMutableSet() }
                }
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) { removed.remove(key); updates[key] = value }
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) { removed.remove(key); updates[key] = value }
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) { removed.remove(key); updates[key] = value }
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) { removed.remove(key); updates[key] = value }
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) { updates.remove(key); removed.add(key) }
            }
            override fun clear(): SharedPreferences.Editor = apply {
                cleared = true
                removed.clear()
                updates.clear()
            }
            override fun commit(): Boolean {
                synchronized(data) {
                    if (cleared) data.clear()
                    removed.forEach { data.remove(it) }
                    updates.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value }
                }
                return true
            }
            override fun apply() { commit() }
        }
    }
}
