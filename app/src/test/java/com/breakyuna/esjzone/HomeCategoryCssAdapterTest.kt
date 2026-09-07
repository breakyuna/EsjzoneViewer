package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.HomeSection
import com.breakyuna.esjzone.network.features.NovelCardLayout
import com.breakyuna.esjzone.network.features.parseNovelCard
import com.breakyuna.esjzone.network.features.selectForumCategories
import com.breakyuna.esjzone.network.features.selectHomeSectionCards
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract tests for the five independent home sections and top-level forum categories. */
class HomeCategoryCssAdapterTest {

    @Test
    fun homeSections_keepIndependentOrderingStatisticsAndAdultSemantics() {
        val document = fixture("home.html")
        val expected = listOf(
            HomeSection.TRANSLATED to Triple("Home Fixture Novel", 1_234, false),
            HomeSection.ORIGINAL to Triple("Original Fixture Novel", 321, false),
            HomeSection.TRANSLATED_R18 to Triple("Translated R18 Fixture", 654, true),
            HomeSection.ORIGINAL_R18 to Triple("Original R18 Fixture", 987, true),
            HomeSection.RECOMMENDATION to Triple("Recommended Fixture Novel", 78, true)
        )

        expected.forEach { (section, contract) ->
            val cards = selectHomeSectionCards(document, section)
            assertEquals("card count for ${section.key}", 1, cards.size)
            val novel = parseNovelCard(cards.single(), section.name.endsWith("R18"), NovelCardLayout.HOME)
            assertEquals(contract.first, novel.name)
            assertEquals("/detail/${when (section) {
                HomeSection.TRANSLATED -> "9001"
                HomeSection.ORIGINAL -> "9003"
                HomeSection.TRANSLATED_R18 -> "9004"
                HomeSection.ORIGINAL_R18 -> "9005"
                HomeSection.RECOMMENDATION -> "9002"
            }}.html", novel.url)
            assertEquals(contract.second, novel.views)
            assertEquals(contract.third, novel.isAdult)
        }
    }

    @Test
    fun forumCategories_selectOnlyTopLevelLinksAndPreserveR18Text() {
        val document = fixture("forum.html")
        val categories = selectForumCategories(document)
        assertEquals(listOf("一般討論", "R18 專區"), categories.map { it.text() })
        assertEquals(
            listOf("/forum/1584622325/", "/forum/1584622376/"),
            categories.map { it.attr("href") }
        )
        assertTrue(categories[1].text().contains("R18", ignoreCase = true))

        val board = document.selectFirst("table.forum-board-detail td a[href='/forum/9040/8040/']")!!
        assertEquals("Fixture Novel Board", board.text())
        assertTrue(board.parent()?.text()?.contains("主題：3") == true)
        assertTrue(board.parent()?.text()?.contains("回覆：4") == true)
    }

    private fun fixture(name: String): Document =
        Jsoup.parse(fixtureText(name), "https://example.test/")

    private fun fixtureText(name: String): String =
        requireNotNull(javaClass.getResource("/esj/$name")) {
            "missing checked-in parser fixture: $name"
        }.openStream().bufferedReader().use { it.readText() }
}
