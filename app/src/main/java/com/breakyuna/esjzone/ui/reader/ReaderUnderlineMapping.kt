package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineRange
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.domain.reader.sourceText

internal data class ReaderUnderlinePosition(val anchor: ReaderAnchor?, val contentPosition: Float?)

/** Resolve original or legacy rendered offsets; changed text falls back to the saved paragraph. */
internal fun readerUnderlinePosition(document: ReaderChapterDocument, chapterKey: String, mark: ReaderUnderline,
    textTransform: (String) -> String, textOffsets: (ReaderBlock) -> ReaderTextOffsets?): ReaderUnderlinePosition {
    val block = document.blocks.getOrNull(mark.blockIndex)
    val sourceLength = block?.sourceText()?.length ?: 0
    val original = block != null && mark.signature == ReaderUnderlines.sourceSignature(block) && mark.end <= sourceLength
    val rendered = block?.renderedReaderText(textTransform)
    val valid = original || block != null && rendered != null && mark.end <= rendered.length &&
        ReaderUnderlines.signature(block, rendered) == mark.signature
    val sourceStart = if (original) mark.start else block?.let { textOffsets(it)?.toSource(mark.start) } ?: mark.start
    val anchor = if (valid) ReaderAnchor(chapterKey, document.contentFingerprint, mark.blockIndex, sourceStart) else null
    val position = if (!valid) document.blocks.lastIndex.takeIf { it >= 0 }
        ?.let { mark.blockIndex.coerceIn(0, it).toFloat() }
        else if (original) mark.blockIndex + mark.start.toFloat() / sourceLength
        else mark.blockIndex + mark.start.toFloat() / rendered!!.length
    return ReaderUnderlinePosition(anchor, position)
}

/** Displayed block with its original offsets; safe off the main thread. */
internal fun readerDisplayOffsets(block: ReaderBlock, script: ReaderScript,
    names: ReaderNameReplaceSettings): ReaderTextOffsets =
    if (ReaderNameReplace.isActive(names)) ReaderNameReplace.blockMapping(block, names) {
        ReaderScriptConverter.blockMapping(it, script)
    } else ReaderScriptConverter.blockMapping(block, script)

/**
 * Renderings a legacy underline may have been saved in: every script, with the
 * current rules, with every rule switched on, and without replacement.
 */
internal fun readerLegacyUnderlineRenderings(block: ReaderBlock, names: ReaderNameReplaceSettings): List<ReaderTextOffsets> {
    val nameOptions = listOf(names.copy(enabled = true),
        names.copy(enabled = true, rules = names.rules.map { it.copy(enabled = true) }),
        ReaderNameReplaceSettings()).distinct()
    return ReaderScript.entries.flatMap { script -> nameOptions.map { readerDisplayOffsets(block, script, it) } }
        .distinctBy { it.text }
}

/** Displayed text of one saved range, used as the contents-panel quote. */
internal fun readerUnderlineQuote(block: ReaderBlock?, range: ReaderUnderlineRange, displayed: (ReaderBlock) -> ReaderTextOffsets): String? {
    block ?: return null
    val offsets = displayed(block)
    if (range.signature == ReaderUnderlines.sourceSignature(block)) {
        val start = offsets.toDisplay(range.start)
        return offsets.text.substring(start, offsets.toDisplayEnd(range.end).coerceAtLeast(start))
    }
    return offsets.text.takeIf { range.end <= it.length }?.substring(range.start, range.end)
}
