package com.breakyuna.esjzone.domain.reader

/** Pure session/window state used by a future Reader shell. It does not own scrolling. */
class ReaderSessionController(
    chapterOrder: List<ReaderChapterRef>,
    private val maxWindowSize: Int = 9
) {
    private var generation = 0L
    private var order = normalize(chapterOrder)

    val sessionId: Long get() = generation
    val chapterOrder: List<ReaderChapterRef> get() = order

    fun begin(chapter: ReaderChapterRef): Long {
        generation += 1
        return generation
    }

    fun replaceChapterOrder(chapters: List<ReaderChapterRef>) {
        order = normalize(chapters)
    }

    fun adjacent(
        chapter: ReaderChapterRef,
        offset: Int,
        fallback: ReaderChapterRef? = null
    ): ReaderChapterRef? {
        val index = order.indexOfFirst { it.url == chapter.url }
        if (index >= 0) return order.getOrNull(index + offset)
        return fallback
    }

    fun trim(
        loaded: List<ReaderChapterRef>,
        fromStart: Boolean,
        anchor: ReaderWindowAnchor?
    ): List<ReaderChapterRef> {
        val retained = trimReaderWindowKeys(
            keys = loaded.map { it.url },
            trimFromStart = fromStart,
            maxSize = maxWindowSize,
            protectedKeys = anchor?.protectedChapterKeys.orEmpty()
        ).toSet()
        return loaded.filter { it.url in retained }
    }

    private fun normalize(chapters: List<ReaderChapterRef>): List<ReaderChapterRef> =
        chapters.asSequence().filter { it.url.isNotBlank() }.distinctBy { it.url }.toList()
}
