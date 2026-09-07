package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.pageCount
import com.breakyuna.esjzone.network.features.selectNovelCards
import com.breakyuna.esjzone.network.features.selectNovelForumLinks
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure CSS-boundary checks for list and search page migration. */
class ListSearchSelectorTest {

    @Test
    fun listFixture_selectsOnlyNovelForumBoards() {
        val links = selectNovelForumLinks(fixture("list.html"))
        assertEquals(1, links.size)
        assertEquals("Category Fixture Novel", links.single().text())
        assertEquals("/forum/9100/9110/", links.single().attr("href"))
    }

    @Test
    fun listAndSearchFixtures_selectCardsAndPagerWithoutPositionalXpath() {
        assertEquals(1, selectNovelCards(fixture("list.html")).size)
        assertEquals(2, selectNovelCards(fixture("search.html")).size)
        assertEquals(2, pageCount(fixture("search.html")))
        assertEquals(1, pageCount(fixture("list.html")))
    }

    private fun fixture(name: String) =
        Jsoup.parse(
            requireNotNull(javaClass.getResource("/esj/$name"))
                .openStream().bufferedReader().use { it.readText() },
            "https://example.test/"
        )
}
