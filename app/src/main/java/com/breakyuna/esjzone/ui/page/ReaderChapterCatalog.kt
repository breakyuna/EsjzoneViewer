package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.novellibrary.novel.Chapter

/** One normalized catalog and its neighbor index, replaced together when the order changes. */
internal class ReaderChapterCatalog(source: List<Chapter>, complete: Boolean = true) {
    val chapters: List<Chapter>
    val isComplete: Boolean
    private val indices: Map<String, Int>

    init {
        val ordered = ArrayList<Chapter>()
        val positions = HashMap<String, Int>()
        source.forEach { chapter ->
            if (!chapter.isExternal) {
                val key = chapterIdentity(chapter)
                if (key.isNotBlank() && key !in positions) {
                    positions[key] = ordered.size
                    ordered += chapter
                }
            }
        }
        chapters = ordered.toList()
        isComplete = complete && chapters.isNotEmpty()
        indices = positions
    }

    fun adjacent(chapter: Chapter, offset: Int): Chapter? {
        if (!isComplete) return null
        val index = indices[chapterIdentity(chapter)]
        // Forum post navigation and partial inventories cannot establish chapter adjacency.
        return if (index != null) chapters.getOrNull(index + offset)
            else null
    }
}
