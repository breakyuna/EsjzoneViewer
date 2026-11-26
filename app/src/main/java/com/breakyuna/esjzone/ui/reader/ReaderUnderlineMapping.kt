package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineAnchors
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineRange
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.domain.reader.sourceText

internal data class ReaderUnderlinePosition(val anchor: ReaderAnchor?, val contentPosition: Float?)

/** Resolve original or legacy rendered offsets; changed text falls back to the saved paragraph. */
internal fun readerUnderlinePosition(document: ReaderChapterDocument, chapterKey: String, mark: ReaderUnderline,
    textTransform: (String) -> String, legacyOffsets: (ReaderBlock) -> ReaderTextOffsets?,
    names: ReaderNameReplaceSettings = ReaderNameReplaceSettings()): ReaderUnderlinePosition {
    val block = document.blocks.getOrNull(mark.blockIndex)
    val sourceLength = block?.sourceText()?.length ?: 0
    val original = block != null && mark.signature == ReaderUnderlines.sourceSignature(block) && mark.end <= sourceLength
    val rendered = block?.untrimmedReaderText(textTransform)
    val valid = original || block != null && rendered != null && mark.end <= rendered.length &&
        ReaderUnderlines.signature(block, rendered) == mark.signature
    if (valid) {
        val sourceStart = if (original) mark.start else block?.let { legacyOffsets(it)?.toSource(mark.start) } ?: mark.start
        val anchor = ReaderAnchor(chapterKey, document.contentFingerprint, mark.blockIndex, sourceStart)
        val position = if (original) mark.blockIndex + mark.start.toFloat() / sourceLength
            else mark.blockIndex + mark.start.toFloat() / rendered!!.length
        return ReaderUnderlinePosition(anchor, position)
    }
    val reanchored = readerUnderlineAnchors(document, names).resolve(mark)
    if (reanchored != null) {
        val targetBlock = document.blocks.getOrNull(reanchored.blockIndex)
        val targetLength = targetBlock?.sourceText()?.length ?: 0
        val anchor = ReaderAnchor(chapterKey, document.contentFingerprint, reanchored.blockIndex, reanchored.start)
        val position = if (targetLength > 0) reanchored.blockIndex + reanchored.start.toFloat() / targetLength
            else reanchored.blockIndex.toFloat()
        return ReaderUnderlinePosition(anchor, position)
    }
    val position = document.blocks.lastIndex.takeIf { it >= 0 }
        ?.let { mark.blockIndex.coerceIn(0, it).toFloat() }
    return ReaderUnderlinePosition(null, position)
}

/** Displayed block with its original offsets; safe off the main thread. */
internal fun readerDisplayOffsets(block: ReaderBlock, script: ReaderScript,
    names: ReaderNameReplaceSettings): ReaderTextOffsets {
    val offsets = ReaderScriptConverter.untrimmedBlockMapping(block, script, names)
    return readerUnindentedOffsets(offsets.text, offsets) ?: offsets
}

/**
 * Renderings a legacy underline may have been saved in: every script, with the
 * current rules, with every rule switched on, and without replacement.
 */
internal fun readerLegacyUnderlineRenderings(block: ReaderBlock, names: ReaderNameReplaceSettings): List<ReaderTextOffsets> {
    return readerUnderlineRenderingOptions(names).map { it(block) }.distinctBy { it.text }
}

internal fun readerUnderlineAnchors(document: ReaderChapterDocument, names: ReaderNameReplaceSettings) =
    ReaderUnderlineAnchors(document.blocks, document.legacyPositions, readerUnderlineRenderingOptions(names)) {
        document.normalizedTextIndex
    }

private fun readerUnderlineRenderingOptions(names: ReaderNameReplaceSettings): List<(ReaderBlock) -> ReaderTextOffsets> {
    val nameOptions = listOf(names.copy(enabled = true),
        names.copy(enabled = true, rules = names.rules.map { it.copy(enabled = true) }))
        .filter(ReaderNameReplace::isActive).distinct() + ReaderNameReplaceSettings()
    return ReaderScript.entries.flatMap { script ->
        nameOptions.filter { script != ReaderScript.ORIGINAL || it.enabled }
            .map { option -> { block: ReaderBlock -> ReaderScriptConverter.untrimmedBlockMapping(block, script, option) } }
    }
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
