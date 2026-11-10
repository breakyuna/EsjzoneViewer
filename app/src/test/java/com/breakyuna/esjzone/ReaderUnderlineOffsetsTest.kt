package com.breakyuna.esjzone

import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineRange
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.domain.reader.searchReaderDocument
import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.ui.reader.ReaderNameReplace
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceRule
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceSettings
import com.breakyuna.esjzone.ui.reader.readerUnderlineQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Underlines are stored in original-text offsets and drawn through the current replacement map. */
class ReaderUnderlineOffsetsTest {
    private val block = ReaderBlock.Text("阿贝多和可莉去找琴团长，阿贝多笑了。")
    private val source = block.sourceText()
    private val sourceSignature = ReaderUnderlines.sourceSignature(block)
    private val longer = ReaderNameReplaceRule(id = "a", source = "阿贝多", target = "Albedo")
    private val shorter = ReaderNameReplaceRule(id = "b", source = "琴团长", target = "琴")
    private val removed = ReaderNameReplaceRule(id = "c", source = "可莉", target = "")

    private fun settings(vararg rules: ReaderNameReplaceRule, enabled: Boolean = true) =
        ReaderNameReplaceSettings(enabled, rules.toList())

    private fun display(settings: ReaderNameReplaceSettings, target: ReaderBlock = block) =
        ReaderNameReplace.blockMapping(target, settings) { ReaderTextOffsets.identity(it.sourceText()) }

    /** Simulates selecting [start, end) on the displayed text and saving it. */
    private fun select(offsets: ReaderTextOffsets, start: Int, end: Int): ReaderUnderline {
        val rendered = ReaderUnderlines.signature(block, offsets.text)
        return ReaderUnderlines.toSource(ReaderUnderline(0, rendered, start, end)) { sourceSignature to offsets }
    }

    /** The displayed text under [offsets] that a saved mark would be drawn on. */
    private fun drawn(mark: ReaderUnderline, offsets: ReaderTextOffsets): String? =
        ReaderUnderlines.displayRange(mark.ranges().single(), sourceSignature,
            ReaderUnderlines.signature(block, offsets.text), offsets)?.let { offsets.text.substring(it.first, it.last + 1) }

    @Test fun displayEndRoundsOutwardAndSourceEndCoversWholeReplacement() {
        val mapped = display(settings(longer))
        assertEquals("Albedo和可莉去找琴团长，Albedo笑了。", mapped.text)
        // Source end inside "阿贝多" still covers the whole displayed "Albedo".
        assertEquals(6, mapped.toDisplayEnd(1))
        assertEquals(6, mapped.toDisplayEnd(3))
        assertEquals(0, mapped.toDisplayEnd(0))
        // Display end inside "Albedo" extends to the end of "阿贝多".
        assertEquals(3, mapped.toSourceEnd(2))
        assertEquals(source.length, mapped.toSourceEnd(mapped.text.length))
        assertEquals(0, ReaderTextOffsets.identity("").toSourceEnd(3))
    }

    @Test fun everyDisplayedSelectionRoundTripsThroughOriginalOffsets() {
        val identity = ReaderTextOffsets.identity(source)
        for (options in listOf(settings(longer), settings(shorter), settings(removed), settings(longer, shorter, removed))) {
            val mapped = display(options)
            for (start in mapped.text.indices) for (end in start + 1..mapped.text.length) {
                val mark = select(mapped, start, end)
                val saved = mark.ranges().single()
                assertEquals(sourceSignature, saved.signature)
                // Drawn text covers the selection, widened only to whole replaced names.
                val visible = drawn(mark, mapped)!!
                assertTrue(visible.contains(mapped.text.substring(start, end)))
                assertEquals(visible, drawn(ReaderUnderlines.toSource(ReaderUnderline(0,
                    ReaderUnderlines.signature(block, mapped.text), mapped.toDisplay(saved.start),
                    mapped.toDisplayEnd(saved.end))) { sourceSignature to mapped }, mapped))
                // With replacement off, the same original characters are drawn.
                assertEquals(source.substring(saved.start, saved.end), drawn(mark, identity))
            }
        }
    }

    @Test fun selectionInsideReplacedNameExpandsToWholeOriginalName() {
        val mapped = display(settings(longer))
        // "bed" inside "Albedo" saves the entire original "阿贝多".
        val mark = select(mapped, 2, 5)
        assertEquals(ReaderUnderlineRange(0, sourceSignature, 0, 3), mark.ranges().single())
        assertEquals("Albedo", drawn(mark, mapped))
        assertEquals("阿贝多", drawn(mark, ReaderTextOffsets.identity(source)))
    }

    @Test fun markStaysOnSameOriginalTextWhenSwitchesAndRulesChange() {
        val withRules = settings(longer, shorter, removed)
        val mapped = display(withRules)
        val start = mapped.text.indexOf("去找琴")
        val mark = select(mapped, start, start + 3)
        assertEquals("去找琴团长", source.substring(mark.start, mark.end))
        val variants = mapOf(
            settings(longer, shorter, removed, enabled = false) to "去找琴团长",
            settings(longer, shorter.copy(enabled = false), removed) to "去找琴团长",
            settings(longer) to "去找琴团长",
            settings(longer, shorter.copy(target = "Jean"), removed) to "去找Jean",
            settings(longer, shorter, removed, ReaderNameReplaceRule(id = "d", source = "去找", target = "")) to "琴",
            settings() to "去找琴团长",
            withRules to "去找琴"
        )
        variants.forEach { (options, expected) -> assertEquals(expected, drawn(mark, display(options))) }
    }

    @Test fun adjacentAndOverlappingRulesKeepIndependentSpans() {
        val text = ReaderBlock.Text("阿贝多阿贝")
        val options = settings(ReaderNameReplaceRule(source = "阿贝多", target = "A"),
            ReaderNameReplaceRule(source = "阿贝", target = "BB"), ReaderNameReplaceRule(source = "阿", target = "C"))
        val mapped = display(options, text)
        assertEquals("ABB", mapped.text)
        assertEquals(0 to 3, mapped.toSource(0) to mapped.toSourceEnd(1))
        assertEquals(3 to 5, mapped.toSource(1) to mapped.toSourceEnd(2))
        assertEquals(1 to 3, mapped.toDisplay(3) to mapped.toDisplayEnd(5))
    }

    @Test fun replacementToEmptyTextHidesOnlyThatSpanAndKeepsTheMark() {
        val mapped = display(settings(removed))
        val name = source.indexOf("可莉")
        val onlyRemoved = ReaderUnderline(0, sourceSignature, name, name + 2)
        assertNull(ReaderUnderlines.displayRange(onlyRemoved.ranges().single(), sourceSignature,
            ReaderUnderlines.signature(block, mapped.text), mapped))
        assertEquals("可莉", drawn(onlyRemoved, ReaderTextOffsets.identity(source)))
        val around = ReaderUnderline(0, sourceSignature, name - 1, name + 3)
        assertEquals("和去", drawn(around, mapped))
        // A whole-block replacement to nothing leaves no drawable text but no crash.
        val gone = display(settings(ReaderNameReplaceRule(source = source, target = "")))
        assertEquals("", gone.text)
        assertNull(ReaderUnderlines.displayRange(ReaderUnderlineRange(0, sourceSignature, 0, source.length),
            sourceSignature, ReaderUnderlines.signature(block, ""), gone))
    }

    @Test fun selectionFromFirstVisibleCharacterSkipsNameRemovedAtParagraphStart() {
        val text = ReaderBlock.Text("可莉来了")
        val signature = ReaderUnderlines.sourceSignature(text)
        val mapped = display(settings(ReaderNameReplaceRule(source = "可莉", target = "")), text)
        assertEquals("来了", mapped.text)
        val selected = ReaderUnderlines.toSource(ReaderUnderline(0, ReaderUnderlines.signature(text, mapped.text), 0, 1)) {
            signature to mapped
        }
        assertEquals(ReaderUnderlineRange(0, signature, 2, 3), selected.ranges().single())
        val migrated = ReaderUnderlines.migrateToSource(listOf(ReaderUnderline(0,
            ReaderUnderlines.signature(text, mapped.text), 0, 1)), listOf(text)) { listOf(mapped) }
        assertEquals(ReaderUnderline(0, signature, 2, 3), migrated.single())
        // Anchors keep their paragraph-start meaning.
        assertEquals(0, mapped.toSource(0))
    }

    @Test fun legacyRangeDrawsOnlyOnItsExactRendering() {
        val mapped = display(settings(longer))
        val rendered = ReaderUnderlines.signature(block, mapped.text)
        val legacy = ReaderUnderlineRange(0, rendered, 0, 6)
        assertEquals(0 until 6, ReaderUnderlines.displayRange(legacy, sourceSignature, rendered, mapped))
        assertNull(ReaderUnderlines.displayRange(legacy, sourceSignature, sourceSignature, ReaderTextOffsets.identity(source)))
        // Without a mapping, original offsets are drawn only on untransformed text.
        val original = ReaderUnderlineRange(0, sourceSignature, 0, 3)
        assertEquals(0 until 3, ReaderUnderlines.displayRange(original, sourceSignature, sourceSignature, null))
        assertNull(ReaderUnderlines.displayRange(original, sourceSignature, rendered, null))
    }

    @Test fun unresolvedSelectionStaysInRenderedOffsets() {
        val rendered = ReaderUnderlines.signature(block, "x")
        val mark = ReaderUnderline(0, rendered, 0, 1)
        assertEquals(mark, ReaderUnderlines.toSource(mark) { null })
    }

    @Test fun legacyRenderedMarksMigrateToOriginalOffsets() {
        val options = settings(longer, shorter)
        val mapped = display(options)
        val rendered = ReaderUnderlines.signature(block, mapped.text)
        val second = mapped.text.lastIndexOf("Albedo")
        val legacy = ReaderUnderline(0, rendered, second, second + 8, quote = "Albedo笑了")
        val untouched = ReaderUnderline(0, sourceSignature, 4, 6, quote = "可莉")
        val unknown = ReaderUnderline(0, "f".repeat(64), 0, 2)
        val rows = ReaderUnderlines.decode(ReaderUnderlines.encode(listOf(legacy, untouched, unknown)))
        val renderings: (ReaderBlock) -> List<ReaderTextOffsets> = { listOf(ReaderTextOffsets.identity(it.sourceText()), display(options, it)) }
        assertTrue(ReaderUnderlines.hasLegacyRanges(rows, listOf(block)))
        val migrated = ReaderUnderlines.migrateToSource(rows, listOf(block), renderings)
        val start = source.lastIndexOf("阿贝多")
        assertEquals(listOf(ReaderUnderline(0, sourceSignature, start, start + 5, quote = "Albedo笑了"), untouched, unknown), migrated)
        assertEquals("阿贝多笑了", source.substring(start, start + 5))
        // Stable after migration, after a persistence round trip and once rules are switched off.
        assertSame(migrated, ReaderUnderlines.migrateToSource(migrated, listOf(block), renderings))
        assertEquals(migrated, ReaderUnderlines.decode(ReaderUnderlines.encode(migrated)))
        assertEquals("阿贝多笑了", drawn(migrated.first(), ReaderTextOffsets.identity(source)))
        assertEquals("Albedo笑了", drawn(migrated.first(), mapped))
    }

    @Test fun marksWithoutReplacementAreAlreadyOriginalAndNeedNoMigration() {
        val legacy = listOf(ReaderUnderline(0, ReaderUnderlines.signature(block, source), 1, 3))
        assertFalse(ReaderUnderlines.hasLegacyRanges(legacy, listOf(block)))
        assertSame(legacy, ReaderUnderlines.migrateToSource(legacy, listOf(block)) { error("not needed") })
    }

    @Test fun crossParagraphLegacyMarkMigratesEachRangeAndKeepsOrder() {
        val first = ReaderBlock.Text("前文没有人名")
        val second = ReaderBlock.Text("阿贝多来了")
        val options = settings(longer)
        val renderedSecond = ReaderUnderlines.signature(second, "Albedo来了")
        val legacy = ReaderUnderline(0, ReaderUnderlines.sourceSignature(first), 2, 6,
            continuation = listOf(ReaderUnderlineRange(1, renderedSecond, 0, 7)))
        val migrated = ReaderUnderlines.migrateToSource(listOf(legacy), listOf(first, second)) {
            listOf(display(options, it))
        }.single()
        assertEquals(listOf(ReaderUnderlineRange(0, ReaderUnderlines.sourceSignature(first), 2, 6),
            ReaderUnderlineRange(1, ReaderUnderlines.sourceSignature(second), 0, 4)), migrated.ranges())
        assertEquals(migrated, ReaderUnderlines.decode(ReaderUnderlines.encode(listOf(migrated))).single())
    }

    @Test fun migratedDuplicateOfANewerMarkCollapses() {
        val mapped = display(settings(longer))
        val legacy = ReaderUnderline(0, ReaderUnderlines.signature(block, mapped.text), 0, 6)
        val current = ReaderUnderline(0, sourceSignature, 0, 3)
        assertEquals(listOf(current), ReaderUnderlines.migrateToSource(listOf(legacy, current), listOf(block)) { listOf(mapped) })
    }

    @Test fun savedMarksMergeInOriginalOffsetsAndQuoteDisplayedText() {
        val mapped = display(settings(longer))
        val firstMark = select(mapped, 0, 6)
        val quote: (ReaderUnderlineRange) -> String? = { range -> readerUnderlineQuote(block, range) { mapped } }
        val saved = ReaderUnderlines.update(emptyList(), firstMark, false, quote)
        assertEquals("Albedo", saved.single().quote)
        // A later selection made with replacement off overlaps the same original text.
        val identity = ReaderTextOffsets.identity(source)
        val merged = ReaderUnderlines.update(saved, select(identity, 2, 5), false, quote)
        assertEquals(ReaderUnderline(0, sourceSignature, 0, 5, quote = "Albedo和可"), merged.single())
        assertTrue(ReaderUnderlines.update(merged, select(mapped, 1, 2), true).isEmpty())
    }

    @Test fun searchHitInsideReplacementHighlightsWholeDisplayedName() {
        val document = ReaderChapterDocument(ReaderChapterRef("c", "u"), listOf(block), contentFingerprint = "a".repeat(64))
        val mapped = display(settings(longer))
        val hit = searchReaderDocument(document, "k", "bed") { mapped }.first()
        val range = hit.highlights.single()
        assertEquals(0 to 3, range.start to range.end)
        assertEquals("Albedo", mapped.text.substring(mapped.toDisplay(range.start), mapped.toDisplayEnd(range.end)))
        assertEquals(0, hit.anchor.offset)
    }
}
