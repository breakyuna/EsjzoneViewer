package com.breakyuna.esjzone

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.ReaderRoute
import com.breakyuna.esjzone.ui.navigation.containerTransformMetadata
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM coverage for the value objects stored by Navigation 3. */
class NavigationRouteContractTest {
    @Test
    fun loginDestinationsClearTheRootAuthorizationGate() {
        for (replaceAll in listOf(false, true)) {
            var authorization: Authorization? = Authorization("fixture-key", "fixture-token", "www.esjzone.cc")
            val stack = mutableListOf<NavKey>(AppNavKey.Main)
            val navigator = com.breakyuna.esjzone.ui.navigation.AppNavigator(stack, mutableMapOf(),
                setAuthorization = { authorization = it })
            if (replaceAll) navigator.replaceAll(com.breakyuna.esjzone.ui.screen.LoginScreen)
            else navigator.replace(com.breakyuna.esjzone.ui.screen.LoginScreen)
            org.junit.Assert.assertNull(authorization)
            assertEquals(listOf(AppNavKey.Login), stack)
        }
    }

    @Test
    fun downloadedCatalogRestoresWithoutAnInMemoryRegistry() {
        val route = LegacyRoute.DownloadedNovel("https://www.esjzone.cc/detail/123.html")
        val restored = Json.decodeFromString(LegacyRoute.serializer(), Json.encodeToString(LegacyRoute.serializer(), route))
        val navigator = com.breakyuna.esjzone.ui.navigation.AppNavigator(mutableListOf(), mutableMapOf())
        assertEquals("DownloadedNovelPage:https://www.esjzone.cc/detail/123.html", navigator.destination(restored)?.key)
    }

    @Test
    fun navigationUsesTypedSearchArgumentsAndPreservesIntermediateEntries() {
        val stack = mutableListOf<NavKey>(AppNavKey.HomeTab)
        val navigator = com.breakyuna.esjzone.ui.navigation.AppNavigator(stack, mutableMapOf())
        val search = com.breakyuna.esjzone.ui.page.SearchPage("title:subtitle")
        assertTrue(navigator.pushIfNotCurrent(search))
        val firstSearch = stack.last() as AppNavKey.Legacy
        assertEquals(LegacyRoute.Search("title:subtitle"), firstSearch.route)
        assertFalse(navigator.pushIfNotCurrent(search))
        navigator.push(com.breakyuna.esjzone.ui.page.ReaderBackgroundPage)
        val background = stack.last() as AppNavKey.Legacy
        assertTrue(navigator.pushIfNotCurrent(search))
        val secondSearch = stack.last() as AppNavKey.Legacy
        assertEquals(LegacyRoute.Search("title:subtitle"), secondSearch.route)
        assertNotEquals(firstSearch.instanceId, secondSearch.instanceId)
        assertEquals(listOf(AppNavKey.HomeTab, firstSearch, background, secondSearch), stack)
    }

    @Test
    fun authorSearchesKeepTheirOriginDetailWhenTheSameAuthorIsSearchedAgain() {
        val detailA = AppNavKey.Legacy(LegacyRoute.Novel("/detail/1.html"))
        val detailB = AppNavKey.Legacy(LegacyRoute.Novel("/detail/2.html"))
        val stack = mutableListOf<NavKey>(AppNavKey.HomeTab, detailA)
        val navigator = com.breakyuna.esjzone.ui.navigation.AppNavigator(stack, mutableMapOf())
        val searchA = com.breakyuna.esjzone.ui.page.SearchPage(" 作者 ", originNovelKey = "NovelPage:/detail/1.html")
        val searchB = com.breakyuna.esjzone.ui.page.SearchPage(" 作者 ", originNovelKey = "NovelPage:/detail/2.html")

        assertTrue(navigator.pushIfNotCurrent(searchA))
        stack.add(detailB)
        assertNotEquals(searchA.key, searchB.key)
        assertTrue(navigator.pushIfNotCurrent(searchB))
        assertTrue(navigator.pop())
        assertEquals(detailB, stack.last())

        val serializer = AppNavKey.Legacy.serializer()
        val restored = Json.decodeFromString(serializer, Json.encodeToString(serializer, searchB.navKey))
        val freshNavigator = com.breakyuna.esjzone.ui.navigation.AppNavigator(mutableListOf(), mutableMapOf())
        assertEquals(searchB.navKey, freshNavigator.destination(restored.route)?.navKey)
        assertEquals(searchB.key, freshNavigator.destination(restored.route)?.key)
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

    private fun scene(key: AppNavKey): Scene<NavKey> {
        val entry = NavEntry<NavKey>(key, metadata = containerTransformMetadata(key)) {}
        return object : Scene<NavKey> {
            override val key: Any = entry.contentKey
            override val entries: List<NavEntry<NavKey>> = listOf(entry)
            override val previousEntries: List<NavEntry<NavKey>> = emptyList()
            override val content: @Composable () -> Unit = {}
        }
    }
}
