package com.breakyuna.esjzone.ui.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTagExpressionTest {
    @Test
    fun includesAndExcludesAreCombinedWithoutChangingTheExistingQuery() {
        val result = buildSearchTagExpression(
            currentQuery = "  作品 名 !旧标签+作者  ",
            included = "后宫+校园　恋爱",
            excluded = "TS !虐主 ！刀"
        )

        assertEquals("作品 名 !旧标签+作者 后宫 校园 恋爱 !TS !虐主 !刀", result.query)
        assertTrue(result.canSearch)
    }

    @Test
    fun exclusionOnlyAndDirectKeywordRemainValid() {
        assertEquals("后宫 校园 !TS", buildSearchTagExpression("", "后宫 校园", "TS").query)
        assertEquals("!TS", buildSearchTagExpression("", "", "TS").query)
        assertTrue(buildSearchTagExpression("", "", "!TS").canSearch)
        assertEquals("书 名+作者", buildSearchTagExpression("书 名+作者", "", "").query)
        assertFalse(buildSearchTagExpression(" ", "", "").canSearch)
    }

    @Test
    fun invalidPrefixCannotAccidentallyInvertTheSelectedMeaning() {
        val included = buildSearchTagExpression("", "!TS", "")
        assertTrue(included.invalidIncluded)
        assertFalse(included.canSearch)
        for (invalid in listOf("!", "！！TS", "!!TS")) {
            val excluded = buildSearchTagExpression("", "后宫", invalid)
            assertTrue(excluded.invalidExcluded)
            assertFalse(excluded.canSearch)
        }
    }
}
