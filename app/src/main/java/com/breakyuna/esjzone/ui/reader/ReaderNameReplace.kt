package com.breakyuna.esjzone.ui.reader

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class ReaderNameReplaceRule(
    val id: String = UUID.randomUUID().toString(),
    val source: String,
    val target: String,
    val enabled: Boolean = true
)

@Immutable
data class ReaderNameReplaceSettings(
    val enabled: Boolean = false,
    val rules: List<ReaderNameReplaceRule> = emptyList()
)

/**
 * Display-only literal replacement. Rules match case-sensitively, never as
 * regular expressions, and longer sources win when several rules share a
 * prefix. Replacing a name can change the rendered length, so underline and
 * search offsets may shift when source and target differ in length.
 */
object ReaderNameReplace {
    fun apply(text: String, settings: ReaderNameReplaceSettings): String {
        if (!settings.enabled || settings.rules.isEmpty() || text.isEmpty()) return text
        val rules = settings.rules
            .filter { it.enabled && it.source.isNotBlank() }
            .sortedByDescending { it.source.length }
        if (rules.isEmpty()) return text
        val result = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val match = rules.firstOrNull { text.startsWith(it.source, index) }
            if (match == null) {
                result.append(text[index])
                index++
            } else {
                result.append(match.target)
                index += match.source.length
            }
        }
        return result.toString()
    }
}
