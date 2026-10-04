package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.novellibrary.novel.Chapter

/** One normalized catalog and its neighbor index, replaced together when the order changes. */
internal class ReaderChapterCatalog(source: List<Chapter>) {
    val chapters: List<Chapter>
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
        indices = positions
    }

    fun adjacent(chapter: Chapter, offset: Int, fallback: Chapter?): Chapter? {
        val index = indices[chapterIdentity(chapter)]
        // A known catalog chapter keeps the catalog boundary authoritative.
        return if (index != null) chapters.getOrNull(index + offset)
            else fallback?.takeUnless { it.isExternal }
    }
}
