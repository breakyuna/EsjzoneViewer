package com.breakyuna.esjzone.ui.reader

import androidx.compose.runtime.Immutable
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets
import java.util.UUID

@Immutable
data class ReaderNameReplaceRule(
    val id: String = UUID.randomUUID().toString(),
    val source: String,
    val target: String,
    val enabled: Boolean = true,
    val bookKey: String? = null,
    val regex: Boolean = false
)

@Immutable
data class ReaderNameReplaceSettings(
    val enabled: Boolean = false,
    val rules: List<ReaderNameReplaceRule> = emptyList(),
    val activeBookKey: String? = null
)

/** Display-only replacement, applied once without cascading into replacement text. */
object ReaderNameReplace {
    private fun applicable(rule: ReaderNameReplaceRule, settings: ReaderNameReplaceSettings): Boolean =
        rule.enabled && rule.source.isNotBlank() &&
            (rule.bookKey == null || rule.bookKey == settings.activeBookKey)

    fun isActive(settings: ReaderNameReplaceSettings): Boolean =
        settings.enabled && settings.rules.any { applicable(it, settings) }

    private fun scan(
        text: String,
        settings: ReaderNameReplaceSettings,
        append: (start: Int, end: Int, target: String) -> Unit
    ) {
        val rules = settings.rules.filter { applicable(it, settings) }
            .sortedByDescending { it.source.length }
            .mapNotNull { rule ->
                runCatching {
                    val pattern = if (rule.regex) rule.source else java.util.regex.Pattern.quote(rule.source)
                    rule to java.util.regex.Pattern.compile(pattern).matcher(text)
                }.getOrNull()
            }
        var index = 0
        while (index <= text.length) {
            var start = text.length + 1
            var end = start
            var target = ""
            for ((rule, matcher) in rules) {
                if (!matcher.find(index) || matcher.start() >= start) continue
                val replacement = if (rule.regex) runCatching {
                    // A fresh find resets append position to zero; discard the unchanged prefix.
                    val buffer = StringBuffer()
                    matcher.appendReplacement(buffer, rule.target)
                    buffer.substring(matcher.start())
                }.getOrNull() ?: continue else rule.target
                start = matcher.start()
                end = matcher.end()
                target = replacement
            }
            while (index < minOf(start, text.length)) {
                append(index, index + 1, text.substring(index, index + 1))
                index++
            }
            if (start > text.length) break
            append(start, end, target)
            index = end
            if (end == start) {
                if (index == text.length) break
                append(index, index + 1, text.substring(index, index + 1))
                index++
            }
        }
    }

    fun apply(text: String, settings: ReaderNameReplaceSettings): String {
        if (!isActive(settings) || text.isEmpty()) return text
        return buildString(text.length) {
            scan(text, settings) { _, _, target -> append(target) }
        }
    }

    fun mapping(offsets: ReaderTextOffsets, settings: ReaderNameReplaceSettings): ReaderTextOffsets {
        if (!isActive(settings) || offsets.text.isEmpty()) return offsets
        val starts = ArrayList<Int>()
        val ends = ArrayList<Int>()
        val text = buildString(offsets.text.length) {
            scan(offsets.text, settings) { start, end, target ->
                append(target)
                repeat(target.length) {
                    val sourceStart = offsets.sourceStarts.getOrElse(start) { offsets.sourceLength }
                    starts.add(sourceStart)
                    ends.add(if (end > start) offsets.sourceEnds[end - 1] else sourceStart)
                }
            }
        }
        return ReaderTextOffsets(text, starts.toIntArray(), ends.toIntArray(), offsets.sourceLength)
    }

    fun blockMapping(
        block: ReaderBlock,
        settings: ReaderNameReplaceSettings,
        base: (ReaderBlock) -> ReaderTextOffsets
    ): ReaderTextOffsets = when (block) {
        is ReaderBlock.Paragraph -> ReaderTextOffsets.join(block.parts.map { mapping(base(it), settings) })
        is ReaderBlock.Text -> mapping(base(block), settings)
        else -> base(block)
    }
}
