package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.BookmarkCoverStore
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.readingStatisticsBookKey
import com.breakyuna.esjzone.network.wenku8.Wenku8Urls
import com.breakyuna.esjzone.network.wenku8.Wenku8PageKind
import com.breakyuna.esjzone.network.wenku8.wenku8PageAllowed
import com.breakyuna.esjzone.network.wenku8.novelDetailUrlForId
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Wenku8IdentityTest {
    private val bookUrl = "https://www.wenku8.net/book/2552.htm"
    private val chapterUrl = "https://www.wenku8.net/novel/2/2552/96772.htm"

    @Test fun urlAndSourceIdentityAreSeparate() {
        assertEquals("wenku8:2552", Wenku8Urls.detailIdentity(bookUrl))
        assertEquals(bookUrl, novelDetailUrlForId("wenku8:2552"))
        assertEquals("https://www.wenku8.net/novel/2/2552/index.htm", Wenku8Urls.catalog("2552"))
        assertEquals("https://www.wenku8.net/novel/0/1/index.htm", Wenku8Urls.catalog("1"))
        assertEquals("wenku8:2552", Wenku8Urls.catalogIdentity(Wenku8Urls.catalog("2552")))
        assertEquals("wenku8:2552", Chapter("", chapterUrl, false).novelId())
        assertEquals("", novelDetailUrlForId("wenku8:abc"))
        assertEquals("", novelDetailUrlForId("bad/id"))
        assertThrows(IllegalArgumentException::class.java) { Wenku8Urls.detail("wenku8:2552") }
    }

    @Test fun invalidOriginsAndNonBookPathsDoNotBecomeBooks() {
        listOf(
            "http://www.wenku8.net/book/2552.htm",
            "https://www.wenku8.net:444/book/2552.htm",
            "https://wenku8.net/book/2552.htm",
            "https://www.wenku8.net.example.org/book/2552.htm",
            "https://user@www.wenku8.net/book/2552.htm",
            "https://www.wenku8.net/book/2552.htm/extra",
            "/book/2552.htm", chapterUrl
        ).forEach { assertNull(it, Wenku8Urls.detailIdentity(it)) }
    }

    @Test fun loginRedirectsAreRecognizedWithoutAllowingThemAsBusinessPages() {
        listOf(
            "https://www.wenku8.net/login.php",
            "https://www.wenku8.net/login.php?jumpurl=%2Findex.php",
            "http://www.wenku8.net/login.php?jumpurl=%2Findex.php"
        ).forEach { login ->
            assertTrue(login, Wenku8Urls.isLogin(login))
            Wenku8PageKind.entries.forEach { kind ->
                assertFalse(wenku8PageAllowed(login, kind))
            }
        }
        listOf(
            "http://www.wenku8.net:81/login.php", "https://www.wenku8.net:444/login.php",
            "https://www.wenku8.net.example.org/login.php", "https://www.wenku8.cc/login.php",
            "https://user@www.wenku8.net/login.php", "https://www.wenku8.net/login.php/extra", bookUrl
        ).forEach { assertFalse(it, Wenku8Urls.isLogin(it)) }
    }

    @Test fun legacyLoginRedirectUpgradesOnlyTransportAndPreservesQueryBytes() {
        val path = "/login.php?jumpurl=http%3A%2F%2Fwww.wenku8.net%2Findex.php"
        assertEquals("https://www.wenku8.net$path", Wenku8Urls.loginRedirectUrl("http://www.wenku8.net$path"))
        listOf("http://www.wenku8.net:81$path", "http://www.wenku8.net.example.org$path",
            "http://fixture-user@www.wenku8.net$path", "http://www.wenku8.net/index.php",
            "http://www.wenku8.net/login.php/extra").forEach {
            assertNull(it, Wenku8Urls.loginRedirectUrl(it))
        }
    }

    @Test fun sameNumericIdCannotOverwriteOtherSourceIdentity() {
        assertEquals("wenku8:2552", BookshelfRepository.novelIdFor(bookUrl))
        assertEquals("2552", BookshelfRepository.novelIdFor("/detail/2552.html"))
        assertEquals("wenku8:2552", readingStatisticsBookKey("", bookUrl))
        assertNotEquals(
            readingStatisticsBookKey("", bookUrl),
            readingStatisticsBookKey("", "/detail/2552.html")
        )
        assertEquals("wenku8:2552", BookmarkCoverStore.cleanNovelId("", chapterUrl))
        assertEquals("wenku8:2552", BookmarkCoverStore.cleanNovelId("wenku8:2552", chapterUrl))
        assertEquals("wenku8:2552", BookmarkCoverStore.cleanNovelId(bookUrl))
    }

    @Test fun esjBookKeepsOwnershipOfExternalChapters() {
        val esj = detail("/detail/2552.html", "/forum/1/2552/")
        assertEquals("2552", esj.id())
        assertEquals("2552", BookmarkCoverStore.cleanNovelId(esj.id(), chapterUrl))
        assertEquals("2552", readingStatisticsBookKey(esj.id(), esj.url, chapterUrl))
        assertEquals("wenku8:2552", detail(bookUrl, "").id())
    }

    private fun detail(url: String, forumUrl: String) = DetailedNovel(
        name = "Synthetic book", url = url, coverUrl = "", views = 0, likes = 0,
        words = 0, type = "", author = "", forumUrl = forumUrl, tags = emptyList(),
        isAdult = false, isFavorite = false, description = NovelDescription(emptyList()),
        chapterList = NovelChapterList(emptyList())
    )
}
