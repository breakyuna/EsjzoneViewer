package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.ReaderNameReplace
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceRule
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceSettings
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderNameReplaceTest {

    private fun settings(
        vararg rules: ReaderNameReplaceRule,
        enabled: Boolean = true
    ) = ReaderNameReplaceSettings(enabled = enabled, rules = rules.toList())

    @Test
    fun disabledMasterSwitchReturnsTextUnchanged() {
        val value = settings(ReaderNameReplaceRule(source = "Alice", target = "Bob"), enabled = false)
        assertEquals("Alice met Alice", ReaderNameReplace.apply("Alice met Alice", value))
    }

    @Test
    fun emptyRulesReturnTextUnchanged() {
        assertEquals("Alice met Alice", ReaderNameReplace.apply("Alice met Alice", settings()))
    }

    @Test
    fun simpleReplacementAppliesEveryOccurrence() {
        val value = settings(ReaderNameReplaceRule(source = "Alice", target = "Bob"))
        assertEquals("Bob met Bob", ReaderNameReplace.apply("Alice met Alice", value))
    }

    @Test
    fun multipleRulesApplyInOnePass() {
        val value = settings(
            ReaderNameReplaceRule(source = "Alice", target = "Bob"),
            ReaderNameReplaceRule(source = "Carol", target = "Dave")
        )
        assertEquals("Bob met Dave", ReaderNameReplace.apply("Alice met Carol", value))
    }

    @Test
    fun longestSourceWinsForOverlappingPrefixes() {
        val value = settings(
            ReaderNameReplaceRule(source = "阿", target = "A"),
            ReaderNameReplaceRule(source = "阿贝", target = "B"),
            ReaderNameReplaceRule(source = "阿贝多", target = "C")
        )
        assertEquals("C和A尔", ReaderNameReplace.apply("阿贝多和阿尔", value))
    }

    @Test
    fun longerSourceWinsRegardlessOfRuleOrder() {
        val value = settings(
            ReaderNameReplaceRule(source = "阿贝多", target = "C"),
            ReaderNameReplaceRule(source = "阿", target = "A")
        )
        assertEquals("C", ReaderNameReplace.apply("阿贝多", value))
    }

    @Test
    fun regexMetacharactersMatchLiterally() {
        val value = settings(ReaderNameReplaceRule(source = "A+B.*", target = "X"))
        assertEquals("X and A+B", ReaderNameReplace.apply("A+B.* and A+B", value))
    }

    @Test
    fun blankSourceRulesAreIgnored() {
        val value = settings(
            ReaderNameReplaceRule(source = " ", target = "X"),
            ReaderNameReplaceRule(source = "", target = "Y")
        )
        assertEquals("Alice", ReaderNameReplace.apply("Alice", value))
    }

    @Test
    fun disabledRuleIsSkipped() {
        val value = settings(
            ReaderNameReplaceRule(source = "Alice", target = "Bob", enabled = false),
            ReaderNameReplaceRule(source = "Carol", target = "Dave")
        )
        assertEquals("Alice met Dave", ReaderNameReplace.apply("Alice met Carol", value))
    }

    @Test
    fun blankTargetRemovesTheMatchedSource() {
        val value = settings(ReaderNameReplaceRule(source = "Alice ", target = ""))
        assertEquals("met Bob", ReaderNameReplace.apply("Alice met Bob", value))
    }

    @Test
    fun matchingIsCaseSensitive() {
        val value = settings(ReaderNameReplaceRule(source = "Alice", target = "Bob"))
        assertEquals("Bob alice ALICE", ReaderNameReplace.apply("Alice alice ALICE", value))
    }

    @Test
    fun identicalSourceAndTargetIsHarmless() {
        val value = settings(ReaderNameReplaceRule(source = "Alice", target = "Alice"))
        assertEquals("Alice met Alice", ReaderNameReplace.apply("Alice met Alice", value))
    }

    @Test
    fun emptyTextStaysEmpty() {
        val value = settings(ReaderNameReplaceRule(source = "Alice", target = "Bob"))
        assertEquals("", ReaderNameReplace.apply("", value))
    }
    @Test
    fun inactiveMappingReturnsInput() {
        val base = ReaderTextOffsets.identity("A")
        listOf(
            settings(),
            settings(ReaderNameReplaceRule(source = "A", target = "Alice"), enabled = false),
            settings(ReaderNameReplaceRule(source = "A", target = "Alice", enabled = false)),
            settings(ReaderNameReplaceRule(source = " ", target = "Alice"))
        ).forEach { assertSame(base, ReaderNameReplace.mapping(base, it)) }
    }

    @Test
    fun longerTargetPreservesSourceBoundaries() {
        val mapped = ReaderNameReplace.mapping(ReaderTextOffsets.identity("xA!"),
            settings(ReaderNameReplaceRule(source = "A", target = "Alice")))
        assertEquals("xAlice!", mapped.text)
        assertArrayEquals(intArrayOf(0, 1, 1, 1, 1, 1, 2), mapped.sourceStarts)
        assertArrayEquals(intArrayOf(1, 2, 2, 2, 2, 2, 3), mapped.sourceEnds)
        for (source in 0..3) assertEquals(source, mapped.toSource(mapped.toDisplay(source)))
        assertEquals(1, mapped.toSource(4))
        assertEquals(6, mapped.toDisplay(2))
    }

    @Test
    fun shorterAndEmptyTargetsPreserveFollowingOffsets() {
        val base = ReaderTextOffsets.identity("xAlice!")
        val shorter = ReaderNameReplace.mapping(base,
            settings(ReaderNameReplaceRule(source = "Alice", target = "A")))
        assertEquals("xA!", shorter.text)
        assertArrayEquals(intArrayOf(0, 1, 6), shorter.sourceStarts)
        assertArrayEquals(intArrayOf(1, 6, 7), shorter.sourceEnds)
        assertEquals(6, shorter.toSource(2))
        assertEquals(2, shorter.toDisplay(6))
        val removed = ReaderNameReplace.mapping(base,
            settings(ReaderNameReplaceRule(source = "Alice", target = "")))
        assertEquals("x!", removed.text)
        assertArrayEquals(intArrayOf(0, 6), removed.sourceStarts)
        assertArrayEquals(intArrayOf(1, 7), removed.sourceEnds)
        assertEquals(6, removed.toSource(1))
        assertEquals(1, removed.toDisplay(6))
        assertEquals(7, removed.sourceLength)
    }

    @Test
    fun mappingComposesWithNonIdentityBase() {
        val base = ReaderTextOffsets("xAB!", intArrayOf(0, 1, 3, 5), intArrayOf(1, 3, 5, 6), 6)
        val mapped = ReaderNameReplace.mapping(base,
            settings(ReaderNameReplaceRule(source = "AB", target = "Alice")))
        assertEquals("xAlice!", mapped.text)
        assertArrayEquals(intArrayOf(0, 1, 1, 1, 1, 1, 5), mapped.sourceStarts)
        assertArrayEquals(intArrayOf(1, 5, 5, 5, 5, 5, 6), mapped.sourceEnds)
        assertEquals(6, mapped.sourceLength)
        assertEquals(5, mapped.toSource(6))
        assertEquals(6, mapped.toDisplay(5))
    }
    @Test
    fun paragraphMappingReplacesEachPartSeparately() {
        val block = ReaderBlock.Paragraph(listOf(ReaderBlock.Text("A"), ReaderBlock.Text("B!")))
        val mapped = ReaderNameReplace.blockMapping(block, settings(
            ReaderNameReplaceRule(source = "AB", target = "wrong"),
            ReaderNameReplaceRule(source = "A", target = "Alice")
        )) { part -> ReaderTextOffsets.identity((part as ReaderBlock.Text).value) }
        assertEquals("AliceB!", mapped.text)
        assertArrayEquals(intArrayOf(0, 0, 0, 0, 0, 1, 2), mapped.sourceStarts)
        assertArrayEquals(intArrayOf(1, 1, 1, 1, 1, 2, 3), mapped.sourceEnds)
        assertEquals(3, mapped.sourceLength)
    }
}
