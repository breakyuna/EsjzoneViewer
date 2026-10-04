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
    val index = order.indexOfFirst { NovelDownloadStore.chapterKey(it) == chapterKey }
    if (index < 0) return null
    val values = textChapterLengths(order) ?: return null
    val total = values.sumOf { it.toLong() }
    if (total <= 0L) return null
    val before = values.take(index).sumOf { it.toLong() }
    return ((before + values[index] * chapterProgress.coerceIn(0f, 1f)) / total.toFloat()).coerceIn(0f, 1f)
}

/** Inverse of textBookProgress, with the same full-catalog length requirement. */
fun DownloadedNovelManifest.chapterIndexAtTextProgress(order: List<String>, bookProgress: Float): Int? {
    val values = textChapterLengths(order) ?: return null
    val total = values.sumOf { it.toLong() }
    if (total <= 0L) return null
    val progress = bookProgress.coerceIn(0f, 1f)
    if (progress >= 1f) return values.lastIndex
    var end = 0L
    values.forEachIndexed { index, length ->
        end += length
        if (progress < end / total.toFloat()) return index
    }
    return values.lastIndex
}

private fun DownloadedNovelManifest.textChapterLengths(order: List<String>): List<Int>? {
    if (order.isEmpty()) return null
    val lengths = chapters.associate { NovelDownloadStore.chapterKey(it.url) to it.textLength }
    return order.map { lengths[NovelDownloadStore.chapterKey(it)] ?: return null }
}
