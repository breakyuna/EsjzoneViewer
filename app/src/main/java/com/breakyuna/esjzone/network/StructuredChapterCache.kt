package com.breakyuna.esjzone.network

import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.data.reader.withStructuredBody
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.google.gson.Gson

/** Shares PageCache's account namespace, timestamps, quota, clearing and atomic writes. */
internal object StructuredChapterCache {
    private val gson = Gson()
    private data class Snapshot(val name: String, val sourceUrl: String, val body: ChapterBody,
        val html: String?, val previous: Chapter?, val next: Chapter?)

    fun read(key: String, url: String): DetailedChapter? {
        val encoded = PageCache.read(PageCache.structuredKey(key), PageCacheTtl.CHAPTER) ?: return null
        return runCatching {
            val stored = requireNotNull(gson.fromJson(encoded, Snapshot::class.java))
            require(stored.sourceUrl == url) // Absolute resources belong to the acquisition mirror.
            stored.body.validate()
            DetailedChapter(stored.name, stored.body.components(), stored.previous, stored.next,
                stored.html, stored.sourceUrl, stored.body)
        }.getOrElse { PageCache.remove(PageCache.structuredKey(key)); null }
    }

    fun write(key: String, sourceHtml: String, detail: DetailedChapter) {
        val ready = detail.withStructuredBody()
        if (ready.content.isEmpty()) return
        val json = gson.toJson(Snapshot(ready.name, ready.sourceUrl.orEmpty(), requireNotNull(ready.body),
            ready.contentHtml, ready.previous, ready.next))
        PageCache.writeDerived(key, sourceHtml, json)
    }
}
