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
        val entry = PageCache.readEntry(PageCache.structuredKey(key), PageCacheTtl.CHAPTER, sourceKey = key) ?: run {
            CacheAnalysis.lookup(CacheLayer.CHAPTER, fresh = false)
            return null
        }
        val encoded = entry.body
        return runCatching {
            val stored = requireNotNull(gson.fromJson(encoded, Snapshot::class.java))
            require(stored.sourceUrl == url) // Absolute resources belong to the acquisition mirror.
            stored.body.validate()
            DetailedChapter(stored.name, stored.body.components(), stored.previous, stored.next,
                stored.html, stored.sourceUrl, stored.body, sourceCacheReference = entry.reference)
                .also {
                    CacheAnalysis.lookup(CacheLayer.CHAPTER, fresh = true)
                    CacheDiagnostics.record(CacheEventType.HIT, CacheLayer.CHAPTER, CacheEventReason.FRESH, PageCache.structuredKey(key))
                }
        }.getOrElse {
            CacheAnalysis.lookup(CacheLayer.CHAPTER, fresh = false)
            PageCache.remove(PageCache.structuredKey(key), CacheEventReason.INVALID_DATA)
            null
        }
    }

    fun write(key: String, sourceHtml: String, detail: DetailedChapter): String? {
        val ready = detail.withStructuredBody()
        if (ready.content.isEmpty()) return null
        val json = gson.toJson(Snapshot(ready.name, ready.sourceUrl.orEmpty(), requireNotNull(ready.body),
            ready.contentHtml, ready.previous, ready.next))
        return PageCache.writeDerived(key, sourceHtml, json)
    }
}
