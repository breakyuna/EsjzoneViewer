package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.ReaderRoute
import com.breakyuna.esjzone.ui.navigation.ContainerTransformTarget
import com.breakyuna.esjzone.ui.navigation.usesContainerTransform
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
    fun onlyCarouselNovelEntriesUseTheHomeContainerTransform() {
        val route = LegacyRoute.Novel("/detail/123.html")
        val ordinaryEntry = AppNavKey.Legacy(route)
        val carouselEntry = AppNavKey.Legacy(route, homeCarouselPage = 100)
        assertFalse(usesContainerTransform(AppNavKey.HomeTab, ordinaryEntry))
        assertTrue(usesContainerTransform(AppNavKey.HomeTab, carouselEntry))
        assertTrue(usesContainerTransform(carouselEntry, AppNavKey.HomeTab))
        assertFalse(usesContainerTransform(AppNavKey.BookshelfTab, carouselEntry))
        assertEquals(
            ContainerTransformTarget.homeCarousel(route.identity, 100),
            ContainerTransformTarget.forKey(carouselEntry)
        )
        assertNotEquals(
            ContainerTransformTarget.forKey(carouselEntry),
            ContainerTransformTarget.forKey(AppNavKey.Legacy(route, homeCarouselPage = 101))
        )
    }

    @Test
    fun savedNovelRoutesRetainCarouselInstanceAndAcceptOlderEntries() {
        val route = LegacyRoute.Novel("/detail/123.html")
        val serializer = AppNavKey.Legacy.serializer()
        val carouselEntry = AppNavKey.Legacy(route, homeCarouselPage = 100)
        assertEquals(carouselEntry, Json.decodeFromString(serializer, Json.encodeToString(serializer, carouselEntry)))
        val olderEntry = AppNavKey.Legacy(route)
        val savedWithoutCarousel = Json.encodeToString(serializer, olderEntry)
        assertFalse(savedWithoutCarousel.contains("homeCarouselPage"))
        assertEquals(olderEntry, Json.decodeFromString(serializer, savedWithoutCarousel))
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
