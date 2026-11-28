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
    private const val MAX_MATCHES = 4096
    private const val MAX_ROLLBACKS = 8
    private const val MAX_PATTERN_CACHE = 256
    private const val MAX_RULE_SET_CACHE = 64

    private class PatternEntry(val pattern: com.google.re2j.Pattern?)
    private data class RuleSignature(val source: String, val target: String, val regex: Boolean)
    private data class RuleSetKey(val activeBookKey: String?, val rules: List<RuleSignature>)

    private sealed interface CompiledRule {
        val source: String
        val target: String

        data class Literal(override val source: String, override val target: String) : CompiledRule
        data class Regex(
            override val source: String,
            override val target: String,
            val pattern: com.google.re2j.Pattern
        ) : CompiledRule
    }

    private val patternCache = object : LinkedHashMap<String, PatternEntry>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PatternEntry>?): Boolean =
            size > MAX_PATTERN_CACHE
    }

    private val ruleSetCache = object : LinkedHashMap<RuleSetKey, List<CompiledRule>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RuleSetKey, List<CompiledRule>>?): Boolean =
            size > MAX_RULE_SET_CACHE
    }

    private fun compileRegex(source: String): com.google.re2j.Pattern? {
        synchronized(patternCache) {
            patternCache[source]?.let { return it.pattern }
        }
        val compiled = runCatching {
            com.google.re2j.Pattern.compile(source).takeIf { it.programSize() <= 512 }
        }.getOrNull()
        synchronized(patternCache) {
            patternCache.putIfAbsent(source, PatternEntry(compiled))?.let { return it.pattern }
        }
        return compiled
    }

    fun supportedPattern(source: String): Boolean =
        source.length <= MAX_PATTERN && compileRegex(source) != null

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

    private fun compiledRules(settings: ReaderNameReplaceSettings): List<CompiledRule> {
        if (!settings.enabled) return emptyList()
        val applicableRules = settings.rules.take(MAX_RULES).filter { applicable(it, settings) }
        if (applicableRules.isEmpty()) return emptyList()
        val key = RuleSetKey(
            settings.activeBookKey,
            applicableRules.map { RuleSignature(it.source, it.target, it.regex) }
        )
        synchronized(ruleSetCache) {
            ruleSetCache[key]?.let { return it }
        }
        val compiled = applicableRules
            .sortedByDescending { it.source.length }
            .mapNotNull { rule ->
                if (rule.regex) {
                    compileRegex(rule.source)?.let { CompiledRule.Regex(rule.source, rule.target, it) }
                } else {
                    CompiledRule.Literal(rule.source, rule.target)
                }
            }
        synchronized(ruleSetCache) {
            ruleSetCache.putIfAbsent(key, compiled)?.let { return it }
        }
        return compiled
    }

    private data class Edit(val start: Int, val end: Int, val target: String)
    private class BudgetExceeded : RuntimeException()

    /** Expand captures without appendReplacement copying the entire prefix for every match. */
    private fun replacement(rule: CompiledRule.Regex, matcher: com.google.re2j.Matcher): String? = runCatching {
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
        if (!settings.enabled || text.isEmpty() || text.length > MAX_INPUT) return null
        val rules = compiledRules(settings)
        if (rules.isEmpty()) return null

        val disabled = BooleanArray(rules.size)
        var rollbacks = 0
        while (true) {
            val result = ArrayList<Edit>()
            val matchCounts = IntArray(rules.size)
            val matchStarts = IntArray(rules.size)
            val matchEnds = IntArray(rules.size)
            val foundFlags = BooleanArray(rules.size)
            val startedFlags = BooleanArray(rules.size)
            val matchers = arrayOfNulls<com.google.re2j.Matcher>(rules.size)
            for (i in rules.indices) {
                if (!disabled[i]) {
                    val rule = rules[i]
                    if (rule is CompiledRule.Regex) {
                        matchers[i] = rule.pattern.matcher(text)
                    }
                }
            }

            var index = 0
            var outputLength = text.length.toLong()
            var needsRestart = false
            while (index <= text.length) {
                var chosen: Edit? = null
                var chosenRule = -1
                for (i in rules.indices) {
                    if (disabled[i]) continue
                    if (matchCounts[i] >= MAX_MATCHES) {
                        disabled[i] = true
                        continue
                    }
                    if (!startedFlags[i] || (foundFlags[i] && matchStarts[i] < index)) {
                        startedFlags[i] = true
                        when (val rule = rules[i]) {
                            is CompiledRule.Literal -> {
                                val pos = text.indexOf(rule.source, index)
                                if (pos >= 0) {
                                    foundFlags[i] = true
                                    matchStarts[i] = pos
                                    matchEnds[i] = pos + rule.source.length
                                } else {
                                    foundFlags[i] = false
                                }
                            }
                            is CompiledRule.Regex -> {
                                val matcher = matchers[i]!!
                                if (matcher.find(index)) {
                                    foundFlags[i] = true
                                    matchStarts[i] = matcher.start()
                                    matchEnds[i] = matcher.end()
                                } else {
                                    foundFlags[i] = false
                                }
                            }
                        }
                    }
                    if (!foundFlags[i]) continue
                    val start = matchStarts[i]
                    if (chosen != null && start >= chosen.start) continue
                    val target = when (val rule = rules[i]) {
                        is CompiledRule.Literal -> rule.target
                        is CompiledRule.Regex -> try {
                            replacement(rule, matchers[i]!!)
                        } catch (_: BudgetExceeded) {
                            disabled[i] = true
                            if (matchCounts[i] > 0) {
                                rollbacks++
                                if (rollbacks > MAX_ROLLBACKS) return null
                                needsRestart = true
                                break
                            }
                            continue
                        }
                    } ?: continue
                    chosen = Edit(start, matchEnds[i], target)
                    chosenRule = i
                }
                if (needsRestart) break
                val edit = chosen ?: break
                val delta = edit.target.length.toLong() - (edit.end - edit.start)
                if (outputLength + delta > MAX_OUTPUT) {
                    disabled[chosenRule] = true
                    if (matchCounts[chosenRule] > 0) {
                        rollbacks++
                        if (rollbacks > MAX_ROLLBACKS) return null
                        needsRestart = true
                        break
                    }
                    continue
                }
                outputLength += delta
                matchCounts[chosenRule]++
                result.add(edit)
                index = if (edit.end > edit.start) edit.end else edit.end + 1
            }
            if (!needsRestart) return result
        }
    }

    fun apply(text: String, settings: ReaderNameReplaceSettings): String {
        val edits = edits(text, settings)?.takeIf { it.isNotEmpty() } ?: return text
        return buildString(text.length) {
            var index = 0
            for (edit in edits) {
                if (index < edit.start) append(text, index, edit.start)
                append(edit.target)
                index = edit.end
            }
            if (index < text.length) append(text, index, text.length)
        }
    }

    fun mapping(offsets: ReaderTextOffsets, settings: ReaderNameReplaceSettings): ReaderTextOffsets {
        val sourceText = offsets.text
        val edits = edits(sourceText, settings)?.takeIf { it.isNotEmpty() } ?: return offsets
        var delta = 0
        for (edit in edits) {
            delta += edit.target.length - (edit.end - edit.start)
        }
        val finalLength = sourceText.length + delta
        val starts = IntArray(finalLength)
        val ends = IntArray(finalLength)
        val text = buildString(finalLength) {
            var index = 0
            var out = 0
            for (edit in edits) {
                val gap = edit.start - index
                if (gap > 0) {
                    append(sourceText, index, edit.start)
                    System.arraycopy(offsets.sourceStarts, index, starts, out, gap)
                    System.arraycopy(offsets.sourceEnds, index, ends, out, gap)
                    out += gap
                }
                val targetLength = edit.target.length
                if (targetLength > 0) {
                    append(edit.target)
                    val sourceStart = offsets.sourceStarts.getOrElse(edit.start) { offsets.sourceLength }
                    val sourceEnd = if (edit.end > edit.start) offsets.sourceEnds[edit.end - 1] else sourceStart
                    starts.fill(sourceStart, out, out + targetLength)
                    ends.fill(sourceEnd, out, out + targetLength)
                    out += targetLength
                }
                index = edit.end
            }
            val tail = sourceText.length - index
            if (tail > 0) {
                append(sourceText, index, sourceText.length)
                System.arraycopy(offsets.sourceStarts, index, starts, out, tail)
                System.arraycopy(offsets.sourceEnds, index, ends, out, tail)
            }
        }
        return ReaderTextOffsets(text, starts, ends, offsets.sourceLength)
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
