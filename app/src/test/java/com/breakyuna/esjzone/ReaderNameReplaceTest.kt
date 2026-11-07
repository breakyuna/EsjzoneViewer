package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.ReaderNameReplace
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceRule
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceSettings
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
}
