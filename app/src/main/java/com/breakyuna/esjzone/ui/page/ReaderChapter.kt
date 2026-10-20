package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.offline.BookTextProgressIndex
import com.breakyuna.esjzone.ui.reader.toReaderDocument

data class ReaderChapter(
    val chapter: Chapter,
    /** Reader presentation consumes the stable domain AST, not HTML components. */
    val document: ReaderChapterDocument,
    val fallbackPrevious: Chapter? = null,
    val fallbackNext: Chapter? = null,
    val isOffline: Boolean = false,
    val requiresPassword: Boolean = false
) {
    companion object {
        fun passwordRequired(chapter: Chapter): ReaderChapter = ReaderChapter(
            chapter = chapter,
            document = ReaderChapterDocument(ReaderChapterRef(chapter.name, chapter.url), emptyList()),
            requiresPassword = true
        )
    }
}

/** Password input stays in the composable; only request status is published. */
data class ReaderChapterPasswordState(
    val submitting: Boolean = false,
    val message: String? = null,
    val failure: LoadFailureKind? = null
)

/** Keep history restoration pending until the saved position has a body to target. */
internal fun ReaderChapter.resumeLocation(chapterProgress: Float, anchor: ReaderAnchor?): ReaderBookLocation? {
    if (requiresPassword) return null
    return ReaderBookLocation(
        chapter = chapter,
        chapterIndex = 0,
        chapterProgress = chapterProgress.coerceIn(0f, 1f),
        totalChapters = 1,
        anchor = anchor
    )
}

/** Loading/layout changes alone must not discard a saved position. */
internal fun shouldCancelDeferredResume(
    deferredChapterKey: String,
    visibleChapterKey: String?,
    userScrollInProgress: Boolean
): Boolean = userScrollInProgress && visibleChapterKey != null && visibleChapterKey != deferredChapterKey

/** A locked chapter has a location even though it has no body to anchor. */
internal fun ReaderChapter.measuredBookLocation(
    chapterProgress: Float?,
    anchor: ReaderAnchor?,
    chapterOrder: List<Chapter>,
    chapterIndices: Map<String, Int>,
    textIndex: BookTextProgressIndex? = null,
    contentPosition: Float? = null
): ReaderBookLocation? {
    if (!requiresPassword && (chapterProgress == null || anchor == null)) return null
    val progress = if (requiresPassword) 0f else chapterProgress ?: return null
    val location = readerBookLocationFor(chapter, progress, chapterOrder, chapterIndices, textIndex)
        ?: if (requiresPassword) ReaderBookLocation(chapter, -1, 0f, chapterOrder.size) else return null
    return location.copy(
        contentPosition = if (requiresPassword) -1f else contentPosition,
        anchor = if (requiresPassword) null else anchor
    )
}

/** Called under the model lock before publishing request completion. */
internal fun MutableMap<String, ReaderChapterPasswordState>.finishPasswordSubmission(
    key: String,
    chapterStillLocked: Boolean,
    failureState: ReaderChapterPasswordState?
) {
    if (!chapterStillLocked) {
        remove(key)
    } else {
        this[key] = (failureState ?: this[key] ?: ReaderChapterPasswordState()).copy(submitting = false)
    }
}

/** Replace in place so unlocking cannot append, reorder, or restore a trimmed chapter. */
internal fun MutableList<ReaderChapter>.replacePasswordChapter(chapter: Chapter, detail: DetailedChapter): Boolean {
    val index = indexOfFirst { chapterIdentity(it.chapter) == chapterIdentity(chapter) && it.requiresPassword }
    if (index < 0) return false
    val original = this[index].chapter
    this[index] = ReaderChapter(original, detail.toReaderDocument(original), detail.previous, detail.next)
    return true
}
