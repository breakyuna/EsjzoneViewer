package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.novellibrary.novel.Chapter

internal data class ReaderBoundaryVerification(
    val chapter: Chapter,
    val offset: Int,
    val unavailable: Boolean = false,
    val storageUnavailable: Boolean = false
)

/** Accessed under the reader model lock; each direction owns its own pending gate. */
internal class ReaderBoundaryVerifications {
    private val pending = linkedMapOf<Int, ReaderBoundaryVerification>()
    val current: ReaderBoundaryVerification? get() = pending.values.firstOrNull()

    fun blocks(offset: Int, chapter: Chapter? = null): Boolean = pending[offset]?.let {
        chapter == null || chapterIdentity(it.chapter) == chapterIdentity(chapter)
    } == true

    fun remember(verification: ReaderBoundaryVerification) {
        pending[verification.offset] = verification
    }

    fun resolve(chapter: Chapter) {
        pending.entries.removeAll { chapterIdentity(it.value.chapter) == chapterIdentity(chapter) }
    }

    fun retainTargets(previous: Chapter?, next: Chapter?) {
        pending.entries.removeAll { (offset, verification) ->
            val target = if (offset < 0) previous else next
            target == null || chapterIdentity(target) != chapterIdentity(verification.chapter)
        }
    }

    fun take(chapter: Chapter? = null): ReaderBoundaryVerification? {
        val entry = if (chapter == null) current else pending.values.firstOrNull {
            chapterIdentity(it.chapter) == chapterIdentity(chapter)
        }
        return entry?.also { pending.remove(it.offset) }
    }

    fun markStorageUnavailable(chapter: Chapter) {
        val entry = pending.values.firstOrNull { chapterIdentity(it.chapter) == chapterIdentity(chapter) } ?: return
        pending[entry.offset] = entry.copy(storageUnavailable = true)
    }

    fun clear() = pending.clear()
}
