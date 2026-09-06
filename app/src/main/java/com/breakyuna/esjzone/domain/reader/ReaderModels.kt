package com.breakyuna.esjzone.domain.reader

/** Stable identity for a chapter. The domain layer never depends on Compose or navigation. */
data class ReaderChapterRef(
    val name: String,
    val url: String
)

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
data class ReaderRuby(
    val base: String,
    val reading: String
)

/** The renderer can map this AST to any UI toolkit without reparsing HTML. */
sealed interface ReaderBlock {
    data class Text(
        val value: String,
        val styles: Set<ReaderTextStyle> = emptySet(),
        val ruby: ReaderRuby? = null
    ) : ReaderBlock

    data class Image(val url: String) : ReaderBlock

    data object LineBreak : ReaderBlock
}

data class ReaderChapterDocument(
    val chapter: ReaderChapterRef,
    val blocks: List<ReaderBlock>,
    val previous: ReaderChapterRef? = null,
    val next: ReaderChapterRef? = null,
    val contentHtml: String? = null,
    val sourceUrl: String? = null
)

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
    val novelCoverUrl: String = ""
)

data class ReaderBookmark(
    val chapterUrl: String,
    val novelId: String,
    val novelName: String,
    val chapterName: String,
    val createdAt: Long
)

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
