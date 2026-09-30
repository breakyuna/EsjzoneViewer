package com.breakyuna.esjzone.ui.reader

internal data class ReaderBoundaryTurn(
    val forward: Boolean,
    val anchorKey: String,
    val targetChapterKey: String
)

/** Resolve against content identities, including when adding a chapter trims the opposite end. */
internal fun resolveReaderBoundaryPage(
    turn: ReaderBoundaryTurn,
    pageKeys: List<String>,
    chapterKeys: List<String>
): Int? {
    val anchor = pageKeys.indexOf(turn.anchorKey)
    if (anchor < 0) return null
    val target = anchor + if (turn.forward) 1 else -1
    return target.takeIf { it in pageKeys.indices && chapterKeys.getOrNull(it) == turn.targetChapterKey }
}
