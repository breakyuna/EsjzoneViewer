package com.breakyuna.esjzone.offline

/** Rebase a chapter completion on the latest inventory instead of publishing a stale snapshot. */
internal fun mergeDownloadManifest(
    existing: DownloadedNovelManifest?,
    incoming: DownloadedNovelManifest,
    replaceCatalog: Boolean
): DownloadedNovelManifest {
    if (existing == null) return incoming
    val old = existing.chapters.associateBy { NovelDownloadStore.chapterKey(it.url) }
    val next = incoming.chapters.associateBy { NovelDownloadStore.chapterKey(it.url) }
    val order = if (replaceCatalog) (incoming.chapters + existing.chapters) else (existing.chapters + incoming.chapters)
    val records = order.distinctBy { NovelDownloadStore.chapterKey(it.url) }.mapIndexed { index, record ->
        val key = NovelDownloadStore.chapterKey(record.url)
        val previous = old[key]
        val update = next[key]
        val metadata = if (replaceCatalog) update ?: previous!! else previous ?: update!!
        metadata.copy(index = index,
            downloaded = previous?.downloaded == true || update?.downloaded == true,
            bodyAvailable = previous?.bodyAvailable == true || update?.bodyAvailable == true,
            textLength = previous?.textLength ?: update?.textLength,
            requiresPassword = previous?.requiresPassword == true || update?.requiresPassword == true,
            localOnly = if (replaceCatalog) key !in next else metadata.localOnly)
    }
    return incoming.copy(version = 2, chapters = records,
        catalogComplete = if (replaceCatalog) incoming.catalogComplete else existing.catalogComplete || existing.complete,
        commonPassword = existing.commonPassword)
}

/** All requested chapter lengths must be known; partial downloads never become the book denominator. */
fun DownloadedNovelManifest.textBookProgress(order: List<String>, chapterKey: String, chapterProgress: Float): Float? {
    return textProgressIndex(order)?.progress(chapterKey, chapterProgress)
}

/** Inverse of textBookProgress, with the same full-catalog length requirement. */
fun DownloadedNovelManifest.chapterIndexAtTextProgress(order: List<String>, bookProgress: Float): Int? {
    return textProgressIndex(order)?.chapterIndex(bookProgress)
}

/** Prepared with the catalog/manifest snapshot, never rebuilt for a scroll offset. */
internal class BookTextProgressIndex(
    private val indices: Map<String, Int>,
    private val prefix: LongArray
) {
    fun progress(chapterKey: String, chapterProgress: Float): Float? =
        indices[chapterKey]?.let { progress(it, chapterProgress) }

    fun progress(index: Int, chapterProgress: Float): Float =
        ((prefix[index] + (prefix[index + 1] - prefix[index]) * chapterProgress.coerceIn(0f, 1f)) /
            prefix.last().toFloat()).coerceIn(0f, 1f)

    fun chapterIndex(bookProgress: Float): Int {
        val progress = bookProgress.coerceIn(0f, 1f)
        val lastIndex = prefix.size - 2
        if (progress >= 1f) return lastIndex
        var low = 0
        var high = lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (progress < prefix[middle + 1] / prefix.last().toFloat()) high = middle
            else low = middle + 1
        }
        return low
    }
}

internal fun DownloadedNovelManifest.textProgressIndex(order: List<String>): BookTextProgressIndex? {
    if (order.isEmpty()) return null
    val lengths = chapters.associate { NovelDownloadStore.chapterKey(it.url) to it.textLength }
    val prefix = LongArray(order.size + 1)
    val indices = HashMap<String, Int>()
    order.forEachIndexed { index, url ->
        val key = NovelDownloadStore.chapterKey(url)
        val length = lengths[key] ?: return null
        indices.putIfAbsent(key, index)
        prefix[index + 1] = prefix[index] + length
    }
    if (prefix.last() <= 0L) return null
    return BookTextProgressIndex(indices, prefix)
}
