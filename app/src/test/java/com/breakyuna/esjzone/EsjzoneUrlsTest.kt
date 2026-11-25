package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.features.favoritePageUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EsjzoneUrlsTest {

    @Test
    fun upgradesCleartextWebViewLinksWithoutChangingTheirTarget() {
        assertEquals(
            "https://example.test/read?chapter=2#part",
            EsjzoneUrls.normalizeHttpUrl("http://example.test:80/read?chapter=2#part")
        )
    }

    @Test
    fun coverOrEmpty_filtersPlaceholdersAndResolvesValidUrls() {
        val placeholders = listOf(
            "https://www.esjzone.cc/assets/img/empty_cover.jpg?t=123",
            "https://www.esjzone.cc/assets/img/nophoto.png",
            "/assets/img/no-image",
            "/assets/img/placeholder.webp",
            "https://i.pinimg.com/564x/86/1e/51/861e5157abc25f92f6b49af0f1465927.jpg",
            "https://i.pinimg.com/236x/86/1e/51/861e5157abc25f92f6b49af0f1465927.webp?t=123",
            "https://images.example.com/covers/861e5157abc25f92f6b49af0f1465927.png"
        )
        placeholders.forEach { url -> assertEquals("", EsjzoneUrls.coverOrEmpty(url)) }

        assertEquals(
            "https://www.esjzone.cc/uploads/covers/book.jpg",
            EsjzoneUrls.coverOrEmpty("/uploads/covers/book.jpg")
        )
        val pinterestCover = "https://i.pinimg.com/564x/aa/bb/cc/aabbccddeeff00112233445566778899.jpg"
        assertEquals(pinterestCover, EsjzoneUrls.coverOrEmpty(pinterestCover))
        val fileUri = "file:///data/user/0/com.breakyuna.esjzone/files/bookmark_covers/cover_123.jpg"
        assertEquals(fileUri, EsjzoneUrls.coverOrEmpty(fileUri))
    }

    @Test
    fun coverUrlFromNovelCard_prefersNonPlaceholderLazyAttributes() {
        val dataOriginalCard = Jsoup.parse(
            """
            <div class="novel-card">
              <a><img src="/assets/img/empty_cover.jpg" data-original="/uploads/covers/book.jpg"></a>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst(".novel-card")
        assertEquals(
            "https://www.esjzone.cc/uploads/covers/book.jpg",
            EsjzoneUrls.coverUrlFromNovelCard(dataOriginalCard)
        )

        val fallbackLazyCard = Jsoup.parse(
            """
            <div class="novel-card">
              <a><img data-src="/assets/img/nophoto.png" data-lazy-src="/uploads/covers/book.jpg"></a>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst(".novel-card")
        assertEquals(
            "https://www.esjzone.cc/uploads/covers/book.jpg",
            EsjzoneUrls.coverUrlFromNovelCard(fallbackLazyCard)
        )
    }

    @Test
    fun coverUrlFromNovelCard_returnsEmptyForPlaceholderCards() {
        val placeholderImgCard = Jsoup.parse(
            """<div><a><img src="/assets/img/empty_cover.jpg"></a></div>""",
            "https://www.esjzone.cc/"
        ).selectFirst("div")
        assertTrue(EsjzoneUrls.coverUrlFromNovelCard(placeholderImgCard).isEmpty())

        val grayLazyCard = Jsoup.parse(
            """
            <div class="card mb-30">
              <a class="card-img-tiles">
                <div class="inner"><div class="main-img">
                  <div class="lazyload" data-src="https://i.pinimg.com/564x/86/1e/51/861e5157abc25f92f6b49af0f1465927.jpg"></div>
                </div></div>
              </a>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst(".card")
        assertTrue(EsjzoneUrls.coverUrlFromNovelCard(grayLazyCard).isEmpty())
    }

    @Test
    fun novelDetailUrlFromForumBoard_usesTheBoardIdFromTheSecondPathSegment() {
        assertEquals(
            "/detail/1788015863.html",
            EsjzoneUrls.novelDetailUrlFromForumBoard(
                "/forum/1584622325/1788015863/"
            )
        )
    }

    @Test
    fun resolve_encodesSearchKeywordAsOnePathSegment() {
        assertEquals(
            "https://www.esjzone.cc/tags/A%23B%3F%2FC%20D",
            EsjzoneUrls.tagsUrl("A#B?/C D")
        )
    }

    @Test
    fun compoundSearchKeepsItsExpressionOnFirstAndLaterPages() {
        for (expression in listOf("!TS 后宫", "后宫+校园 !TS", "!TS")) {
            val first = EsjzoneUrls.tagsUrl(expression, category = 0, sort = 1).toHttpUrl()
            val later = EsjzoneUrls.tagsUrl(expression, category = 0, sort = 4, page = 2).toHttpUrl()
            assertEquals(listOf("tags", expression), first.pathSegments)
            assertEquals(listOf("tags-04", expression, "2.html"), later.pathSegments)
        }
    }

    @Test
    fun resolve_usesDocumentBaseForRelativeChapterAssets() {
        val base = "https://www.esjzone.one/forum/123/456.html"
        assertEquals("https://www.esjzone.one/forum/123/image.jpg", EsjzoneUrls.resolve("image.jpg", base))
        assertEquals("https://www.esjzone.one/forum/image.jpg", EsjzoneUrls.resolve("../image.jpg", base))
        assertEquals("https://cdn.example/image.jpg", EsjzoneUrls.resolve("https://cdn.example/image.jpg", base))
    }

    @Test
    fun isEsjHost_identifiesMirrorDomainsAndSubdomains() {
        assertTrue(EsjzoneUrls.isEsjHost("www.esjzone.cc"))
        assertTrue(EsjzoneUrls.isEsjHost("esjzone.cc"))
        assertTrue(EsjzoneUrls.isEsjHost("www.esjzone.one"))
        assertTrue(EsjzoneUrls.isEsjHost("esjzone.one"))
        assertTrue(EsjzoneUrls.isEsjHost("m.esjzone.cc"))
        assertTrue(EsjzoneUrls.isEsjHost("m.esjzone.one"))
        assertFalse(EsjzoneUrls.isEsjHost("esjzone.me"))
        assertFalse(EsjzoneUrls.isEsjHost("ncode.syosetu.com"))
        assertFalse(EsjzoneUrls.isEsjHost("fake-esjzone.com"))
    }

    @Test
    fun resolve_rewritesEsjMirrorDomainToTargetBase() {
        assertEquals(
            "https://www.esjzone.one/detail/123.html",
            EsjzoneUrls.resolve("https://www.esjzone.cc/detail/123.html", "https://www.esjzone.one")
        )
        assertEquals(
            "https://www.esjzone.cc/forum/123/456.html?q=1#bottom",
            EsjzoneUrls.resolve("https://www.esjzone.one/forum/123/456.html?q=1#bottom", "https://www.esjzone.cc")
        )
        assertEquals(
            "https://www.esjzone.one/detail/789.html",
            EsjzoneUrls.resolve("http://www.esjzone.cc/detail/789.html", "https://www.esjzone.one")
        )
        assertEquals(
            "https://www.esjzone.one/forum/1/2.html",
            EsjzoneUrls.resolve("//www.esjzone.cc/forum/1/2.html", "https://www.esjzone.one")
        )
    }

    @Test
    fun resolve_preservesExternalUrls() {
        assertEquals(
            "https://ncode.syosetu.com/n1234/",
            EsjzoneUrls.resolve("https://ncode.syosetu.com/n1234/", "https://www.esjzone.one")
        )
        assertEquals(
            "https://i.pinimg.com/image.jpg",
            EsjzoneUrls.resolve("https://i.pinimg.com/image.jpg", "https://www.esjzone.cc")
        )
    }
    @Test
    fun detailBoundaryRejectsAccountPathsAndUntrustedAuthorities() {
        val base = "https://www.esjzone.cc/"
        listOf("/my/logout", "/detail/12.html?next=/my/logout", "/detail/12.html#x",
            "https://www.esjzone.cc:444/detail/12.html", "http://www.esjzone.cc/detail/12.html",
            "https://user@www.esjzone.cc/detail/12.html", "https://evil.test/detail/12.html",
            "https://evil.esjzone.cc/detail/12.html", "/detail/12%2f34.html").forEach {
            assertNull(it, EsjzoneUrls.novelDetailUrl(it, base))
        }
        assertEquals("https://www.esjzone.cc/detail/12.html", EsjzoneUrls.novelDetailUrl("/detail/12.html", base))
        assertEquals("https://www.esjzone.cc/detail/12.html",
            EsjzoneUrls.novelDetailUrl("https://www.esjzone.one/detail/12.html", base))
    }

    @Test
    fun favoritePageUrl_entersSortModeBeforeFollowingNumericPager() {
        assertEquals(
            "https://www.esjzone.cc/my/favorite/new/",
            favoritePageUrl("new", 1)
        )
        assertEquals(
            "https://www.esjzone.cc/my/favorite/udate/",
            favoritePageUrl("udate", 1)
        )
        assertEquals(
            "https://www.esjzone.cc/my/favorite/2",
            favoritePageUrl("new", 2)
        )
        assertEquals(
            "https://www.esjzone.cc/my/favorite/udate/2",
            favoritePageUrl("udate", 2)
        )
    }

}
