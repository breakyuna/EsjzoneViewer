package com.breakyuna.esjzone.ui.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTagExpressionTest {
    @Test
    fun exclusionsAreAppendedWithPlusAndBangWithoutChangingTheExistingQuery() {
        val result = buildSearchTagExpression(
            currentQuery = "  作品 名 !旧标签+作者  ",
            excluded = "TS !虐主 ！刀"
        )

        assertEquals("作品 名 !旧标签+作者+!TS+!虐主+!刀", result.query)
        assertTrue(result.canSearch)
    }

    @Test
    fun exclusionOnlyAndDirectKeywordRemainValid() {
        assertEquals("!TS+!校园", buildSearchTagExpression("", "TS 校园").query)
        assertEquals("!TS+!校园", buildSearchTagExpression("", "!TS+校园").query)
        assertEquals("!TS", buildSearchTagExpression("", "TS").query)
        assertTrue(buildSearchTagExpression("", "!TS").canSearch)
        assertEquals("书 名+作者", buildSearchTagExpression("书 名+作者", "").query)
        assertEquals("后宫+!TS", buildSearchTagExpression("后宫", "TS").query)
        assertFalse(buildSearchTagExpression(" ", "").canSearch)
    }

    @Test
    fun invalidPrefixCannotAccidentallyInvertTheSelectedMeaning() {
        for (invalid in listOf("!", "！！TS", "!!TS")) {
            val excluded = buildSearchTagExpression("后宫", invalid)
            assertTrue(excluded.invalidExcluded)
            assertFalse(excluded.canSearch)
        }
    }

    @Test
    fun exclusionFieldInsertsBangAndPlusBetweenKeywords() {
        assertEquals("!TS", formatExclusionInput("TS"))
        assertEquals("!TS+", formatExclusionInput("TS "))
        assertEquals("!TS+!校园", formatExclusionInput("TS 校园"))
        assertEquals("!TS+!校园", formatExclusionInput("TS+校园"))
        assertEquals("!TS+!校园+", formatExclusionInput("!TS+!校园 "))
        assertEquals("!!TS", formatExclusionInput("!!TS"))
    }

    @Test
    fun cursorFollowsPrefixesAndSeparatorsInsertedBeforeIt() {
        assertEquals("!T+!S", formatExclusionInput("!T S"))
        assertEquals(4, mapExclusionCursor("!T S", 3))
        assertEquals(2, mapExclusionCursor("TS", 1))
        assertEquals(1, mapExclusionCursor("TS", 0))
        assertEquals(4, mapExclusionCursor("TS ", 3))
        assertEquals(3, mapExclusionCursor("TS ", 2))
        assertEquals(5, mapExclusionCursor("TS+校园", 3))
        assertEquals("!TS+!校园".length, mapExclusionCursor("!TS+!校园", "!TS+!校园".length))
        assertEquals(2, mapExclusionCursor("!TS", 2))
    }
}
