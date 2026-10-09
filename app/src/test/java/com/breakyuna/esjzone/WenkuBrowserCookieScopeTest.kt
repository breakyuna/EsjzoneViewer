package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.external.wenkuBrowserCookiesForRequest
import com.breakyuna.esjzone.network.external.wenkuBrowserCookieDeletionTargets
import com.breakyuna.esjzone.network.external.encodeWenkuBrowserSnapshots
import com.breakyuna.esjzone.network.external.decodeWenkuBrowserSnapshots
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic cookie pairs; no account or live browser data. */
class WenkuBrowserCookieScopeTest {
    private val root = "https://www.wenku8.net/".toHttpUrl()
    private val chapter = "https://www.wenku8.net/novel/2/2552/1.htm".toHttpUrl()

    @Test fun savedBrowserSnapshotsRestoreValuesAndExactUrlScopes() {
        val snapshots = mapOf(root to mapOf("sample" to "root-value"),
            chapter to mapOf("sample" to "chapter-value"))
        val restored = decodeWenkuBrowserSnapshots(encodeWenkuBrowserSnapshots(snapshots))
        assertEquals(snapshots, restored)
        assertEquals("chapter-value", wenkuBrowserCookiesForRequest(chapter, restored, emptyList()).single().value)
        assertEquals("root-value", wenkuBrowserCookiesForRequest(chapter.resolve("2.htm")!!, restored, emptyList()).single().value)
    }

    @Test fun restoredSnapshotsRejectOtherOriginsAndEsjCookies() {
        val snapshots = mapOf(root to mapOf("sample" to "fixture", "ews_key" to "fixture"),
            "https://example.org/".toHttpUrl() to mapOf("sample" to "fixture"))
        assertEquals(mapOf(root to mapOf("sample" to "fixture")),
            decodeWenkuBrowserSnapshots(encodeWenkuBrowserSnapshots(snapshots)))
        assertTrue(decodeWenkuBrowserSnapshots("invalid-json").isEmpty())
    }

    @Test fun chapterSnapshotDoesNotEscapeToSearchOrAnotherChapter() {
        val snapshots = mapOf(chapter to mapOf("sample" to "chapter-value"))
        assertEquals("chapter-value", wenkuBrowserCookiesForRequest(chapter, snapshots, emptyList()).single().value)
        assertTrue(wenkuBrowserCookiesForRequest(root.resolve("modules/article/search.php")!!, snapshots, emptyList()).isEmpty())
        assertTrue(wenkuBrowserCookiesForRequest(chapter.resolve("2.htm")!!, snapshots, emptyList()).isEmpty())
    }

    @Test fun rootSnapshotAppliesWithinTheSameHttpsOrigin() {
        val snapshots = mapOf(root to mapOf("sample" to "root-value"))
        assertEquals("root-value", wenkuBrowserCookiesForRequest(chapter, snapshots, emptyList()).single().value)
        listOf("http://www.wenku8.net/", "https://www.wenku8.net:444/", "https://example.org/").forEach {
            assertTrue(wenkuBrowserCookiesForRequest(it.toHttpUrl(), snapshots, emptyList()).isEmpty())
        }
    }

    @Test fun matchingNativeCookieRetainsItsAttributes() {
        val native = Cookie.Builder().hostOnlyDomain(root.host).path("/novel/2/2552")
            .name("sample").value("chapter-value").secure().httpOnly().expiresAt(4_000_000_000_000L).build()
        val result = wenkuBrowserCookiesForRequest(chapter,
            mapOf(chapter to mapOf("sample" to "chapter-value")), listOf(native))
        assertEquals(listOf(native), result)
        assertTrue(result.single().httpOnly)
        assertEquals("/novel/2/2552", result.single().path)
    }

    @Test fun exactSnapshotOverridesRootWithoutRemovingUnrelatedNativeCookies() {
        val native = Cookie.Builder().hostOnlyDomain(root.host).path("/")
            .name("unrelated").value("native-value").secure().build()
        val snapshots = mapOf(chapter to mapOf("sample" to "chapter-value", "unrelated" to "native-value"),
            root to mapOf("sample" to "root-value"))
        val result = wenkuBrowserCookiesForRequest(chapter, snapshots, listOf(native))
        assertEquals(mapOf("unrelated" to "native-value", "sample" to "chapter-value"), result.associate { it.name to it.value })
    }

    @Test fun missingRootCookieDoesNotRestoreAStaleNativeSession() {
        val stale = Cookie.Builder().hostOnlyDomain(root.host).path("/")
            .name("old-sample").value("old-value").secure().build()
        val scoped = Cookie.Builder().hostOnlyDomain(root.host).path("/novel/2/2552")
            .name("scoped-sample").value("scoped-value").secure().build()
        assertEquals(listOf(scoped), wenkuBrowserCookiesForRequest(chapter, mapOf(root to emptyMap()), listOf(stale, scoped)))
        assertTrue(wenkuBrowserCookiesForRequest(chapter, mapOf(chapter to emptyMap()), listOf(stale, scoped)).isEmpty())
    }

    @Test fun deletionTargetsCoverCapturedPathScopesAndStayWithinWenku() {
        val targets = wenkuBrowserCookieDeletionTargets(mapOf(chapter to mapOf("sample" to "sample-value")))
        assertTrue(targets.any { it.hostOnly && it.path == "/novel/2/2552/" })
        assertTrue(targets.any { !it.hostOnly && it.domain == "wenku8.net" && it.path == "/" })
        assertTrue(targets.all { it.expiresAt <= 0 && it.secure && it.domain in setOf(root.host, "wenku8.net") })
        assertTrue(wenkuBrowserCookieDeletionTargets(mapOf("https://example.org/path".toHttpUrl() to mapOf("sample" to "sample-value"))).isEmpty())
    }
}
