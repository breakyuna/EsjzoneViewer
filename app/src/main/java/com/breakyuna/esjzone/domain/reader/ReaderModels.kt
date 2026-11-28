package com.breakyuna.esjzone.domain.reader

import androidx.compose.runtime.Immutable
import java.io.Serializable

/** Stable identity for a chapter. The domain layer never depends on Compose or navigation. */
@Immutable
data class ReaderChapterRef(
    val name: String,
    val url: String
)

@Immutable
sealed interface ReaderTextStyle {
    data object Bold : ReaderTextStyle
    data object Italic : ReaderTextStyle
    data object Underline : ReaderTextStyle
    data object StrikeThrough : ReaderTextStyle
    data class ForegroundColor(val red: Int, val green: Int, val blue: Int) : ReaderTextStyle
    data class BackgroundColor(val red: Int, val green: Int, val blue: Int) : ReaderTextStyle
    data class FontSizePx(val value: Int) : ReaderTextStyle
}

/** Ruby is kept as data instead of an inline Compose implementation. */
@Immutable
data class ReaderRuby(
    val base: String,
    val reading: String
)

/** Link range in the original UTF-16 text of one reader block. */
@Immutable
data class ReaderLink(val start: Int, val end: Int, val url: String)

/** Presentation metadata is separate from text identity and its saved anchors. */
@Immutable
data class ReaderChapterFormatting(
    val alignments: Map<Int, String> = emptyMap(),
    /** A position is the number of body blocks preceding the horizontal rule. */
    val dividersAt: List<Int> = emptyList(),
    /** Missing fields in older snapshots retain 0; the parser stamps new formatting explicitly. */
    val parserVersion: Int = 0,
    /** Number of source text parts belonging to the visible summary at the start of a block. */
    val disclosureTitleParts: Map<Int, Int> = emptyMap(),
    /** True when the primary rich parser failed content conservation and lossless fallback was used. */
    val degraded: Boolean = false
) : Serializable {
    companion object {
        const val CURRENT_PARSER_VERSION = 2
    }
}

/** The renderer can map this AST to any UI toolkit without reparsing HTML. */
@Immutable
sealed interface ReaderBlock {
    /** One source paragraph; its spans must flow inline instead of becoming rows. */
    @Immutable
    data class Paragraph(val parts: List<Text>) : ReaderBlock

    @Immutable
    data class Text(
        val value: String,
        val styles: Set<ReaderTextStyle> = emptySet(),
        val ruby: ReaderRuby? = null
    ) : ReaderBlock

    @Immutable
    data class Image(val url: String) : ReaderBlock

    data object LineBreak : ReaderBlock
}

@Immutable
data class ReaderChapterDocument(
    val chapter: ReaderChapterRef,
    val blocks: List<ReaderBlock>,
    val previous: ReaderChapterRef? = null,
    val next: ReaderChapterRef? = null,
    val contentHtml: String? = null,
    val sourceUrl: String? = null,
    val contentFingerprint: String = "",
    val links: Map<Int, List<ReaderLink>> = emptyMap(),
    val formatting: ReaderChapterFormatting = ReaderChapterFormatting(),
    val legacyPositions: ReaderLegacyPositions? = null
) {
    internal val normalizedTextIndex by lazy { buildNormalizedDocumentText(blocks) }

    /** Derived once per immutable document, without allocating paragraph strings. */
    internal val textPrefixLengths: IntArray by lazy {
        IntArray(blocks.size + 1).also { prefix ->
            blocks.forEachIndexed { index, block ->
                val length = when (block) {
                    is ReaderBlock.Paragraph -> block.parts.sumOf { it.value.length }
                    is ReaderBlock.Text -> block.value.length
                    else -> 0
                }
                prefix[index + 1] = prefix[index] + length
            }
        }
    }
}
