package com.breakyuna.esjzone.domain.reader

import androidx.compose.runtime.Immutable

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
    val contentFingerprint: String = ""
) {
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

@Immutable
data class ReadingProgress(
    val activityId: String,
    val novelId: String,
    val novelName: String,
    val novelUrl: String,
    val chapterUrl: String,
    val chapterName: String,
    val chapterIndex: Int,
    val totalChapters: Int,
    val chapterProgress: Float,
    val startedAt: Long,
    val lastReadAt: Long,
    val durationMs: Long,
    val novelCoverUrl: String = "",
    val anchor: String? = null,
    val bookProgress: Float? = null
)

@Immutable
data class ReaderBookmark(
    val chapterUrl: String,
    val novelId: String,
    val novelName: String,
    val chapterName: String,
    val createdAt: Long
)

@Immutable
data class ReaderSettingsState(
    val background: String = "SYSTEM",
    val font: String = "SYSTEM",
    val fontSizeSp: Float = 18f,
    val letterSpacingSp: Float = 0.3f,
    val lineSpacingSp: Float = 10f,
    val paragraphSpacingDp: Float = 10f,
    val pageSpacingDp: Float = 32f,
    val horizontalPaddingDp: Float = 20f,
    val script: String = "ORIGINAL"
)
