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
    val enabled: Boolean = true
)

@Immutable
data class ReaderNameReplaceSettings(
    val enabled: Boolean = false,
    val rules: List<ReaderNameReplaceRule> = emptyList()
)

/** Display-only, case-sensitive literal replacement; longer sources win. */
object ReaderNameReplace {
    fun isActive(settings: ReaderNameReplaceSettings): Boolean =
        settings.enabled && settings.rules.any { it.enabled && it.source.isNotBlank() }

    private inline fun scan(
        text: String,
        settings: ReaderNameReplaceSettings,
        append: (start: Int, end: Int, target: String) -> Unit
    ) {
        val rules = settings.rules.filter { it.enabled && it.source.isNotBlank() }
            .sortedByDescending { it.source.length }
        var index = 0
        while (index < text.length) {
            val match = rules.firstOrNull { text.startsWith(it.source, index) }
            val end = index + (match?.source?.length ?: 1)
            append(index, end, match?.target ?: text.substring(index, end))
            index = end
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
                    starts.add(offsets.sourceStarts[start])
                    ends.add(offsets.sourceEnds[end - 1])
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
