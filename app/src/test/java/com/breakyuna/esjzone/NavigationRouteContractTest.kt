package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.ReaderRoute
import com.breakyuna.esjzone.ui.navigation.ContainerTransformTarget
import com.breakyuna.esjzone.ui.navigation.usesContainerTransform
import com.breakyuna.esjzone.ui.navigation.containerTransformMetadata
import com.breakyuna.esjzone.ui.navigation.canShowAppUpdates
import com.breakyuna.esjzone.ui.navigation.needsStorageSetup
import com.breakyuna.esjzone.network.Authorization
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.compose.runtime.Composable
import androidx.navigation3.scene.Scene
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Pure JVM coverage for the value objects stored by Navigation 3. */
class NavigationRouteContractTest {
    @Test
    fun downloadedCatalogRestoresWithoutAnInMemoryRegistry() {
        val route = LegacyRoute.DownloadedNovel("https://www.esjzone.cc/detail/123.html")
        val restored = Json.decodeFromString(LegacyRoute.serializer(), Json.encodeToString(LegacyRoute.serializer(), route))
        val navigator = com.breakyuna.esjzone.ui.navigation.AppNavigator(mutableListOf(), mutableMapOf())
        assertEquals("DownloadedNovelPage:https://www.esjzone.cc/detail/123.html", navigator.destination(restored)?.key)
    }

    @Test
    fun storageSetupWaitsForLoginAndAlsoGuardsRestoredReadersAndDetails() {
        val session = Authorization("fixture-key", "fixture-token", "www.esjzone.cc")
        for (destination in listOf(null, AppNavKey.Loading, AppNavKey.Login)) {
            assertFalse(needsStorageSetup(destination, session, ""))
        }
        val destinations = listOf(
            AppNavKey.Main,
            AppNavKey.Reader(ReaderRoute("123", "/forum/123/456.html")),
            AppNavKey.Legacy(LegacyRoute.Novel("/detail/123.html"))
        )
        for (destination in destinations) {
            assertTrue(needsStorageSetup(destination, session, ""))
            assertFalse(needsStorageSetup(destination, session, "content://fixture/tree/books"))
            assertFalse(needsStorageSetup(destination, null, ""))
            assertFalse(needsStorageSetup(destination, Authorization(), ""))
        }
    }

    @Test
    fun updatesWaitUntilTheLoginOrLoadingPageHasClosed() {
        val session = Authorization("fixture-key", "fixture-token", "www.esjzone.cc")
        // Relogin must also suppress a pending prompt while the old session remains.
        for (destination in listOf(AppNavKey.Loading, AppNavKey.Login)) {
            assertFalse(canShowAppUpdates(destination, session))
        }
        assertTrue(canShowAppUpdates(AppNavKey.Main, session))
    }

    @Test
    fun updatesRemainAvailableInSettingsAndRestoredReadersOnlyWithASession() {
        val session = Authorization("fixture-key", "fixture-token", "www.esjzone.cc")
        val destinations = listOf(
            AppNavKey.Main,
            AppNavKey.Legacy(LegacyRoute.Static("SettingsPage")),
            AppNavKey.Reader(ReaderRoute(novelId = "123", chapterIdentity = "/forum/123/456.html"))
        )
        for (destination in destinations) {
            assertTrue(canShowAppUpdates(destination, session))
            assertFalse(canShowAppUpdates(destination, null))
            assertFalse(canShowAppUpdates(destination, Authorization()))
        }
    }

    @Test
    fun cacheRecordsRouteRestoresWithoutAnInMemoryRegistry() {
        val navigator = com.breakyuna.esjzone.ui.navigation.AppNavigator(
            mutableListOf(), mutableMapOf())
        assertEquals(com.breakyuna.esjzone.ui.page.CacheRecordsPage,
            navigator.destination(LegacyRoute.Static("CacheRecordsPage")))
    }

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
    fun readerRestoresLatestChapterWithoutReplacingTheLiveEntry() {
        val original = AppNavKey.Reader(ReaderRoute("123", "/forum/123/1.html"))
        val stack = mutableListOf<NavKey>(AppNavKey.Main, original)
        val savedRoutes = mutableMapOf<String, String>()
        val navigator = com.breakyuna.esjzone.ui.navigation.AppNavigator(stack, mutableMapOf(),
            readerRoutes = savedRoutes)

        navigator.updateReaderRoute("123", "/forum/123/2.html")
        navigator.updateReaderRoute("123", "/forum/123/3.html")
        assertEquals(listOf(AppNavKey.Main, original), stack)
        // Simulate recreation without the live destination registry.
        val restored = com.breakyuna.esjzone.ui.navigation.AppNavigator(stack.toMutableList(), mutableMapOf(),
            readerRoutes = savedRoutes.toMutableMap())
        val restoredPage = restored.readerDestination(original.route)
        assertEquals("ChapterPage:123:/forum/123/3.html", restoredPage.key)
        restored.updateReaderRoute("123", "/forum/123/4.html")
        // Recomposition must retain the live destination, even as its resume snapshot advances.
        org.junit.Assert.assertSame(restoredPage, restored.readerDestination(original.route))
        assertTrue(navigator.pop())
        assertTrue(savedRoutes.isEmpty())
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
    fun sceneContentKeysAreSaveableAndKeepCarouselInstancesDistinct() {
        val route = LegacyRoute.Novel("/detail/123.html")
        val keys = listOf(
            AppNavKey.HomeTab,
            AppNavKey.HistoryTab,
            AppNavKey.BookshelfTab,
            AppNavKey.ProfileTab,
            AppNavKey.Legacy(route),
            AppNavKey.Legacy(route, homeCarouselPage = 100),
            AppNavKey.Legacy(route, homeCarouselPage = 101)
        )
        val contentKeys = keys.map { scene(it).key }
        assertTrue(contentKeys.all { it is java.io.Serializable })
        java.io.ObjectOutputStream(java.io.ByteArrayOutputStream()).use {
            it.writeObject(contentKeys)
        }
        assertEquals(keys.size, contentKeys.toSet().size)
    }

    @Test
    fun sceneMetadataKeepsLinkedTransitionsInBothDirections() {
        val targets = listOf(
            ContainerTransformTarget.FORUM,
            ContainerTransformTarget.GUESTBOOK,
            ContainerTransformTarget.WATER_COOLER,
            ContainerTransformTarget.READING_STATISTICS,
            ContainerTransformTarget.BOOKSHELF_READING_STATISTICS,
            ContainerTransformTarget.BOOKMARKS,
            ContainerTransformTarget.DOWNLOADS,
            ContainerTransformTarget.SETTINGS,
            ContainerTransformTarget.homeCarousel("/detail/123.html", 100)
        )
        for (target in targets) {
            val origin = scene(target.origin)
            val destination = scene(AppNavKey.Legacy(target.route, target.homeCarouselPage))
            assertTrue(usesContainerTransform(origin, destination))
            // The outgoing entry's metadata remains available after it leaves the back stack.
            assertTrue(usesContainerTransform(destination, origin))
            assertFalse(usesContainerTransform(scene(AppNavKey.HistoryTab), destination))
        }
        assertFalse(usesContainerTransform(
            scene(AppNavKey.HomeTab),
            scene(AppNavKey.Legacy(LegacyRoute.Novel("/detail/123.html")))
        ))
    }

    @Test
    fun readingStatisticsMatchesTheEntryPointInEachTab() {
        val destination = AppNavKey.Legacy(LegacyRoute.Static("ReadingStatisticsPage"))
        assertEquals(
            ContainerTransformTarget.READING_STATISTICS,
            ContainerTransformTarget.forKey(destination, origin = AppNavKey.ProfileTab)
        )
        assertEquals(
            ContainerTransformTarget.BOOKSHELF_READING_STATISTICS,
            ContainerTransformTarget.forKey(destination, origin = AppNavKey.BookshelfTab)
        )
        assertFalse(usesContainerTransform(AppNavKey.HomeTab, destination))
        for (target in listOf(ContainerTransformTarget.BOOKMARKS, ContainerTransformTarget.DOWNLOADS, ContainerTransformTarget.SETTINGS)) {
            assertFalse(usesContainerTransform(AppNavKey.BookshelfTab, AppNavKey.Legacy(target.route)))
        }
    }

    private fun scene(key: AppNavKey): Scene<NavKey> {
        val entry = NavEntry<NavKey>(key, metadata = containerTransformMetadata(key)) {}
        return object : Scene<NavKey> {
            override val key: Any = entry.contentKey
            override val entries: List<NavEntry<NavKey>> = listOf(entry)
            override val previousEntries: List<NavEntry<NavKey>> = emptyList()
            override val content: @Composable () -> Unit = {}
        }
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
