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
    const val MAX_RULES = 128
    const val MAX_PATTERN = 512
    const val MAX_TARGET = 4096
    private const val MAX_INPUT = 131_072
    private const val MAX_OUTPUT = 262_144
    private const val MAX_WORK = 16_000_000L
    private const val MAX_MATCHES = 4096

    fun supportedPattern(source: String): Boolean = source.length <= MAX_PATTERN && runCatching {
        com.google.re2j.Pattern.compile(source).programSize() <= 512
    }.getOrDefault(false)

    fun validate(settings: ReaderNameReplaceSettings) {
        require(settings.rules.size <= MAX_RULES) { "Too many replacement rules" }
        require(settings.rules.all { it.source.length <= MAX_PATTERN && it.target.length <= MAX_TARGET }) {
            "Replacement rule exceeds size limit"
        }
    }

    private fun applicable(rule: ReaderNameReplaceRule, settings: ReaderNameReplaceSettings): Boolean =
        rule.enabled && rule.source.isNotBlank() && rule.source.length <= MAX_PATTERN &&
            rule.target.length <= MAX_TARGET && (rule.bookKey == null || rule.bookKey == settings.activeBookKey)

    fun isActive(settings: ReaderNameReplaceSettings): Boolean =
        settings.enabled && settings.rules.take(MAX_RULES).any { applicable(it, settings) }

    private data class Edit(val start: Int, val end: Int, val target: String)
    private class BudgetExceeded : RuntimeException()
    private class Candidate(val rule: ReaderNameReplaceRule, val matcher: com.google.re2j.Matcher) {
        var found = false
        var started = false
        fun seek(index: Int) {
            if (!started || (found && matcher.start() < index)) {
                found = matcher.find(index)
                started = true
            }
        }
    }

    /** Expand captures without appendReplacement copying the entire prefix for every match. */
    private fun replacement(rule: ReaderNameReplaceRule, matcher: com.google.re2j.Matcher): String? = runCatching {
        if (!rule.regex) return@runCatching rule.target
        buildString {
            var index = 0
            fun add(value: String) {
                if (length + value.length > MAX_OUTPUT) throw BudgetExceeded()
                append(value)
            }
            while (index < rule.target.length) {
                val char = rule.target[index++]
                when (char) {
                    '\\' -> { require(index < rule.target.length); add(rule.target[index++].toString()) }
                    '$' -> {
                        require(index < rule.target.length)
                        if (rule.target[index] == '{') {
                            val end = rule.target.indexOf('}', index + 1)
                            require(end > index + 1)
                            add(matcher.group(rule.target.substring(index + 1, end)).orEmpty())
                            index = end + 1
                        } else {
                            require(rule.target[index] in '0'..'9')
                            var group = rule.target[index++].digitToInt()
                            require(group <= matcher.groupCount())
                            while (index < rule.target.length && rule.target[index] in '0'..'9') {
                                val next = group * 10 + rule.target[index].digitToInt()
                                if (next > matcher.groupCount()) break
                                group = next
                                index++
                            }
                            add(matcher.group(group).orEmpty())
                        }
                    }
                    else -> add(char.toString())
                }
            }
        }
    }.getOrElse { error ->
        if (error is BudgetExceeded) throw error
        null
    }

    private fun edits(text: String, settings: ReaderNameReplaceSettings): List<Edit>? {
        if (!isActive(settings) || text.isEmpty() || text.length > MAX_INPUT) return null
        var work = 0L
        val candidates = settings.rules.take(MAX_RULES).filter { applicable(it, settings) }
            .sortedByDescending { it.source.length }.mapNotNull { rule ->
                runCatching {
                    val pattern = com.google.re2j.Pattern.compile(
                        if (rule.regex) rule.source else com.google.re2j.Pattern.quote(rule.source))
                    if (pattern.programSize() > 512) return@runCatching null
                    work += pattern.programSize().toLong() * text.length
                    Candidate(rule, pattern.matcher(text))
                }.getOrNull()
            }
        if (work > MAX_WORK) return null
        val result = ArrayList<Edit>()
        var index = 0
        var outputLength = text.length.toLong()
        while (index <= text.length) {
            var chosen: Edit? = null
            for (candidate in candidates) {
                candidate.seek(index)
                if (!candidate.found) continue
                val matcher = candidate.matcher
                if (chosen != null && matcher.start() >= chosen.start) continue
                val target = try { replacement(candidate.rule, matcher) }
                    catch (_: BudgetExceeded) { return null }
                if (target == null) continue
                chosen = Edit(matcher.start(), matcher.end(), target)
            }
            val edit = chosen ?: break
            if (result.size >= MAX_MATCHES) return null
            outputLength += edit.target.length.toLong() - (edit.end - edit.start)
            if (outputLength > MAX_OUTPUT) return null
            result.add(edit)
            index = if (edit.end > edit.start) edit.end else edit.end + 1
        }
        return result
    }

    private fun scan(text: String, edits: List<Edit>, append: (Int, Int, String) -> Unit) {
        var index = 0
        for (edit in edits) {
            while (index < edit.start) {
                append(index, index + 1, text.substring(index, index + 1))
                index++
            }
            append(edit.start, edit.end, edit.target)
            index = edit.end
        }
        while (index < text.length) {
            append(index, index + 1, text.substring(index, index + 1))
            index++
        }
    }

    fun apply(text: String, settings: ReaderNameReplaceSettings): String {
        val edits = edits(text, settings)?.takeIf { it.isNotEmpty() } ?: return text
        return buildString(text.length) { scan(text, edits) { _, _, target -> append(target) } }
    }

    fun mapping(offsets: ReaderTextOffsets, settings: ReaderNameReplaceSettings): ReaderTextOffsets {
        val edits = edits(offsets.text, settings)?.takeIf { it.isNotEmpty() } ?: return offsets
        val starts = ArrayList<Int>()
        val ends = ArrayList<Int>()
        val text = buildString(offsets.text.length) {
            scan(offsets.text, edits) { start, end, target ->
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
