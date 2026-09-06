package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.ReaderRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
