package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineRange
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines

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
