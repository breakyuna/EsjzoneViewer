package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.ReaderRoute
import com.breakyuna.esjzone.ui.navigation.readerToken
import com.breakyuna.esjzone.ui.navigation.readerRouteFromToken
import com.breakyuna.esjzone.ui.navigation.encodeRouteTokenPart
import com.breakyuna.esjzone.ui.navigation.decodeRouteTokenPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM coverage for the value objects stored by Navigation 3. */
class NavigationRouteContractTest {
    @Test
    fun featureRoutesAreStableValueObjects() {
        assertEquals(LegacyRoute.Search("keyword"), LegacyRoute.Search("keyword"))
        assertNotEquals(LegacyRoute.Search("keyword"), LegacyRoute.Search("other"))
        assertEquals(
            LegacyRoute.NovelList(novelType = 1, sortType = 2, adultOnly = true),
            LegacyRoute.NovelList(novelType = 1, sortType = 2, adultOnly = true)
        )
    }

    @Test
    fun readerRouteRemainsSeparateFromLegacyRoutes() {
        val route = ReaderRoute(novelId = "novel", chapterIdentity = "chapter")
        assertEquals(AppNavKey.Reader(route), AppNavKey.Reader(route))
        assertNotEquals(AppNavKey.Reader(route), AppNavKey.Legacy(LegacyRoute.Novel("novel")))
    }

    @Test
    fun readerTokensRoundTripSourceIdAndCompleteUrl() {
        val chapter = "https://www.wenku8.net/novel/2/2552/96772.htm?key=a:b#anchor"
        val wenku = ReaderRoute("wenku8:2552", chapter)
        assertEquals("ChapterPage:wenku8%3A2552:$chapter", readerToken(wenku))
        assertEquals(wenku, readerRouteFromToken(readerToken(wenku)))
        val esj = ReaderRoute("2552", "https://www.esjzone.cc/forum/2552/99.html")
        assertEquals("ChapterPage:2552:${esj.chapterIdentity}", readerToken(esj))
        assertEquals(esj, readerRouteFromToken(readerToken(esj)))
        assertEquals(ReaderRoute("", "chapter"), readerRouteFromToken("ChapterPage:chapter"))
    }

    @Test
    fun routePartsKeepLiteralPlusAndUnicode() {
        val part = "书名 + 作者:%"
        assertEquals(part, decodeRouteTokenPart(encodeRouteTokenPart(part)))
        assertEquals("a+b", decodeRouteTokenPart("a+b"))
    }

    @Test
    fun startTabDefaultsAndValidValuesAreConsistent() {
        val valid = com.breakyuna.esjzone.data.settings.SettingsDefaults.VALID_START_TABS
        val defaultTab = com.breakyuna.esjzone.data.settings.SettingsDefaults.DEFAULT_START_TAB
        assertTrue(valid.contains(defaultTab))
        assertTrue(valid.contains("HOME"))
        assertTrue(valid.contains("BOOKSHELF"))
        assertTrue(valid.contains("HISTORY"))
        assertTrue(valid.contains("PROFILE"))
    }
}
